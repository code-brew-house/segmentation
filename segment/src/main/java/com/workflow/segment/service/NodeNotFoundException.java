package com.workflow.segment.service;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NodeNotFoundException extends RuntimeException {
    public NodeNotFoundException(UUID id) { super("Node not found: " + id); }
}
