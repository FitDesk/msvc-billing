package com.msvcbilling.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/saludo")
    public String saludo() {
        return "Hola Microservicio Billing";
    }
}
