package main

import (
	"crypto/tls"
	"crypto/x509"
	"flag"
	"fmt"
	"github.com/Sirupsen/logrus"
	"io/ioutil"
	"net/http"
	"net/http/httputil"
)

var localAddr *string = flag.String("l", "localhost:2376", "local address")

var tlscacert *string = flag.String("--tlscacert", "certs.d/proxy/ca.pem", "")
var tlscert *string = flag.String("--tlscert", "certs.d/proxy/server-cert.pem", "")
var tlskey *string = flag.String("--tlskey", "certs.d/proxy/server-key.pem", "")

var tlscacertNode *string = flag.String("--tlscacert-node", "certs.d/node/ca.pem", "")
var tlscertNode *string = flag.String("--tlscert-node", "certs.d/node/cert.pem", "")
var tlskeyNode *string = flag.String("--tlskey-node", "certs.d/node/key.pem", "")

var log = logrus.New()

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

	// Load client cert
	certNode, err := tls.LoadX509KeyPair(*tlscertNode, *tlskeyNode)
	if err != nil {
		log.Fatal(err)
	}

	// Load CA cert
	caCertNode, err := ioutil.ReadFile(*tlscacertNode)
	if err != nil {
		log.Fatal(err)
	}
	caCertPoolNode := x509.NewCertPool()
	caCertPoolNode.AppendCertsFromPEM(caCertNode)

	// Setup config
	tlsConfigNode := &tls.Config{
		ClientAuth:   tls.RequireAndVerifyClientCert,
		Certificates: []tls.Certificate{certNode},
		ClientCAs:    caCertPoolNode,
		RootCAs:      caCertPoolNode,
	}

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		director := func(req *http.Request) {
			fmt.Printf("%+v", req.TLS)
			req = r
			req.URL.Scheme = "https"
			req.URL.Host = "vexor-bot:9999"
		}
		transport := &http.Transport{
			TLSClientConfig: tlsConfigNode,
		}
		proxy := &httputil.ReverseProxy{
			Director:  director,
			Transport: transport,
		}
		proxy.ServeHTTP(w, r)
	})

	server := &http.Server{
		Addr: ":8181",
	}
	if err != nil {
		log.Fatal(err)
	}

	server.TLSConfig = tlsConfig

	err = server.ListenAndServeTLS(*tlscert, *tlskey)
	if err != nil {
		log.Fatal(err)
	}
}
