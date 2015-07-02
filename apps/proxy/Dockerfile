FROM golang:1.4.2

ADD proxy/ /app/dd-app-proxy
WORKDIR /app/dd-app-proxy

RUN make deps-deps && make build

CMD bin/proxy

EXPOSE 2376
