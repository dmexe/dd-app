package main

import (
	"crypto/tls"
	"crypto/x509"
	"flag"
	log "github.com/Sirupsen/logrus"
	"io/ioutil"
	"net"
)

var localAddr *string = flag.String("l", "localhost:2376", "local address")
var remoteAddr *string = flag.String("r", "/var/run/docker.sock", "remote address")
var tlscacert *string = flag.String("--tlscacert", "certs.d/node/ca.pem", "")
var tlscert *string = flag.String("--tlscert", "certs.d/node/server-cert.pem", "")
var tlskey *string = flag.String("--tlskey", "certs.d/node/server-key.pem", "")

var tlsConfig *tls.Config

func handleConn(n int, in <-chan *net.TCPConn) {
	for conn := range in {
		client, err := NewClient(n, *remoteAddr, conn)
		if err == nil {
			client.Proxy()
		}
	}
}

func main() {
	flag.Parse()

	// Load client cert
	cert, err := tls.LoadX509KeyPair(*tlscert, *tlskey)
	if err != nil {
		log.Fatal(err)
	}

	// Load CA cert
	caCert, err := ioutil.ReadFile(*tlscacert)
	if err != nil {
		log.Fatal(err)
	}
	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM(caCert)

	// Setup config
	tlsConfig = &tls.Config{
		ClientAuth:   tls.RequireAndVerifyClientCert,
		Certificates: []tls.Certificate{cert},
		ClientCAs:    caCertPool,
		RootCAs:      caCertPool,
	}

	log.Info("Listening: ", *localAddr, ", proxying ", *remoteAddr)

	addr, err := net.ResolveTCPAddr("tcp", *localAddr)
	if err != nil {
		log.Fatal(err)
	}

	listener, err := net.ListenTCP("tcp", addr)
	if err != nil {
		log.Fatal(err)
	}

	pending := make(chan *net.TCPConn)

	for i := 0; i < 5; i++ {
		go handleConn(i, pending)
	}

	for {
		conn, err := listener.AcceptTCP()
		if err != nil {
			log.Error(err)
		} else {
			log.WithFields(log.Fields{
				"client": conn.RemoteAddr(),
			}).Info("Accept connection")
			pending <- conn
		}
	}
}
