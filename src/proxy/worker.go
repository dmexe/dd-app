package main

import (
	"crypto/tls"
	log "github.com/Sirupsen/logrus"
	"net"
)

type Worker struct {
	pending  chan *tls.Conn
	Num      int
	tlsProxy *tls.Config
	tlsNode  *tls.Config
}

func NewWorker(n int, tlsProxy *tls.Config, tlsNode *tls.Config) *Worker {
	w := &Worker{
		pending:  make(chan *tls.Conn, n),
		Num:      n,
		tlsProxy: tlsProxy,
		tlsNode:  tlsNode,
	}

	return w
}

func (w *Worker) Handle(index int) {
	for conn := range w.pending {
		client, err := NewClient(index, conn, w.tlsNode)
		if err == nil {
			client.Proxy()
		} else {
			tlsConnLog(conn).Error(err)
		}
	}
}

func (w *Worker) Spawn() {
	for i := 0; i < w.Num; i++ {
		go w.Handle(i)
	}
}

func (w *Worker) Listen(bindAddr string) error {
	addr, err := net.ResolveTCPAddr("tcp", bindAddr)
	if err != nil {
		return err
	}

	listener, err := net.ListenTCP("tcp", addr)
	if err != nil {
		return err
	}

	log.Info("Listen: ", addr, ", workers: ", w.Num)

	w.Spawn()

	closer := func(err error, conn *tls.Conn) {
		tlsConnLog(conn).Error(err)
		if err := conn.Close(); err != nil {
			tlsConnLog(conn).Error(err)
		} else {
			tlsConnLog(conn).Error("Client connection closed")
		}
	}

	for {
		conn, err := listener.AcceptTCP()
		if err != nil {
			log.Error(err)
		} else {
			tcpConnLog(conn).Info("Accept connection")

			tlsConn := tls.Server(conn, w.tlsProxy)
			tlsConnLog(tlsConn).Info("Wait handshake")

			err := tlsConn.Handshake()
			if err != nil {
				closer(err, tlsConn)
			} else {
				w.pending <- tlsConn
			}
		}
	}

	return nil
}
