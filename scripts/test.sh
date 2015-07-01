#!/bin/bash

set -e pipefail
set -x

eval "$(docker-machine env local)"

bin/proxy -tls-node $DOCKER_CERT_PATH -l $DOCKER_HOST &
pid=$!

function finish {
  kill $pid
}

trap finish EXIT

sleep 1

docker="docker --tlsverify --tlscacert=certs.d/proxy/ca.pem --tlscert=certs.d/proxy/cert.pem --tlskey=certs.d/proxy/key.pem -H localhost:2376"

$docker images
$docker build --force-rm --no-cache .
