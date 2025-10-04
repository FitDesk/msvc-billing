package com.msvcbilling.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/saludo")
    public ResponseEntity<String> saludo() {
        return ResponseEntity.ok("Hola Microservicio Billing testing");
    }
}
