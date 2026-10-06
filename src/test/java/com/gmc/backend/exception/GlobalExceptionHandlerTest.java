package com.gmc.backend.exception;

import com.gmc.backend.dto.response.ApiErrorResponse;
import com.gmc.backend.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDuplicateEmail_shouldReturn409Conflict() {
        DuplicateEmailException ex = new DuplicateEmailException("test@example.com");

        ResponseEntity<ApiErrorResponse> response = handler.handleDuplicateEmail(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("DUPLICATE_EMAIL", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("test@example.com"));
    }

    @Test
    void handleNotFound_shouldReturn404NotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Product not found: 99");

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("NOT_FOUND", response.getBody().getError());
        assertEquals("Product not found: 99", response.getBody().getMessage());
    }

    @Test
    void handleBusinessRule_shouldReturn422UnprocessableEntity() {
        BusinessRuleException ex = new BusinessRuleException("Cart is empty");

        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessRule(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BUSINESS_RULE_VIOLATION", response.getBody().getError());
        assertEquals("Cart is empty", response.getBody().getMessage());
    }

    @Test
    void handleStatusTransition_shouldReturn409Conflict() {
        InvalidOrderStatusTransitionException ex =
                new InvalidOrderStatusTransitionException(OrderStatus.DELIVERED, OrderStatus.PROCESSING);

        ResponseEntity<ApiErrorResponse> response = handler.handleStatusTransition(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_STATUS_TRANSITION", response.getBody().getError());
    }

    @Test
    void handleGeneric_shouldReturn500InternalServerError() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().getError());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void handleValidation_shouldReturn400WithAllFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError emailError = new FieldError("req", "email", "Email is required");
        FieldError nameError = new FieldError("req", "name", "Name is required");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(emailError, nameError));

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("email"));
        assertTrue(response.getBody().getMessage().contains("name"));
    }

    @Test
    void handleInvalidFile_shouldReturn400BadRequest() {
        InvalidFileException ex = new InvalidFileException("Unsupported file type");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidFile(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_FILE", response.getBody().getError());
    }

    @Test
    void handleMaxUploadSize_shouldReturn400WithCustomMessage() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024L);

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FILE_TOO_LARGE", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("10 MB"));
    }

    @Test
    void handleSecurityBreach_shouldReturn401WithReuseCode() {
        SecurityBreachException ex = new SecurityBreachException();

        ResponseEntity<ApiErrorResponse> response = handler.handleSecurityBreach(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("REFRESH_TOKEN_REUSE", response.getBody().getError());
    }
}
