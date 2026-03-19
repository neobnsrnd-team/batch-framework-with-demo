package com.hanabank.nbc.mis.batch.framework.exception;

/**
 * 배치 초기화(init) 단계에서 발생하는 예외.
 * BatchJob.init() 구현 시 복구 불가능한 초기화 오류 시 던진다.
 *
 * <p>예시:
 * <ul>
 *   <li>필수 파라미터 누락</li>
 *   <li>외부 시스템 연결 불가</li>
 *   <li>필수 파일/리소스 없음</li>
 * </ul>
 */
public class BatchInitException extends BatchException {

    public BatchInitException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BatchInitException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
