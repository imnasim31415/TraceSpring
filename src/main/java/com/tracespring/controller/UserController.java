package com.tracespring.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final List<Map<String, Object>> USERS = List.of(
        Map.of("id", 1, "name", "Alice", "email", "alice@example.com", "role", "ADMIN"),
        Map.of("id", 2, "name", "Bob",   "email", "bob@example.com",   "role", "USER")
    );

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        return ResponseEntity.ok(USERS);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        Map<String, Object> created = new HashMap<>(body);
        created.put("id", USERS.size() + 1);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
