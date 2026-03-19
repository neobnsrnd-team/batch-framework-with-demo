package com.hanabank.nbc.mis.batch.framework.web;

import com.hanabank.nbc.mis.batch.framework.core.BatchJob;
import com.hanabank.nbc.mis.batch.framework.core.BatchJobExecutor;
import com.hanabank.nbc.mis.batch.framework.core.BatchJobResult;
import com.hanabank.nbc.mis.batch.framework.mapper.BatchScheduleMapper;
import com.hanabank.nbc.mis.batch.framework.mapper.FwkBatchHisMapper;
import com.hanabank.nbc.mis.batch.framework.vo.FwkBatchAppVo;
import com.hanabank.nbc.mis.batch.framework.vo.WasInstanceIdentity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 배치 수동 실행 REST API 컨트롤러.
 *
 * <p>엔드포인트: {@code POST /api/batch/{batchAppId}/run}
 *
 * <p>HTTP 응답 코드:
 * <ul>
 *   <li>200 OK          — 실행 완료 (SUCCESS/FAILED_EXEC 포함 — 실행 자체는 완료)</li>
 *   <li>403 Forbidden   — 현재 WAS 인스턴스에 해당 배치 실행 권한 없음</li>
 *   <li>404 Not Found   — 배치 앱 ID가 DB에 없거나 Spring Bean 없음</li>
 *   <li>409 Conflict    — 실행 중 or 이미 SUCCESS (forceRerun=false)</li>
 *   <li>500 Internal    — 프레임워크 내부 오류</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/batch")
public class BatchManualController {

    private static final Logger log = LoggerFactory.getLogger(BatchManualController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BatchJobExecutor executor;
    private final BatchScheduleMapper scheduleMapper;
    private final FwkBatchHisMapper hisMapper;
    private final WasInstanceIdentity wasIdentity;
    private final Map<String, BatchJob> jobMap;

    public BatchManualController(BatchJobExecutor executor,
                                 BatchScheduleMapper scheduleMapper,
                                 FwkBatchHisMapper hisMapper,
                                 WasInstanceIdentity wasIdentity,
                                 Map<String, BatchJob> jobMap) {
        this.executor       = executor;
        this.scheduleMapper = scheduleMapper;
        this.hisMapper      = hisMapper;
        this.wasIdentity    = wasIdentity;
        this.jobMap         = jobMap;
    }

    /**
     * 배치 수동 실행.
     *
     * @param batchAppId 실행할 배치 앱 ID
     * @param request    실행 요청 (baseBatchDate, forceRerun, params)
     */
    @PostMapping("/{batchAppId}/run")
    public ResponseEntity<?> runBatch(@PathVariable String batchAppId,
                                      @Valid @RequestBody BatchRunRequest request) {

        String instanceId      = wasIdentity.getInstanceId();
        String baseBatchDate   = StringUtils.hasText(request.getBaseBatchDate())
                ? request.getBaseBatchDate()
                : LocalDate.now().minusDays(1).format(DATE_FMT);  // 수동 실행 기본값: 어제

        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ [REST] 배치 수동 실행 요청");
        log.info("│  batchAppId   : {}", batchAppId);
        log.info("│  instanceId   : {}", instanceId);
        log.info("│  baseBatchDate: {}", baseBatchDate);
        log.info("│  forceRerun   : {}", request.isForceRerun());
        log.info("│  params       : {}", request.getParams());
        log.info("└─────────────────────────────────────────────────────────────");

        // [1] 배치 앱 메타 정보 조회 (DB)
        FwkBatchAppVo appMeta = scheduleMapper.selectBatchAppById(batchAppId);
        if (appMeta == null) {
            log.warn("[REST] 배치 앱 없음: {}", batchAppId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "배치 앱을 찾을 수 없습니다: " + batchAppId));
        }

        // [2] Spring Bean 조회
        BatchJob job = findBatchJob(appMeta.getBatchAppFileName());
        if (job == null) {
            log.warn("[REST] Spring Bean 없음: {}", appMeta.getBatchAppFileName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "배치 잡 Bean을 찾을 수 없습니다: " + appMeta.getBatchAppFileName()));
        }

        // [3] RUNNING 중복 실행 방지 (forceRerun이어도 항상 차단)
        if (hisMapper.existsRunning(batchAppId, baseBatchDate)) {
            log.warn("[REST] 이미 실행 중 (RUNNING): {}, batchDate={}", batchAppId, baseBatchDate);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "이미 실행 중입니다 (RUNNING 상태). 완료 후 재시도하세요.",
                                 "batchAppId", batchAppId, "baseBatchDate", baseBatchDate));
        }

        // [4] SUCCESS 이력 처리
        int canceledCount = 0;
        if (hisMapper.existsSuccess(batchAppId, baseBatchDate)) {
            if (!request.isForceRerun()) {
                log.warn("[REST] 이미 성공 이력 존재: {}, batchDate={} (forceRerun=false)",
                        batchAppId, baseBatchDate);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "이미 성공적으로 실행된 배치입니다. forceRerun=true로 재실행하세요.",
                                     "batchAppId", batchAppId, "baseBatchDate", baseBatchDate));
            }
            // forceRerun=true: SUCCESS → CANCELED 처리
            canceledCount = hisMapper.cancelPreviousSuccess(batchAppId, baseBatchDate, instanceId);
            log.info("[REST] forceRerun: SUCCESS → CANCELED 처리 완료 ({}건)", canceledCount);
        }

        // [5] 배치 실행 (forceRerun=true → 선행배치 체크 스킵)
        BatchJobResult result;
        try {
            result = executor.execute(job, appMeta, request.getParams(),
                                      baseBatchDate, request.isForceRerun());
        } catch (Exception e) {
            log.error("[REST] executor 호출 중 예외: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "배치 실행 중 내부 오류: " + e.getMessage()));
        }

        // [6] 응답 반환
        BatchRunResponse response = BatchRunResponse.of(
                batchAppId, baseBatchDate, result, request.isForceRerun(), canceledCount);

        log.info("[REST] 배치 수동 실행 완료 — resRtCode={}, elapsed={}ms",
                result.getResRtCode(), result.getElapsedMs());

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // 내부 헬퍼
    // =========================================================

    private BatchJob findBatchJob(String batchAppFileName) {
        if (batchAppFileName == null) return null;
        BatchJob job = jobMap.get(batchAppFileName);
        if (job == null && !batchAppFileName.isEmpty()) {
            String camelKey = Character.toLowerCase(batchAppFileName.charAt(0))
                    + batchAppFileName.substring(1);
            job = jobMap.get(camelKey);
        }
        return job;
    }
}
