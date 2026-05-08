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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable int id) {
        return USERS.stream()
                    .filter(u -> u.get("id").equals(id))
                    .findFirst()
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        Map<String, Object> created = new HashMap<>(body);
        created.put("id", USERS.size() + 1);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable int id,
                                                          @RequestBody Map<String, Object> body) {
        boolean exists = USERS.stream().anyMatch(u -> u.get("id").equals(id));
        if (!exists) return ResponseEntity.notFound().build();
        Map<String, Object> updated = new HashMap<>(body);
        updated.put("id", id);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable int id) {
        boolean exists = USERS.stream().anyMatch(u -> u.get("id").equals(id));
        if (!exists) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patchUser(@PathVariable int id,
                                                         @RequestBody Map<String, Object> patch) {
        return USERS.stream()
                    .filter(u -> u.get("id").equals(id))
                    .findFirst()
                    .map(existing -> {
                        Map<String, Object> merged = new HashMap<>(existing);
                        merged.putAll(patch);
                        return ResponseEntity.ok(merged);
                    })
                    .orElse(ResponseEntity.notFound().build());
    }
}
