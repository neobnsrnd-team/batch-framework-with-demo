package com.hanabank.nbc.mis.batch.framework.exception;

/**
 * 배치 프레임워크 최상위 예외 클래스.
 * 모든 배치 관련 예외는 이 클래스를 상속한다.
 */
public class BatchException extends RuntimeException {

    private final String errorCode;

    public BatchException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BatchException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
