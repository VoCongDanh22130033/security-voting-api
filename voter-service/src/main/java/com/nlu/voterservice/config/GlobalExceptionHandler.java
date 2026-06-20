package com.nlu.voterservice.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAll(Exception e) {
        System.err.println(">>> [GlobalExceptionHandler] " + e.getClass().getName() + ": " + e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
