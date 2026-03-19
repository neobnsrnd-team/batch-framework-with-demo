package com.hanabank.nbc.mis.batch.framework.exception;

/**
 * 배치 실행(executeBatch) 단계에서 발생하는 예외.
 * BatchJob.executeBatch() 구현 시 비즈니스 오류 또는 데이터 처리 오류 시 던진다.
 *
 * <p>예시:
 * <ul>
 *   <li>처리 중 DB 오류 (롤백 후)</li>
 *   <li>외부 API 오류 (재시도 불가)</li>
 *   <li>데이터 정합성 오류</li>
 *   <li>임계치 초과 실패율</li>
 * </ul>
 */
public class BatchExecutionException extends BatchException {

    public BatchExecutionException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BatchExecutionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
