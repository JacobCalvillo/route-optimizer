package com.routeopt.ai;

import org.springframework.http.HttpStatus;

/** Raised when the model call fails; carries the HTTP status the API should surface. */
public class OrderParsingException extends RuntimeException {

    private final HttpStatus status;
    private final String requestId;

    public OrderParsingException(HttpStatus status, String message, String requestId, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.requestId = requestId;
    }

    public OrderParsingException(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getRequestId() {
        return requestId;
    }
}
