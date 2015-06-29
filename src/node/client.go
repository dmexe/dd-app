package main

import (
	log "github.com/Sirupsen/logrus"
	"io"
	"net"
)

type Client struct {
	workerId   int
	clientConn *net.TCPConn
	serverConn *net.UnixConn
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
	_, err := io.Copy(dst, src)
	if err != nil {
		c.log.Error(name, " stream copy error:", err)
	}

	if err := src.Close(); err != nil {
		c.log.Error(name, " connection close error:", err)
	}

	c.log.Info(name, " connection successfuly closed")

	closedCh <- true
}

func (c *Client) Proxy() {
	// channels to wait on the close event for each connection
	serverClosed := make(chan bool, 1)
	clientClosed := make(chan bool, 1)

	go c.Copy("Client", c.serverConn, c.clientConn, clientClosed)
	go c.Copy("Server", c.clientConn, c.serverConn, serverClosed)

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
		// c.serverConn.SetLinger(0)
		c.serverConn.CloseRead()
		waitFor = serverClosed
		c.log.Info("Finalize server connection")
	case <-serverClosed:
		c.clientConn.CloseRead()
		waitFor = clientClosed
		c.log.Info("Finalize client connection")
	}
	<-waitFor
	c.log.Info("Done")
}

func NewClient(workerId int, remoteAddr string, clientConn *net.TCPConn) (*Client, error) {
	logger := log.WithFields(log.Fields{
		"worker": workerId,
		"client": clientConn.RemoteAddr().String(),
	})

	logger.Info("Creating new client")

	serverConn, err := net.Dial("unix", remoteAddr)
	if err != nil {
		logger.Error(err)
		if err := clientConn.Close(); err != nil {
			logger.Error(err)
		}
		logger.Error("Client connection closed")
		return nil, err
	}

	logger.Info("Open connection to ", serverConn.RemoteAddr())

	client := &Client{
		workerId:   workerId,
		clientConn: clientConn,
		serverConn: serverConn.(*net.UnixConn),
		log:        logger,
	}

	return client, nil
}
