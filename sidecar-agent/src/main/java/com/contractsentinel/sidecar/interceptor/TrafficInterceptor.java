package com.contractsentinel.sidecar.interceptor;

import com.contractsentinel.sidecar.config.SamplingStrategy;
import com.contractsentinel.sidecar.kafka.TrafficSamplePublisher;
import com.contractsentinel.sidecar.model.TrafficSample;
import com.contractsentinel.sidecar.pii.PiiScrubber;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class TrafficInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TrafficInterceptor.class);

    private final TrafficSamplePublisher publisher;
    private final PiiScrubber piiScrubber;
    private final SamplingStrategy samplingStrategy;
    private final String serviceId;

    private final ConcurrentHashMap<String, EndpointSampleState> endpointStates = new ConcurrentHashMap<>();

    public TrafficInterceptor(
            TrafficSamplePublisher publisher,
            PiiScrubber piiScrubber,
            SamplingStrategy samplingStrategy,
            @Value("${sidecar.service-id:unknown}") String serviceId) {
        this.publisher = publisher;
        this.piiScrubber = piiScrubber;
        this.samplingStrategy = samplingStrategy;
        this.serviceId = serviceId;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(request instanceof ContentCachingRequestWrapper)) {
            return true;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            ContentCachingRequestWrapper requestWrapper = getRequestWrapper(request);
            ContentCachingResponseWrapper responseWrapper = getResponseWrapper(response);

            String endpoint = getEndpoint(request);
            String method = request.getMethod();
            int statusCode = response.getStatus();

            String requestBody = extractBody(requestWrapper);
            String responseBody = extractBody(responseWrapper);

            Map<String, String> requestHeaders = extractHeaders(request);
            Map<String, String> responseHeaders = extractResponseHeaders(response);

            String scrubbedRequest = piiScrubber.scrub(requestBody);
            String scrubbedResponse = piiScrubber.scrub(responseBody);

            EndpointSampleState state = endpointStates.computeIfAbsent(
                    endpoint + ":" + method, k -> new EndpointSampleState());

            boolean sampled = state.recordSample();
            boolean shouldPublish = samplingStrategy.shouldSample(statusCode) || sampled;

            if (shouldPublish) {
                TrafficSample sample = new TrafficSample(
                        serviceId,
                        endpoint,
                        method,
                        requestHeaders,
                        scrubbedRequest,
                        responseHeaders,
                        scrubbedResponse,
                        statusCode,
                        Instant.now(),
                        Instant.now()
                );
                publisher.publish(sample);
            }
        } catch (Exception e) {
            log.error("Error processing traffic interceptor for request", e);
        }
    }

    private ContentCachingRequestWrapper getRequestWrapper(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            return wrapper;
        }
        return new ContentCachingRequestWrapper(request);
    }

    private ContentCachingResponseWrapper getResponseWrapper(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            return wrapper;
        }
        return new ContentCachingResponseWrapper(response);
    }

    private String getEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        return query != null ? path + "?" + query : path;
    }

    private String extractBody(ContentCachingRequestWrapper wrapper) {
        byte[] buf = wrapper.getContentAsByteArray();
        if (buf.length > 0) {
            return new String(buf, StandardCharsets.UTF_8);
        }
        return "";
    }

    private String extractBody(ContentCachingResponseWrapper wrapper) {
        byte[] buf = wrapper.getContentAsByteArray();
        if (buf.length > 0) {
            return new String(buf, StandardCharsets.UTF_8);
        }
        return "";
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
                .collect(Collectors.toMap(
                        name -> name,
                        request::getHeader,
                        (v1, v2) -> v1
                ));
    }

    private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        return response.getHeaderNames().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        response::getHeader,
                        (v1, v2) -> v1
                ));
    }

    private static class EndpointSampleState {
        private final ConcurrentLinkedQueue<Long> samples = new ConcurrentLinkedQueue<>();
        private final AtomicInteger count = new AtomicInteger(0);

        boolean recordSample() {
            count.incrementAndGet();
            samples.add(System.nanoTime());
            return true;
        }
    }
}
