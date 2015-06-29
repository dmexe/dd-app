FROM golang:1.4.2

ADD . /app/vx-app-websocket
WORKDIR /app/vx-app-websocket

RUN make deps && make build

CMD ["/app/vx-app-websocket/bin/server"]
EXPOSE 3003
