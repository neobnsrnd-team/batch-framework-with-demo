package com.hanabank.nbc.mis.batch.framework.core;

import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.logging.BatchLogService;
import com.hanabank.nbc.mis.batch.framework.mapper.FwkBatchHisMapper;
import com.hanabank.nbc.mis.batch.framework.vo.FwkBatchAppVo;
import com.hanabank.nbc.mis.batch.framework.vo.WasInstanceIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 배치 잡 실행 엔진.
 *
 * <p>4중 예외 방어 구조:
 * <pre>
 * [Layer 1] BatchJob.init()        → try/catch → FAILED_INIT 결과
 * [Layer 2] BatchJob.executeBatch() → try/catch → FAILED_EXEC 결과
 * [Layer 3] execute() try/finally  → writeFinalLog 절대 누락 방지
 * [Layer 4] Scheduler ErrorHandler → Runnable 외부 예외 포착 (BatchSchedulerConfig)
 * </pre>
 *
 * <p>오버로드 메서드:
 * <ul>
 *   <li>스케줄러용: baseBatchDate=오늘, 선행배치 체크 ON</li>
 *   <li>REST 수동용: baseBatchDate=요청값, 선행배치 체크 ON</li>
 *   <li>REST forceRerun용: baseBatchDate=요청값, 선행배치 체크 OFF</li>
 * </ul>
 */
