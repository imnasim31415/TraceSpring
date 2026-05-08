package com.tracespring.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable-by-design snapshot of a single HTTP request's lifecycle.
 * Mutable fields are volatile and updated only by the tracing infrastructure.
 */
public class RequestTrace {

    private final String requestId;
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final long startTimeMs;

    // Ordered insertion map; access guarded by synchronized recordStage()
    private final LinkedHashMap<String, Long> stageTimestamps = new LinkedHashMap<>();

    private volatile String handlerMethod;
    private volatile int responseStatus;
    private volatile long totalExecutionMs;
    private volatile String exceptionInfo;
    private volatile boolean completed;

    public RequestTrace(String requestId, String method, String path,
                        Map<String, String> headers, Map<String, String> queryParams) {
        this.requestId    = requestId;
        this.method       = method;
        this.path         = path;
        this.headers      = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.queryParams  = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
        this.startTimeMs  = System.currentTimeMillis();
    }

    public synchronized void recordStage(LifecycleStage stage) {
        stageTimestamps.put(stage.name(), System.currentTimeMillis());
    }

    public void complete() {
        this.totalExecutionMs = System.currentTimeMillis() - startTimeMs;
        this.completed = true;
    }

    // — Setters (called only by tracing infrastructure) —

    public void setHandlerMethod(String handlerMethod)   { this.handlerMethod  = handlerMethod; }
    public void setResponseStatus(int responseStatus)    { this.responseStatus  = responseStatus; }
    public void setExceptionInfo(String exceptionInfo)   { this.exceptionInfo   = exceptionInfo; }

    // — Getters —

    public String getRequestId()                         { return requestId; }
    public String getMethod()                            { return method; }
    public String getPath()                              { return path; }
    public Map<String, String> getHeaders()              { return headers; }
    public Map<String, String> getQueryParams()          { return queryParams; }
    public long getStartTimeMs()                         { return startTimeMs; }
    public String getHandlerMethod()                     { return handlerMethod; }
    public int getResponseStatus()                       { return responseStatus; }
    public long getTotalExecutionMs()                    { return totalExecutionMs; }
    public String getExceptionInfo()                     { return exceptionInfo; }
    public boolean isCompleted()                         { return completed; }

    public synchronized Map<String, Long> getStageTimestamps() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(stageTimestamps));
    }
}
