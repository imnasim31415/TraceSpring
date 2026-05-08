package com.tracespring.service;

import com.tracespring.model.RequestTrace;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TraceStore {

    private final ConcurrentHashMap<String, RequestTrace> traces = new ConcurrentHashMap<>();

    public void save(RequestTrace trace) {
        traces.put(trace.getRequestId(), trace);
    }

    public Optional<RequestTrace> find(String requestId) {
        return Optional.ofNullable(traces.get(requestId));
    }

    public Collection<RequestTrace> findAll() {
        return Collections.unmodifiableCollection(traces.values());
    }

    public int size() {
        return traces.size();
    }
}
