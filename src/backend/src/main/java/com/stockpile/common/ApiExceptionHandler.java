package com.stockpile.common;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

/** Maps domain/validation errors to consistent HTTP responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler({ IllegalStateException.class, IllegalArgumentException.class })
	public ResponseEntity<Map<String, Object>> handleBadState(RuntimeException ex) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * Missing required query param (e.g. {@code /api/placements} without
	 * {@code warehouseId}). Spring's default 400 body carries no {@code message},
	 * which is what the frontend surfaces — so name the parameter explicitly.
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<Map<String, Object>> handleMissingParam(
			MissingServletRequestParameterException ex) {
		return build(HttpStatus.BAD_REQUEST,
				"Required parameter '" + ex.getParameterName() + "' is missing");
	}

	/** Query param of the wrong type (e.g. {@code warehouseId=abc}). */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> handleTypeMismatch(
			MethodArgumentTypeMismatchException ex) {
		return build(HttpStatus.BAD_REQUEST,
				"Parameter '" + ex.getName() + "' has an invalid value: '" + ex.getValue() + "'");
	}

	/**
	 * Unreadable request body — malformed JSON, or a field that cannot be
	 * deserialized (e.g. an invalid enum constant like {@code accessFace:
	 * "FRONT"}). Body-side counterpart of the query-param handlers above; same
	 * rationale: Spring's default 400 body carries no {@code message}.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadableBody(
			HttpMessageNotReadableException ex) {
		for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof InvalidFormatException ife) {
				return build(HttpStatus.BAD_REQUEST, invalidFieldMessage(ife));
			}
		}
		return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
	}

	/** e.g. "Field 'accessFace' has an invalid value: 'FRONT' (expected one of: NORTH, …)". */
	private static String invalidFieldMessage(InvalidFormatException ife) {
		String field = ife.getPath().stream()
				.map(JacksonException.Reference::getPropertyName)
				.filter(Objects::nonNull)
				.collect(Collectors.joining("."));
		String message = "Field '" + (field.isEmpty() ? "<body>" : field)
				+ "' has an invalid value: '" + ife.getValue() + "'";
		Class<?> target = ife.getTargetType();
		if (target != null && target.isEnum()) {
			message += " (expected one of: " + Arrays.stream(target.getEnumConstants())
					.map(Object::toString).collect(Collectors.joining(", ")) + ")";
		}
		return message;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return build(HttpStatus.BAD_REQUEST, detail);
	}

	private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", message == null ? "" : message));
	}
}
