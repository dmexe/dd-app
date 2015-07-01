package main

import (
	"flag"
	log "github.com/Sirupsen/logrus"
)

var (
	bindAddr  *string = flag.String("b", ":2376", "bind address")
	lookupUrl *string = flag.String("l", "http://localhost:80", "lookup url")

	tlsProxyDir *string = flag.String("tls-proxy", "certs.d/proxy", "")
	tlsNodeDir  *string = flag.String("tls-node", "certs.d/node", "")
)

func main() {
	flag.Parse()

	var (
		tlsProxy = NewTlsDir(*tlsProxyDir, TlsDirServer)
		tlsNode  = NewTlsDir(*tlsNodeDir, TlsDirClient)
	)

	tlsProxyCfg, err := tlsProxy.Config()
	if err != nil {
		log.Fatal(err)
	}

	tlsNodeCfg, err := tlsNode.Config()
	if err != nil {
		log.Fatal(err)
	}

	w := NewWorker(5, tlsProxyCfg, tlsNodeCfg, *lookupUrl)

	if err = w.Listen(*bindAddr); err != nil {
		log.Fatal(err)
	}
}
