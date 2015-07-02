#!/bin/bash

set -e pipefail
set -x

eval "$(docker-machine env local)"

apps/proxy/bin/proxy -tls-node $DOCKER_CERT_PATH -tls-proxy $(pwd)/certs.d/proxy -l $DOCKER_HOST &
pid=$!

function finish {
  kill $pid
}

trap finish EXIT

sleep 1

docker="docker --tlsverify --tlscacert=$(pwd)/certs.d/proxy/ca.pem --tlscert=$(pwd)/certs.d/proxy/cert.pem --tlskey=$(pwd)/certs.d/proxy/key.pem -H localhost:2376"

$docker images
(cd apps/proxy && $docker build --force-rm --no-cache .)
