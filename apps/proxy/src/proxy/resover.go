package main

import (
	"crypto/tls"
	"encoding/json"
	"errors"
	"github.com/Sirupsen/logrus"
	"io/ioutil"
	"net"
	"net/http"
	"strings"
)

type ResolverPair struct {
	userId string
	role   string
}

type Resolver struct {
	Id      string `json:"id"`
	RawAddr string `json:"addr"`
	Status  string `json:"status"`
	Addr    *net.TCPAddr
}

func NewResolver(conn *tls.Conn, endpoint string, logger *logrus.Entry) (*Resolver, error) {
	certs := conn.ConnectionState().PeerCertificates
	chain := []ResolverPair{}

	for _, cert := range certs {
		cn := cert.Subject.CommonName
		if len(cert.Subject.OrganizationalUnit) > 0 && cn != "" {
			ou := cert.Subject.OrganizationalUnit[0]
			chain = append(chain, ResolverPair{cn, ou})
		}
	}

	if len(chain) == 0 {
		return nil, errors.New("Cannot found CommonName or OrganizationalUnit in peer certificate")
	}

	userId := chain[0].userId
	role := chain[0].role

	endpoint = strings.Replace(endpoint, ":userId", userId, 1)
	endpoint = strings.Replace(endpoint, ":role", role, 1)

	logger.Debug("Resolve using ", endpoint)

	response, err := http.Get(endpoint)
	if err != nil {
		return nil, err
	}

	defer response.Body.Close()

	content, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}

	res := &Resolver{}

	err = json.Unmarshal(content, res)
	if err != nil {
		return nil, err
	}

	addrAndPort := strings.Join([]string{res.RawAddr, "2376"}, ":")

	addr, err := net.ResolveTCPAddr("tcp", addrAndPort)
	if err != nil {
		return nil, err
	}

	res.Addr = addr

	return res, nil
}
