package main

import (
	"net/url"
)

func LookupDockerUrl(lookupUrl string) (string, error) {

	u, err := url.Parse(lookupUrl)
	if err != nil {
		return "", err
	}

	switch u.Scheme {
	case "tcp":
		return u.Host, nil
	}

	return u.Host, nil
}

func LookupCredentialsUrl(url string) {
}
