package com.example.server.exception;

public class VideoStorageException extends RuntimeException{

    private static final long serialVersionUID = 1L;

    public VideoStorageException(String message) {
        super(message);
    }

    public VideoStorageException(String message, Throwable cause) {
        super(message, cause);
    }

}
