#!/bin/bash

set -e pipefail
set -x

cn="${CN:-localhost}"

mkdir -p certs.d
cd certs.d

openssl genrsa -aes256 -passout pass:foobar -out ca-key.pem 2048

openssl req \
  -new \
  -x509 \
  -days 365 \
  -batch \
  -subj "/CN=server/" \
  -passin pass:foobar \
  -key ca-key.pem \
  -sha256 \
  -out ca.pem

openssl genrsa \
  -out server-key.pem 2048
openssl req \
  -subj "/CN=${cn}" -new -key server-key.pem -out server.csr

openssl x509 \
  -req -days 365 -in server.csr -CA ca.pem -CAkey ca-key.pem \
  -CAcreateserial -out server-cert.pem \
  -passin pass:foobar

# client

openssl genrsa -out key.pem 2048
openssl req -subj '/CN=client' -new -key key.pem -out client.csr
# To make the key suitable for client authentication, create an extensions config file:
echo extendedKeyUsage = clientAuth > extfile.cnf

openssl x509 -req -days 365 -in client.csr -CA ca.pem -CAkey ca-key.pem \
  -CAcreateserial -out cert.pem -extfile extfile.cnf \
  -passin pass:foobar

rm -v client.csr server.csr

#chmod -v 0400 ca-key.pem key.pem server-key.pem
#chmod -v 0444 ca.pem server-cert.pem cert.pem

