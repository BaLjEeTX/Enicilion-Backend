package com.enicilion.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InventoryExhaustedException extends RuntimeException {
    public java.math.BigDecimal remainingQuantity;

    public InventoryExhaustedException(String message) {
        super(message);
    }
}
