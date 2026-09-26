package com.jpcore.labs.payment.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiExceptionHandler {

    @ExceptionHandler(AiRateLimitException.class)
    public ProblemDetail rateLimitError(AiRateLimitException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    @ExceptionHandler(AiTimeoutException.class)
    public ProblemDetail timeoutError(AiTimeoutException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, exception.getMessage());
    }

    @ExceptionHandler(AiAuthenticationException.class)
    public ProblemDetail authenticationError(AiAuthenticationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }
}
