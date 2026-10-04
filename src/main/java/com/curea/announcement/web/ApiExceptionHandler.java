package com.curea.announcement.web;

import com.curea.announcement.service.ApiException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final MessageSource messageSource;

    public ApiExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handle(ApiException ex) {
        return respond(ex.getStatus(), ex.getCode());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code) {
        String description = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return ResponseEntity.status(status).body(new ErrorResponse(code, description));
    }
}
