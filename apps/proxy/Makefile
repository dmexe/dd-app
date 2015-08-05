.PHONY: build deps vim

build:
	gb build

deps:
	go get github.com/constabulary/gb/...
	gb vendor restore

vim:
	GOPATH=./src:./vendor/src mvim .
