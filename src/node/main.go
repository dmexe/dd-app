package main

import (
	"flag"
	log "github.com/Sirupsen/logrus"
	"net"
)

var localAddr *string = flag.String("l", "localhost:9999", "local address")
var remoteAddr *string = flag.String("r", "/var/run/docker.sock", "remote address")

func handleConn(n int, in <-chan *net.TCPConn, out chan<- *Client) {
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

	log.Info("Listening: ", *localAddr, ", proxying ", *remoteAddr)

	addr, err := net.ResolveTCPAddr("tcp", *localAddr)
	if err != nil {
		log.Fatal(err)
	}

	listener, err := net.ListenTCP("tcp", addr)
	if err != nil {
		log.Fatal(err)
	}

	pending, complete := make(chan *net.TCPConn), make(chan *Client)

	for i := 0; i < 5; i++ {
		go handleConn(i, pending, complete)
	}
	go closeConn(complete)

	for {
		conn, err := listener.AcceptTCP()
		log.Info("Accept ", conn.RemoteAddr())
		if err != nil {
			panic(err)
		}
		pending <- conn
	}
}
