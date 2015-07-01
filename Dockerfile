FROM golang:1.4.2

ADD . /app/vx-app-websocket
WORKDIR /app/vx-app-websocket

RUN make deps-gb && make build

EXPOSE 3003
