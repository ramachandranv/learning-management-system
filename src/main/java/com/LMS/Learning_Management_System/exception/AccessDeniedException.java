package com.LMS.Learning_Management_System.exception;

/**
 * Exception thrown when access is denied for operations
 */
public class AccessDeniedException extends RuntimeException {
    
    public AccessDeniedException(String message) {
        super(message);
    }
    
    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}