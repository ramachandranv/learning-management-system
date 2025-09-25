package com.LMS.Learning_Management_System.exception;

/**
 * Exception thrown when question-related operations fail
 */
public class QuestionException extends RuntimeException {
    
    public QuestionException(String message) {
        super(message);
    }
    
    public QuestionException(String message, Throwable cause) {
        super(message, cause);
    }
}