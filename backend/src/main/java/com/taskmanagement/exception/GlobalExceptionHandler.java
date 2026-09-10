package com.taskmanagement.exception;

import com.taskmanagement.dto.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import io.jsonwebtoken.JwtException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Response<Void>> handleStaleWrite(org.springframework.dao.OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "Account changed in another request. Reload and try again.");
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Response<Void>> handleStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Response.error(ex.getReason()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Response<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicatedResourceException.class)
    public ResponseEntity<Response<Void>> handleDuplicateResource(DuplicatedResourceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Response<Void>> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Response<Void>> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Response<Void>> handleMissingAuthentication(
            AuthenticationCredentialsNotFoundException ex
    ) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Response<Void>> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({JwtException.class, UsernameNotFoundException.class})
    public ResponseEntity<Response<Void>> handleInvalidToken(RuntimeException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Response<Void>> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(InvalidGoogleCredentialException.class)
    public ResponseEntity<Response<Void>> handleInvalidGoogleCredential(
            InvalidGoogleCredentialException ex
    ) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationStoreUnavailableException.class)
    public ResponseEntity<Response<Void>> handleAuthenticationStoreUnavailable(
            AuthenticationStoreUnavailableException ex
    ) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Authentication service is temporarily unavailable");
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<Response<Void>> handleStorageUnavailable(StorageUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleGeneral(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Response<Void>> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Response<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
    }

    private ResponseEntity<Response<Void>> build(@NonNull HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Response.error(message));
    }
}
