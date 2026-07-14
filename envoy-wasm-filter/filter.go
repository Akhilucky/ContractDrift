package main

import (
	"encoding/json"
	"regexp"
	"sync"
	"time"
	"unsafe"

	"github.com/tetratelabs/proxy-wasm-go-sdk/proxywasm"
	"github.com/tetratelabs/proxy-wasm-go-sdk/proxywasm/types"
)

// TrafficSample is written as NDJSON for the tailer to pick up.
type TrafficSample struct {
	Timestamp   string            `json:"timestamp"`
	Source      string            `json:"source"`
	Destination string            `json:"destination"`
	Method      string            `json:"method"`
	Path        string            `json:"path"`
	StatusCode  int               `json:"status_code"`
	ReqHeaders  map[string]string `json:"request_headers,omitempty"`
	RespHeaders map[string]string `json:"response_headers,omitempty"`
	ReqBody     string            `json:"request_body,omitempty"`
	RespBody    string            `json:"response_body,omitempty"`
	DurationMs  int64             `json:"duration_ms"`
	Environment string            `json:"environment"`
}

var (
	piiPatterns = []*regexp.Regexp{
		regexp.MustCompile(`[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}`),
		regexp.MustCompile(`\b\d{3}-\d{2}-\d{4}\b`),
		regexp.MustCompile(`\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})\b`),
		regexp.MustCompile(`(?i)(authorization|auth-token|api-key)\s*[:=]\s*\S+`),
	}
	sampleRate  = 1000
	filePath    = "/tmp/traffic_samples.ndjson"
	environment = "development"
)

type httpContext struct {
	proxywasm.HttpContext
	id              int
	startTime       time.Time
	method          string
	path            string
	statusCode      int
	requestHeaders  map[string]string
	responseHeaders map[string]string
	requestBody     []byte
	responseBody    []byte
	finished        bool
	mu              sync.Mutex
}

func init() {
	proxywasm.SetVMContext(&vmContext{})
}

type vmContext struct {
	proxywasm.VMContext
}

func (*vmContext) OnVMStart(vmConfigurationSize int) bool {
	return true
}

func (*vmContext) GetVMConfiguration(vmConfigurationSize int) uint32 {
	return proxywasm.StatusOK
}

func (*vmContext) NewHttpContext(contextID uint32) proxywasm.HttpContext {
	return &httpContext{
		.HttpContext:    proxywasm.HTTPContext{},
		id:              int(contextID),
		requestHeaders:  make(map[string]string),
		responseHeaders: make(map[string]string),
		startTime:       time.Now(),
	}
}

func (ctx *httpContext) OnHttpRequestHeaders(numHeaders int, endOfStream bool) types.Action {
	headers, err := proxywasm.GetHttpRequestHeaders()
	if err != nil {
		proxywasm.LogWarnf("failed to get request headers: %v", err)
		return types.ActionContinue
	}

	for _, h := range headers {
		key := h[0]
		val := h[1]
		ctx.requestHeaders[key] = val
		if key == ":method" {
			ctx.method = val
		} else if key == ":path" {
			ctx.path = val
		}
	}

	return types.ActionContinue
}

func (ctx *httpContext) OnHttpResponseHeaders(numHeaders int, endOfStream bool) types.Action {
	headers, err := proxywasm.GetHttpResponseHeaders()
	if err != nil {
		proxywasm.LogWarnf("failed to get response headers: %v", err)
		return types.ActionContinue
	}

	for _, h := range headers {
		key := h[0]
		val := h[1]
		ctx.responseHeaders[key] = val
		if key == ":status" {
			var code int
			for _, c := range val {
				code = code*10 + int(c-'0')
			}
			ctx.statusCode = code
		}
	}

	return types.ActionContinue
}

func (ctx *httpContext) OnHttpStreamDone() {
	ctx.mu.Lock()
	defer ctx.mu.Unlock()

	if ctx.finished {
		return
	}
	ctx.finished = true

	sample := TrafficSample{
		Timestamp:   time.Now().UTC().Format(time.RFC3339),
		Method:      ctx.method,
		Path:        ctx.path,
		StatusCode:  ctx.statusCode,
		ReqHeaders:  scrubHeaders(ctx.requestHeaders),
		RespHeaders: scrubHeaders(ctx.responseHeaders),
		ReqBody:     scrubPII(string(ctx.requestBody)),
		RespBody:    scrubPII(string(ctx.responseBody)),
		DurationMs:  time.Since(ctx.startTime).Milliseconds(),
		Environment: environment,
	}

	data, err := json.Marshal(sample)
	if err != nil {
		proxywasm.LogWarnf("failed to marshal traffic sample: %v", err)
		return
	}

	data = append(data, '\n')

	if !shouldSample(ctx.path) {
		return
	}

	key := []byte(time.Now().Format("200601021504"))
	key = append(key, ':')
	key = append(key, []byte(ctx.path)...)

	// Append to shared file via shared_queue to the host
	if err := proxywasm.BufferWriteV2("traffic_output", data); err != nil {
		proxywasm.LogDebugf("traffic output write: %v", err)
	}

	_ = unsafe.Pointer(nil)
	proxywasm.LogDebugf("traffic sample written for %s %s", ctx.method, ctx.path)
}

func (ctx *httpContext) OnHttpBody(bodySize int, endOfStream bool) types.Action {
	if !endOfStream {
		return types.ActionContinue
	}

	body, err := proxywasm.GetHttpRequestBody(0, bodySize)
	if err != nil {
		return types.ActionContinue
	}

	ctx.mu.Lock()
	defer ctx.mu.Unlock()

	ctx.requestBody = body
	return types.ActionContinue
}

// shouldSample performs simple reservoir-style sampling per endpoint.
// Accepts all traffic for simplicity; the tailer applies rate limiting.
func shouldSample(path string) bool {
	return true
}

func scrubHeaders(headers map[string]string) map[string]string {
	result := make(map[string]string, len(headers))
	for k, v := range headers {
		key := regexp.MustCompile(`(?i)authorization|auth-token|api-key|cookie|set-cookie`)
		if key.MatchString(k) {
			result[k] = "[REDACTED]"
		} else {
			result[k] = v
		}
	}
	return result
}

func scrubPII(text string) string {
	result := text
	for _, pattern := range piiPatterns {
		result = pattern.ReplaceAllString(result, "[REDACTED]")
	}
	return result
}
