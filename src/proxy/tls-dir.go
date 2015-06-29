package main

import (
	"crypto/tls"
	"crypto/x509"
	"io/ioutil"
	"strings"
)

const (
	TlsDirServer = 0
	TlsDirClient = 1
)

type TlsDir struct {
	path     string
	mode     int
	CaPath   string
	CertPath string
	KeyPath  string
}

func (d *TlsDir) Config() (*tls.Config, error) {
	// Load client cert
	cert, err := tls.LoadX509KeyPair(d.CertPath, d.KeyPath)
	if err != nil {
		return nil, err
	}

	// Load CA cert
	caCert, err := ioutil.ReadFile(d.CaPath)
	if err != nil {
		return nil, err
	}

	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM(caCert)

	tlsConfig := &tls.Config{
		ClientAuth:   tls.RequireAndVerifyClientCert,
		Certificates: []tls.Certificate{cert},
		ClientCAs:    caCertPool,
		RootCAs:      caCertPool,
	}

	return tlsConfig, nil
}

func NewTlsDir(path string, mode int) *TlsDir {
	d := &TlsDir{
		path: path,
		mode: mode,
	}

	d.CaPath = strings.Join([]string{d.path, "/", "ca.pem"}, "")

	if d.mode == TlsDirServer {
		d.CertPath = strings.Join([]string{d.path, "/", "server-cert.pem"}, "")
		d.KeyPath = strings.Join([]string{d.path, "/", "server-key.pem"}, "")
	} else {
		d.CertPath = strings.Join([]string{d.path, "/", "cert.pem"}, "")
		d.KeyPath = strings.Join([]string{d.path, "/", "key.pem"}, "")
	}

	return d
}
