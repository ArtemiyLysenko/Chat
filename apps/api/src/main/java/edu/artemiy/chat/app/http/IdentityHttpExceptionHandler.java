package edu.artemiy.chat.app.http;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import edu.artemiy.chat.attachments.api.AttachmentsErrorType;
import edu.artemiy.chat.attachments.api.AttachmentsException;
import edu.artemiy.chat.contacts.api.ContactsErrorType;
import edu.artemiy.chat.contacts.api.ContactsException;
import edu.artemiy.chat.identity.api.IdentityErrorType;
import edu.artemiy.chat.identity.api.IdentityException;
import edu.artemiy.chat.messaging.api.MessagingErrorType;
import edu.artemiy.chat.messaging.api.MessagingException;
import edu.artemiy.chat.rooms.api.RoomsErrorType;
import edu.artemiy.chat.rooms.api.RoomsException;

@RestControllerAdvice(basePackages = "edu.artemiy.chat.app.http")
class IdentityHttpExceptionHandler {

    @ExceptionHandler(IdentityException.class)
    ResponseEntity<ErrorResponse> handleIdentityException(IdentityException exception) {
        return ResponseEntity.status(httpStatus(exception.errorType()))
            .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(RoomsException.class)
    ResponseEntity<ErrorResponse> handleRoomsException(RoomsException exception) {
        return ResponseEntity.status(httpStatus(exception.errorType()))
            .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(ContactsException.class)
    ResponseEntity<ErrorResponse> handleContactsException(ContactsException exception) {
        return ResponseEntity.status(httpStatus(exception.errorType()))
            .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MessagingException.class)
    ResponseEntity<ErrorResponse> handleMessagingException(MessagingException exception) {
        return ResponseEntity.status(httpStatus(exception.errorType()))
            .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(AttachmentsException.class)
    ResponseEntity<ErrorResponse> handleAttachmentsException(AttachmentsException exception) {
        return ResponseEntity.status(httpStatus(exception.errorType()))
            .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "Request validation failed." : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse("request.validation_failed", message));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("request.bad_request", exception.getMessage()));
    }

    private static HttpStatusCode httpStatus(IdentityErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static HttpStatusCode httpStatus(RoomsErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static HttpStatusCode httpStatus(ContactsErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static HttpStatusCode httpStatus(MessagingErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static HttpStatusCode httpStatus(AttachmentsErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private record ErrorResponse(String code, String message) {
    }
}
