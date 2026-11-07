package com.dora.flowerstore.common;

import org.springframework.http.HttpStatus;

/**
 * 業務例外：可預期的錯誤（庫存不足、參數不合法等），轉成對應 HTTP 狀態碼回給前端
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