@Component
public class BatchJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchJobExecutor.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BatchLogService logService;
    private final FwkBatchHisMapper hisMapper;
    private final WasInstanceIdentity wasIdentity;

    public BatchJobExecutor(BatchLogService logService,
                            FwkBatchHisMapper hisMapper,
                            WasInstanceIdentity wasIdentity) {
        this.logService  = logService;
        this.hisMapper   = hisMapper;
        this.wasIdentity = wasIdentity;
    }

    // =========================================================
    // 오버로드 진입점
    // =========================================================

    /**
     * 스케줄러 자동 실행 — baseBatchDate=오늘, 선행배치 체크 ON.
     */
    public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta, Map<String, String> params) {
        return execute(job, appMeta, params, null, false);
    }

    /**
     * REST 수동 실행 — 임의 baseBatchDate, 선행배치 체크 ON.
     */
    public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta,
                                  Map<String, String> params, String baseBatchDate) {
        return execute(job, appMeta, params, baseBatchDate, false);
    }

    /**
     * REST forceRerun 실행 — 임의 baseBatchDate, 선행배치 체크 선택.
     */
    public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta,
                                  Map<String, String> params, String baseBatchDate,
                                  boolean skipPreBatchCheck) {

        final String instanceId          = wasIdentity.getInstanceId();
        final String finalBaseBatchDate  = StringUtils.hasText(baseBatchDate)
                ? baseBatchDate
                : LocalDate.now().format(DATE_FMT);
        final String batchAppId          = appMeta.getBatchAppId();

        logExecutionStart(batchAppId, instanceId, finalBaseBatchDate, skipPreBatchCheck, params);

        // ── [사전] 선행 배치 완료 여부 체크 ────────────────────────────
        if (!skipPreBatchCheck && StringUtils.hasText(appMeta.getPreBatchAppId())) {
            String preBatchAppId = appMeta.getPreBatchAppId();
            log.info("[{}][PRE-CHECK] 선행 배치 확인: {} ...", batchAppId, preBatchAppId);
            boolean preDone = hisMapper.existsSuccessToday(preBatchAppId, finalBaseBatchDate);
            if (!preDone) {
                log.warn("[{}][PRE-CHECK] 선행 배치 미완료 → SKIPPED (preBatchAppId={})",
                        batchAppId, preBatchAppId);
                return BatchJobResult.skipped(batchAppId, preBatchAppId);
            }
            log.info("[{}][PRE-CHECK] 선행 배치 완료 확인 OK ({})", batchAppId, preBatchAppId);
        }

        // ── 실행 순번 계산 + 컨텍스트 생성 ────────────────────────────
        int executeSeq = logService.calculateNextSeq(batchAppId, instanceId, finalBaseBatchDate);
        Map<String, String> mergedParams = mergeParams(appMeta.getProperties(), params);
        BatchExecutionContext context = new BatchExecutionContext(
                appMeta, instanceId, finalBaseBatchDate, executeSeq, mergedParams);

        log.info("[{}][EXEC] 실행 컨텍스트 생성 완료 | seq={}, params={}",
                batchAppId, executeSeq, mergedParams);

        // ── 핵심 실행 블록 (try-finally writeFinalLog 보장) ───────────
        BatchJobResult result = null;
        long startMs = System.currentTimeMillis();

        try {
            // [STEP 1] 시작 이력 INSERT (RUNNING)
            logService.writeInitLog(context);

            // [STEP 2] init() — Layer 1 방어
            log.info("[{}][INIT] ▶ init() 시작 ─────────────────────────────────────", batchAppId);
            long initStart = System.currentTimeMillis();
            try {
                job.init(context);
                log.info("[{}][INIT] ◀ init() 완료 (소요: {}ms)", batchAppId, elapsed(initStart));
            } catch (Throwable initEx) {
                // Layer 1: init 예외 포착 → FAILED_INIT 결과 생성 후 return
                // finally 블록은 return 이후에도 실행됨 → writeFinalLog 보장
                log.error("[{}][INIT] ✗ init() 예외 발생 [{}: {}]",
                        batchAppId, initEx.getClass().getSimpleName(), initEx.getMessage(), initEx);
                result = BatchJobResult.failedInit(context, initEx, elapsed(startMs));
                return result;   // ← finally 블록 실행 후 실제 반환
            }

            // [STEP 3] executeBatch() — Layer 2 방어
            log.info("[{}][EXEC] ▶ executeBatch() 시작 ─────────────────────────────", batchAppId);
            long execStart = System.currentTimeMillis();
            try {
                job.executeBatch(context);
                result = BatchJobResult.success(context, elapsed(startMs));
                log.info("[{}][EXEC] ◀ executeBatch() 완료 (소요: {}ms)", batchAppId, elapsed(execStart));
            } catch (Throwable execEx) {
                // Layer 2: executeBatch 예외 포착 → FAILED_EXEC 결과 생성
                log.error("[{}][EXEC] ✗ executeBatch() 예외 발생 [{}: {}]",
                        batchAppId, execEx.getClass().getSimpleName(), execEx.getMessage(), execEx);
                result = BatchJobResult.failedExec(context, execEx, elapsed(startMs));
            }

            return result;

        } finally {
            // Layer 3: result가 null이면 예상치 못한 오류 (CONTEXT_ERROR)
            if (result == null) {
                result = BatchJobResult.error(batchAppId, new IllegalStateException("execute() result is null"));
            }
            // writeFinalLog — 절대 누락되지 않음 (return/throw 모두 finally 실행)
            safeWriteFinalLog(context, result);
            logExecutionEnd(batchAppId, result);
        }
    }

    // =========================================================
    // 내부 헬퍼
    // =========================================================

    /**
     * writeFinalLog를 안전하게 호출한다.
     * DB 오류 시 예외를 삼켜 배치 결과 반환을 방해하지 않는다.
     */
    private void safeWriteFinalLog(BatchExecutionContext context, BatchJobResult result) {
        try {
            logService.writeFinalLog(context, result);
        } catch (Throwable logEx) {
            // 이력 기록 실패는 배치 결과에 영향을 주지 않음 — 에러 로그만 출력
            log.error("[{}][LOG] ✗ writeFinalLog 실패 (이력 누락!) - {}",
                    context.getBatchAppId(), logEx.getMessage(), logEx);
        }
    }

    /**
     * DB PROPERTIES(JSON) + REST 파라미터 병합.
     * REST 파라미터가 우선한다.
     */
    private Map<String, String> mergeParams(String propertiesJson, Map<String, String> requestParams) {
        Map<String, String> merged = new HashMap<>();
        if (StringUtils.hasText(propertiesJson)) {
            // 간단한 JSON 파싱 (실제 구현에서는 ObjectMapper 사용)
            // 예: {"threshold":"100","reportType":"DAILY"}
            try {
                String cleaned = propertiesJson.trim().replaceAll("[{}\"\\s]", "");
                for (String kv : cleaned.split(",")) {
                    String[] parts = kv.split(":", 2);
                    if (parts.length == 2) {
                        merged.put(parts[0].trim(), parts[1].trim());
                    }
                }
            } catch (Exception e) {
                log.warn("[PARAMS] DB PROPERTIES 파싱 실패 (무시): {}", e.getMessage());
            }
        }
        if (requestParams != null) {
            merged.putAll(requestParams);   // REST 파라미터가 DB 기본값 덮어씀
        }
        return Collections.unmodifiableMap(merged);
    }

    private long elapsed(long startMs) {
        return System.currentTimeMillis() - startMs;
    }

    // =========================================================
    // 로그 출력 헬퍼
    // =========================================================

    private void logExecutionStart(String batchAppId, String instanceId,
                                   String baseBatchDate, boolean skipPreBatchCheck,
                                   Map<String, String> params) {
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ [{}] 배치 실행 시작", batchAppId);
        log.info("│  WAS 인스턴스  : {}", instanceId);
        log.info("│  배치기준일자  : {}", baseBatchDate);
        log.info("│  선행배치체크  : {}", skipPreBatchCheck ? "OFF (forceRerun)" : "ON");
        log.info("│  요청 파라미터 : {}", params);
        log.info("└─────────────────────────────────────────────────────────────");
    }

    private void logExecutionEnd(String batchAppId, BatchJobResult result) {
        String status = result.isSuccess() ? "✓ SUCCESS" : "✗ " + result.getResRtCode();
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ [{}] 배치 실행 종료 — {}", batchAppId, status);
        log.info("│  처리건수 : 대상={}, 실행={}, 성공={}, 실패={}",
                result.getRecordCount(), result.getExecuteCount(),
                result.getSuccessCount(), result.getFailCount());
        log.info("│  소요시간 : {}ms", result.getElapsedMs());
        if (result.isFailed()) {
            log.info("│  오류코드  : {}", result.getErrorCode());
            log.info("│  오류내용  : {}", result.getErrorReason());
        }
        log.info("└─────────────────────────────────────────────────────────────");
    }
}
