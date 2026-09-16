package com.example.orders.exception;

public class ErpClientException extends RuntimeException {

    public ErpClientException(String message) {
        super(message);
    }

    public ErpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
