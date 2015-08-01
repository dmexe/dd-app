package main

import (
	"flag"
	log "github.com/Sirupsen/logrus"
)

var (
	bindAddr  *string = flag.String("b", ":2376", "bind address")
	apiUrl    *string = flag.String("a", "http://localhost:3000", "API url")
	credUrl   *string = flag.String("c", ":api/api/v1/proxy/credentials/:subject", "credentials endpoint")
	lookupUrl *string = flag.String("l", ":api/api/v1/proxy/instance/:userId/:role", "resolver endpoint")
	subject   *string = flag.String("s", "localhost", "subject")
)

func main() {
	flag.Parse()

	cred, err := NewCredentials(*apiUrl, *subject, *credUrl)
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

	w := NewWorker(5, tlsProxyCfg, tlsNodeCfg, *lookupUrl, *apiUrl)

	if err = w.Listen(*bindAddr); err != nil {
		log.Fatal(err)
	}
}
