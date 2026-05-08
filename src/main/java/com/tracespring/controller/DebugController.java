package com.tracespring.controller;

import com.tracespring.model.RequestTrace;
import com.tracespring.service.TraceStore;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collection;

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final TraceStore traceStore;

    @GetMapping("/dashboard")
    public void dashboard(HttpServletResponse response) throws IOException {
        response.sendRedirect("/dashboard.html");
    }

    @GetMapping("/traces")
    public ResponseEntity<Collection<RequestTrace>> getAllTraces() {
        return ResponseEntity.ok(traceStore.findAll());
    }

    @GetMapping("/traces/{requestId}")
    public ResponseEntity<RequestTrace> getTrace(@PathVariable String requestId) {
        return traceStore.find(requestId)
                         .map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }
}
