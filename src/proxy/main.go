package main

import (
	"flag"
	"fmt"
	"github.com/Sirupsen/logrus"
	golog "log"
	"net/http"
	"net/http/httputil"
	"strconv"
)

var (
	bindAddr *string = flag.String("b", ":2376", "bind address")

	proxyTlsDir *string = flag.String("proxy-tls-dir", "certs.d/proxy", "")
	nodeTlsDir  *string = flag.String("node-tls-dir", "certs.d/node", "")

	log = logrus.New()
)

func main() {
	flag.Parse()

	lw := log.Writer()
	defer lw.Close()

	var (
		proxyTls = NewTlsDir(*proxyTlsDir, TlsDirServer)
		nodeTls  = NewTlsDir(*nodeTlsDir, TlsDirClient)
	)

	proxyTlsConfig, err := proxyTls.Config()
	if err != nil {
		log.Fatal(err)
	}

	nodeTlsConfig, err := nodeTls.Config()
	if err != nil {
		log.Fatal(err)
	}

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		log.Info("Starting ", r.Method, " ", r.URL)
		director := func(req *http.Request) {
			req = r
			req.URL.Scheme = "https"
			req.URL.Host = "192.168.87.170:2376"
		}
		transport := &http.Transport{
			TLSClientConfig: nodeTlsConfig,
		}

		proxy := &httputil.ReverseProxy{
			Director:  director,
			Transport: transport,
			ErrorLog:  golog.New(w, "", 0),
		}

		lrw := NewLogResponseWriter(w)

		proxy.ServeHTTP(lrw, r)

		log.Info("Finish ", r.Method, " ", r.URL, " status=", strconv.Itoa(lrw.Status()), " len=", strconv.Itoa(lrw.Size()))
	})

	server := &http.Server{
		Addr:     *bindAddr,
		ErrorLog: golog.New(lw, "", 0),
	}
	server.TLSConfig = proxyTlsConfig

	err = server.ListenAndServeTLS(proxyTls.CertPath, proxyTls.KeyPath)
	if err != nil {
		log.Fatal(err)
	}
}
