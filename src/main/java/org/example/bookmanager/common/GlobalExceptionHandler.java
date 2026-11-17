package org.example.bookmanager.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("business error: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "validation failed" : fieldError.getDefaultMessage();
        log.warn("validation failed: {}", message);
        return Result.fail(400, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        log.warn("upload size exceeded: {}", e.getMessage());
        return Result.fail(400, "uploaded file exceeds size limit");
    }

    @ExceptionHandler(MultipartException.class)
    public Result<Void> handleMultipart(MultipartException e) {
        log.warn("multipart error: {}", e.getMessage());
        return Result.fail(400, "invalid multipart request");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("unhandled error", e);
        return Result.fail(500, e.getMessage());
    }
}
