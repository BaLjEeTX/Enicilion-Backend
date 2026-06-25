package com.enicilion.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadValidationException extends RuntimeException {
    public BadValidationException(String message) {
        super(message);
    }
}
