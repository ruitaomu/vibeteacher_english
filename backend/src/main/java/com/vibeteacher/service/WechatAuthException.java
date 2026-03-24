package com.vibeteacher.service;

import org.springframework.http.HttpStatus;

public class WechatAuthException extends RuntimeException {
    private final HttpStatus status;

    public WechatAuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
