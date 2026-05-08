package com.tracespring.filter;

import com.tracespring.model.LifecycleStage;
import com.tracespring.model.RequestTrace;
import com.tracespring.service.TraceStore;
import com.tracespring.wrapper.CachingRequestWrapper;
import com.tracespring.wrapper.CachingResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Entry point of the Spring MVC lifecycle trace.
 * Executes before DispatcherServlet — wraps request/response, assigns requestId,
 * and records FILTER_START and RESPONSE_SENT bookend stages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestTracingFilter extends OncePerRequestFilter {

    /** Attribute key for passing requestId to downstream interceptors and aspects. */
    public static final String REQUEST_ID_ATTR = "traceRequestId";

    private final TraceStore traceStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();

        CachingRequestWrapper  wrappedReq = new CachingRequestWrapper(request);
        CachingResponseWrapper wrappedRes = new CachingResponseWrapper(response);

        RequestTrace trace = new RequestTrace(
            requestId,
            request.getMethod(),
            request.getRequestURI(),
            extractHeaders(request),
            extractQueryParams(request)
        );

        // Publish requestId so interceptors and aspects can look up the same trace
        wrappedReq.setAttribute(REQUEST_ID_ATTR, requestId);

        trace.recordStage(LifecycleStage.FILTER_START);
        trace.recordStage(LifecycleStage.REQUEST_RECEIVED);
        traceStore.save(trace);

        log.info("[FILTER START   ] {} {} | requestId={}", request.getMethod(), request.getRequestURI(), requestId);

        try {
            chain.doFilter(wrappedReq, wrappedRes);
        } finally {
            trace.recordStage(LifecycleStage.RESPONSE_SENT);
            trace.setResponseStatus(wrappedRes.getStatus());
            trace.complete();
            wrappedRes.copyBodyToResponse();

            log.info("[FILTER END     ] {} {} | status={} | {}ms | requestId={}",
                request.getMethod(), request.getRequestURI(),
                wrappedRes.getStatus(), trace.getTotalExecutionMs(), requestId);
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames())
                   .forEach(name -> headers.put(name, request.getHeader(name)));
        return headers;
    }

    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap()
               .forEach((key, values) -> params.put(key, String.join(",", values)));
        return params;
    }
}
