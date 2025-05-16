package com.github.einrobin.clusterexecutor.cloud.exception;

public class CloudInstanceCreateException extends Exception {

    public CloudInstanceCreateException(String message) {
        super(message);
    }

    public CloudInstanceCreateException(String message, Throwable cause) {
        super(message, cause);
    }
}
