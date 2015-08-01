package main

import (
	"crypto/tls"
	log "github.com/Sirupsen/logrus"
	"io"
	"net"
	"time"
)

type Client struct {
	workerId   int
	clientConn *tls.Conn
	tlsConn    *tls.Conn
	conn       *net.TCPConn
	log        *log.Entry
}

// This does the actual data transfer.
// The broker only closes the Read side.
func (c *Client) Copy(name string, dst net.Conn, src net.Conn, closedCh chan bool) {
	// We can handle errors in a finer-grained manner by inlining io.Copy (it's
	// simple, and we drop the ReaderFrom or WriterTo checks for
	// net.Conn->net.Conn transfers, which aren't needed). This would also let
	// us adjust buffersize.

	c.log.Info(name, " begin stream copy")
	n, err := io.Copy(dst, src)
	c.log.Info(name, " copy ", n, " bytes")
	if err != nil {
		switch err.(type) {
		case *net.OpError:
			if err.(*net.OpError).Timeout() {
				c.log.Info(name, " stream copy ", err)
			} else {
				c.log.Error(name, " stream copy ", err)
			}
		default:
			c.log.Error(name, " stream copy ", err)
		}
	}

	if err := src.Close(); err != nil {
		c.log.Error(name, " connection close error:", err)
	} else {
		c.log.Info(name, " connection closed")
	}

	closedCh <- true
}

func (c *Client) Proxy() {
	// channels to wait on the close event for each connection
	serverClosed := make(chan bool, 1)
	clientClosed := make(chan bool, 1)

	go c.Copy("Server -> Client", c.tlsConn, c.clientConn, clientClosed)
	go c.Copy("Client -> Server", c.clientConn, c.tlsConn, serverClosed)

	// wait for one half of the proxy to exit, then trigger a shutdown of the
	// other half by calling CloseRead(). This will break the read loop in the
	// broker and allow us to fully close the connection cleanly without a
	// "use of closed network connection" error.
	var waitFor chan bool

	select {
	case <-clientClosed:
		// the client closed first and any more packets from the server aren't
		// useful, so we can optionally SetLinger(0) here to recycle the port
		// faster.
		c.tlsConn.SetReadDeadline(time.Now())
		waitFor = serverClosed
		c.log.Info("Finalize server connection")
	case <-serverClosed:
		c.clientConn.SetReadDeadline(time.Now())
		waitFor = clientClosed
		c.log.Info("Finalize client connection")
	}
	<-waitFor
	c.log.Info("Done")
}

func NewClient(workerId int, conn *tls.Conn, tlsConfig *tls.Config, endpoint string) (*Client, error) {
	logger := tlsConnLog(conn).WithFields(log.Fields{
		"worker": workerId,
	})
	logger.Info("Creating new client")

	closer := func(err error) {
		logger.Error(err)
		if err := conn.Close(); err != nil {
			logger.Error(err)
		} else {
			logger.Error("Client connection closed")
		}
	}

	resolver, err := NewResolver(conn, endpoint, logger)
	if err != nil {
		closer(err)
		return nil, err
	}
	logger.Info("Got ", resolver.Addr)

	serverConn, err := net.DialTCP("tcp", nil, resolver.Addr)
	if err != nil {
		closer(err)
		return nil, err
	}

	tlsConfig.InsecureSkipVerify = true
	tlsConn := tls.Client(serverConn, tlsConfig)

	logger.Info("Established ", serverConn.RemoteAddr(), " <-> ", tlsConn.RemoteAddr())
	client := &Client{
		workerId:   workerId,
		clientConn: conn,
		tlsConn:    tlsConn,
		conn:       serverConn,
		log:        logger,
	}

	return client, nil
}
