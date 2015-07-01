.PHONY: build deps-gb deps-fetch

build:
	gb build

deps-gb:
	go get github.com/constabulary/gb/...

deps-fetch: deps-gb
	gb vendor list -f "-branch {{.Branch}} -tag {{.Revision}} {{.Importpath}}" | xargs gb vendor fetch
