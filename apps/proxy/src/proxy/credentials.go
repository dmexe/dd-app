package main

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	log "github.com/Sirupsen/logrus"
	"io/ioutil"
	"net/http"
	"strings"
)

type CredentialsTlsInfo struct {
	CaCert string `json:"ca"`
	Cert   string `json:"cert"`
	Key    string `json:"key"`
}

type Credentials struct {
	Docker  CredentialsTlsInfo `json:"docker"`
	Clients CredentialsTlsInfo `json:"clients"`
}

func NewCredentials(apiUrl string, subject string, urlPattern string) (*Credentials, error) {

	url := strings.Replace(
		strings.Replace(urlPattern, ":api", apiUrl, 1),
		":subject",
		subject,
		1,
	)

	log.Info("Using ", url, " as credentials endpoint")

	response, err := http.Get(url)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()

	content, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}

	cred := &Credentials{}

	err = json.Unmarshal(content, cred)
	if err != nil {
		return nil, err
	}

	return cred, nil
}

func (cfg CredentialsTlsInfo) TlsConfig() (*tls.Config, error) {

	// Load client cert
	cert, err := tls.X509KeyPair([]byte(cfg.Cert), []byte(cfg.Key))
	if err != nil {
		return nil, err
	}

	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM([]byte(cfg.CaCert))

	tlsConfig := &tls.Config{
		ClientAuth:   tls.RequireAndVerifyClientCert,
		Certificates: []tls.Certificate{cert},
		ClientCAs:    caCertPool,
		RootCAs:      caCertPool,
	}

	return tlsConfig, nil
}
