package com.scooter.backend;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiError {
  public static class Failure extends RuntimeException {
    public final HttpStatus status;
    public Failure(HttpStatus status, String message) { super(message); this.status = status; }
  }
  @ExceptionHandler(Failure.class) public ResponseEntity<?> fail(Failure e) {
    return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
  }
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, DataIntegrityViolationException.class})
  public ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("error", "Invalid request or database constraint violation"));
  }
}
