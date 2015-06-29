package main

import (
	"crypto/tls"
	"crypto/x509"
	"flag"
	log "github.com/Sirupsen/logrus"
	"io/ioutil"
	"net"
)

var localAddr *string = flag.String("l", "localhost:9999", "local address")
var remoteAddr *string = flag.String("r", "/var/run/docker.sock", "remote address")
var tlscacert *string = flag.String("--tlscacert", "certs.d/ca.pem", "")
var tlscert *string = flag.String("--tlscert", "certs.d/server-cert.pem", "")
var tlskey *string = flag.String("--tlskey", "certs.d/server-key.pem", "")

func handleConn(n int, in <-chan *tls.Conn, out chan<- *Client) {
	for conn := range in {
		client, err := NewClient(n, *remoteAddr, conn)
		if err == nil {
			client.Proxy()
			out <- client
		}
	}
}

func closeConn(in <-chan *Client) {
	//for c := range in {
	//c.Close()
	//}
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
	tlsConfig := &tls.Config{
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

	pending, complete := make(chan *tls.Conn), make(chan *Client)

	for i := 0; i < 5; i++ {
		go handleConn(i, pending, complete)
	}
	go closeConn(complete)

	for {
		conn, err := listener.AcceptTCP()
		if err != nil {
			log.Error(err)
		} else {
			log.Info("Accept ", conn.RemoteAddr())

			tlsConn := tls.Server(conn, tlsConfig)
			err = tlsConn.Handshake()
			if err != nil {
				conn.Close()
				log.Error("Client ", conn.RemoteAddr(), " error: ", err)
			} else {
				pending <- tlsConn
			}
		}
	}
}
