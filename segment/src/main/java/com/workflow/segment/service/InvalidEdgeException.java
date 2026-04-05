package com.workflow.segment.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidEdgeException extends RuntimeException {
    public InvalidEdgeException(String message) {
        super(message);
    }
}
