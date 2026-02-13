package com.paucasesnoves.steamAPI.exception;

public class DatabaseNotEmptyException extends RuntimeException {
    public DatabaseNotEmptyException(String message) {
        super(message);
    }
}