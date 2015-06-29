build:
	gb build

deps:
	go get github.com/constabulary/gb/...
	gb vendor update -all

