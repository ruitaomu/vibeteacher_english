package com.vibeteacher.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                                   HttpServletRequest request) {
        logger.warn("Request payload too large. path={}", request.getRequestURI(), ex);
        return buildErrorResponse(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "图片超过上传大小限制（20MB），请压缩后重试。",
            request.getRequestURI()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex,
                                                                     HttpServletRequest request) {
        logger.warn("Bad request. path={}", request.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex,
                                                                  HttpServletRequest request) {
        logger.warn("Service state error. path={}", request.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex,
                                            HttpServletRequest request) {
        logger.warn("Client disconnected before response was fully written. path={}, reason={}",
            request.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex,
                                                               HttpServletRequest request) {
        if (isClientAbort(ex)) {
            logger.warn("Client connection aborted. path={}, reason={}", request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        logger.error("Unhandled API exception. path={}", request.getRequestURI(), ex);
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
            ? "服务端发生未处理异常，请稍后重试。"
            : ex.getMessage();
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status,
                                                                   String message,
                                                                   String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String className = current.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("broken pipe")
                    || lower.contains("connection reset")
                    || lower.contains("software caused connection abort")
                    || message.contains("软件中止了一个已建立的连接")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
