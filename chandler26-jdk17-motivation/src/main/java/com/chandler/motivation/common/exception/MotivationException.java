package com.chandler.motivation.common.exception;

public class MotivationException extends RuntimeException {

    private final String code;

    public MotivationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
