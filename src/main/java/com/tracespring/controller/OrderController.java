package com.tracespring.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable int id) {
        if (id <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Order ID must be positive"));
        }
        return ResponseEntity.ok(Map.of(
            "id",       id,
            "product",  "Spring Boot in Action",
            "quantity", 1,
            "status",   "SHIPPED"
        ));
    }
}
