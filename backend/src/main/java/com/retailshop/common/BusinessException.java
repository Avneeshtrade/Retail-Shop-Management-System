package com.retailshop.common;

/** A rule violation the client can fix (insufficient stock, invalid state transition, ...). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
