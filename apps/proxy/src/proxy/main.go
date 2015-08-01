package main

import (
	"flag"
	log "github.com/Sirupsen/logrus"
)

var (
	bindAddr  *string = flag.String("b", ":2376", "bind address")
	credUrl   *string = flag.String("c", "http://localhost:3000/api/v1/docker/credentials/localhost", "credentials url")
	lookupUrl *string = flag.String("l", "http://localhost:3000/api/v1/docker/lookup/:subject", "lookup url")
)

func main() {
	flag.Parse()

	cred, err := NewCredentials(*credUrl)
	if err != nil {
		log.Fatal(err)
	}

	tlsProxyCfg, err := cred.Clients.TlsConfig()
	if err != nil {
		log.Fatal(err)
	}

	tlsNodeCfg, err := cred.Docker.TlsConfig()
	if err != nil {
		log.Fatal(err)
	}

	w := NewWorker(5, tlsProxyCfg, tlsNodeCfg, *lookupUrl)

	if err = w.Listen(*bindAddr); err != nil {
		log.Fatal(err)
	}
}
