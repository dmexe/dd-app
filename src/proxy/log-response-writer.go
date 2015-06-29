package main

import (
	"fmt"
	"net/http"
)

// LogResponseWritter wraps the standard http.ResponseWritter allowing for more
// verbose logging
type LogResponseWriter struct {
	status int
	size   int
	http.ResponseWriter
}

func NewLogResponseWriter(res http.ResponseWriter) *LogResponseWriter {
	// Default the status code to 200
	return &LogResponseWriter{200, 0, res}
}

// Status provides an easy way to retrieve the status code
func (w *LogResponseWriter) Status() int {
	return w.status
}

// Size provides an easy way to retrieve the response size in bytes
func (w *LogResponseWriter) Size() int {
	return w.size
}

// Header returns & satisfies the http.ResponseWriter interface
func (w *LogResponseWriter) Header() http.Header {
	return w.ResponseWriter.Header()
}

// Write satisfies the http.ResponseWriter interface and
// captures data written, in bytes
func (w *LogResponseWriter) Write(data []byte) (int, error) {

	fmt.Printf("> %s\n", string(data))

	written, err := w.ResponseWriter.Write(data)
	w.size += written

	return written, err
}

// WriteHeader satisfies the http.ResponseWriter interface and
// allows us to cach the status code
func (w *LogResponseWriter) WriteHeader(statusCode int) {

	w.status = statusCode
	w.ResponseWriter.WriteHeader(statusCode)
}
