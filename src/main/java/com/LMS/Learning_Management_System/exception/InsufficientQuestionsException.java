package com.LMS.Learning_Management_System.exception;

/**
 * Exception thrown when insufficient questions are available for quiz creation
 */
public class InsufficientQuestionsException extends RuntimeException {
    
    public InsufficientQuestionsException(String message) {
        super(message);
    }
    
    public InsufficientQuestionsException(String message, Throwable cause) {
        super(message, cause);
    }
}