package com.hanabank.nbc.mis.batch.framework.core;

import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;

/**
 * 배치 잡 계약 인터페이스.
 *
 * <p>모든 배치 잡은 이 인터페이스를 구현해야 한다.
 * 프레임워크는 다음 순서로 메서드를 호출한다:
 * <ol>
 *   <li>{@link #getBatchAppId()} — FWK_BATCH_APP.BATCH_APP_ID 와 매핑 확인</li>
 *   <li>{@link #init(BatchExecutionContext)} — 초기화 (파라미터 읽기, 리소스 준비)</li>
 *   <li>{@link #executeBatch(BatchExecutionContext)} — 비즈니스 로직 수행</li>
 * </ol>
 *
 * <p>구현 시 주의사항:
 * <ul>
 *   <li>init()에서 복구 불가 오류 → {@link BatchInitException} 던질 것</li>
 *   <li>executeBatch()에서 비즈니스 오류 → {@link BatchExecutionException} 던질 것</li>
 *   <li>예외를 직접 처리하지 않아도 프레임워크가 catch → DB 이력 기록 보장</li>
 *   <li>처리 건수는 context의 countSuccess() / countFail() 으로 업데이트</li>
 * </ul>
 *
 * <p>Spring Bean으로 등록 시 클래스 단순명(simple name)이 FWK_BATCH_APP.BATCH_APP_FILE_NAME 과 매핑됨.
 */
public interface BatchJob {

    /**
     * 이 배치 잡의 고유 ID를 반환한다.
     * FWK_BATCH_APP.BATCH_APP_ID 와 일치해야 한다.
     *
     * @return 배치 앱 ID (예: "BATCH_DAILY_REPORT")
     */
    String getBatchAppId();

    /**
     * 배치 초기화 단계.
     * DB 이력 INSERT 직후, executeBatch() 전에 호출된다.
     *
     * <p>이 단계에서 수행할 작업:
     * <ul>
     *   <li>파라미터 검증 및 파싱</li>
     *   <li>외부 시스템 연결 확인</li>
     *   <li>처리 대상 건수 조회 → context.setRecordCount(n)</li>
     *   <li>실행에 필요한 리소스/설정 로딩</li>
     * </ul>
     *
     * @param context 실행 컨텍스트 (파라미터, 날짜, 카운터 포함)
     * @throws BatchInitException 초기화 실패 시 (프레임워크가 FAILED_INIT으로 기록)
     */
    void init(BatchExecutionContext context) throws BatchInitException;

    /**
     * 배치 실행 단계 (비즈니스 로직).
     * init() 정상 완료 후에만 호출된다.
     *
     * <p>이 단계에서 수행할 작업:
     * <ul>
     *   <li>데이터 처리 (조회 → 변환 → 저장)</li>
     *   <li>처리 건수 카운트 → context.countSuccess() / context.countFail()</li>
     *   <li>진행 상황 로그 출력</li>
     * </ul>
     *
     * @param context 실행 컨텍스트
     * @throws BatchExecutionException 비즈니스 오류 시 (프레임워크가 FAILED_EXEC으로 기록)
     */
    void executeBatch(BatchExecutionContext context) throws BatchExecutionException;
}
