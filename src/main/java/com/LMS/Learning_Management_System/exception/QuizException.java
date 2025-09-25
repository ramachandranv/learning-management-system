package com.LMS.Learning_Management_System.exception;

/**
 * Exception thrown when quiz-related operations fail
 */
public class QuizException extends RuntimeException {
    
    public QuizException(String message) {
        super(message);
    }
    
    public QuizException(String message, Throwable cause) {
        super(message, cause);
    }
}