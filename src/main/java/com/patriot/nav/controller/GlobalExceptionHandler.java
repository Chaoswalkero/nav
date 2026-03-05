package com.patriot.nav.controller;

import com.patriot.nav.model.RouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * Global Exception Handler für REST API
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RouteResponse> handleValidationException(
            MethodArgumentNotValidException e,
            WebRequest request) {
        
        log.warn("Validation error: {}", e.getMessage());
        
        RouteResponse response = new RouteResponse();
        response.setStatus("error");
        response.setError("Validation error: " + e.getBindingResult().getFieldError().getDefaultMessage());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(JsonMappingException.class)
    public ResponseEntity<RouteResponse> handleJsonMappingException(
            JsonMappingException e,
            WebRequest request) {
        
        log.warn("JSON parsing error: {}", e.getMessage());
        
        RouteResponse response = new RouteResponse();
        response.setStatus("error");
        response.setError("Invalid JSON format: " + e.getMessage());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RouteResponse> handleIllegalArgument(
            IllegalArgumentException e,
            WebRequest request) {
        
        log.warn("Invalid argument: {}", e.getMessage());
        
        RouteResponse response = new RouteResponse();
        response.setStatus("error");
        response.setError("Invalid argument: " + e.getMessage());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RouteResponse> handleGlobalException(
            Exception e,
            WebRequest request) {
        
        log.error("Unexpected error", e);
        
        RouteResponse response = new RouteResponse();
        response.setStatus("error");
        response.setError("Internal server error: " + e.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
