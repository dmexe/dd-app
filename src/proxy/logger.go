package main

import (
	"crypto/tls"
	log "github.com/Sirupsen/logrus"
	"net"
)

func tlsConnLog(conn *tls.Conn) *log.Entry {
	return log.WithFields(log.Fields{
		"addr": conn.RemoteAddr(),
	})
}

func tcpConnLog(conn *net.TCPConn) *log.Entry {
	return log.WithFields(log.Fields{
		"addr": conn.RemoteAddr(),
	})
}
