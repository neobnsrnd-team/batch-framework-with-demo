-- ================================================================
-- HanaBank NBC MIS 배치 프레임워크 — 데모 데이터 등록 SQL
-- Schema: D_SPIDERLINK
-- ================================================================

-- ----------------------------------------------------------------
-- 1. FWK_BATCH_APP — 배치 앱 등록
-- ----------------------------------------------------------------

-- [Demo #1] 일일 리포트 생성 — 정상 수행
INSERT INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE,
    PROPERTIES, TRX_ID, ORG_ID, IO_TYPE,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) VALUES (
    'BATCH_DAILY_REPORT', '일일 리포트 생성', 'DailyReportBatchJob',
    '매일 전일 거래 데이터를 집계하여 일일 리포트를 생성한다',
    NULL,                           -- 선행 배치 없음
    'D',                            -- 일별 실행
    '0 0 1 * * ?',                  -- 매일 01:00
    'Y', 'Y', 'A',
    '{"reportType":"DAILY","threshold":"100"}',
    'TRX001', 'ORG001', 'O',
    TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'), 'SYSTEM'
);

-- [Demo #2] 데이터 동기화 — 정상 수행 + 선행 배치 의존성
INSERT INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE,
    PROPERTIES, TRX_ID, ORG_ID, IO_TYPE,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) VALUES (
    'BATCH_DATA_SYNC', '데이터 동기화', 'DataSyncBatchJob',
    'ERP 시스템으로 거래 데이터를 청크 단위로 동기화한다',
    'BATCH_DAILY_REPORT',           -- 일일 리포트 완료 후 실행
    'D',
    '0 30 2 * * ?',                 -- 매일 02:30
    'Y', 'Y', 'A',
    '{"chunkSize":"50","targetSystem":"ERP"}',
    'TRX002', 'ORG001', 'O',
    TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'), 'SYSTEM'
);

-- [Demo #3] init() 실패 시나리오
INSERT INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE,
    PROPERTIES, TRX_ID, ORG_ID, IO_TYPE,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) VALUES (
    'BATCH_INIT_FAIL', 'init 실패 데모', 'InitFailBatchJob',
    '[데모] init() 단계에서 필수 파라미터 누락으로 FAILED_INIT 발생',
    NULL, 'D',
    '0 0 3 * * ?',                  -- 매일 03:00
    'Y', 'N', 'B',
    '{}',                           -- apiKey 미설정 → init() 실패 유발
    'TRX003', 'ORG001', 'I',
    TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'), 'SYSTEM'
);

-- [Demo #4] executeBatch() NPE 시나리오
INSERT INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE,
    PROPERTIES, TRX_ID, ORG_ID, IO_TYPE,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) VALUES (
    'BATCH_EXEC_NPE', 'NPE 발생 데모', 'ExecNpeBatchJob',
    '[데모] executeBatch()에서 NullPointerException 발생 — 프레임워크 Layer2 포착',
    NULL, 'D',
    '0 0 4 * * ?',                  -- 매일 04:00
    'Y', 'N', 'B',
    '{}',
    'TRX004', 'ORG001', 'B',
    TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'), 'SYSTEM'
);

-- [Demo #5] 비즈니스 오류 시나리오
INSERT INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE,
    PROPERTIES, TRX_ID, ORG_ID, IO_TYPE,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) VALUES (
    'BATCH_EXEC_BIZ_FAIL', '비즈니스 실패 데모', 'ExecBusinessFailBatchJob',
    '[데모] 150번째 레코드에서 외부 API 오류로 BatchExecutionException 발생',
    NULL, 'D',
    '0 30 3 * * ?',                 -- 매일 03:30
    'Y', 'N', 'B',
    '{"failAtRecord":"150"}',
    'TRX005', 'ORG001', 'B',
    TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'), 'SYSTEM'
);

-- [Demo #6] 부분 성공/실패 시나리오
INSERT INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE,
    PROPERTIES, TRX_ID, ORG_ID, IO_TYPE,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) VALUES (
    'BATCH_PARTIAL_FAIL', '부분 실패 데모', 'PartialFailBatchJob',
    '[데모] 1,000건 중 약 10% 무작위 실패 — fail-fast=false 부분 처리',
    NULL, 'D',
    '0 0 5 * * ?',                  -- 매일 05:00
    'Y', 'N', 'C',
    '{"totalRecords":"1000","failRatePercent":"10","maxFailRatePercent":"20"}',
    'TRX006', 'ORG001', 'B',
    TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'), 'SYSTEM'
);

-- [Demo #7] 대용량 처리 시나리오
INSERT INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE,
    PROPERTIES, TRX_ID, ORG_ID, IO_TYPE,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) VALUES (
    'BATCH_SLOW_PROCESS', '대용량 처리 데모', 'SlowProcessBatchJob',
    '[데모] 10,000건 처리 — 진행률/속도/예상잔여시간 상세 로그',
    NULL, 'D',
    '0 0 0 * * ?',                  -- 매일 자정
    'Y', 'N', 'C',
    '{"totalRecords":"10000","msPerRecord":"1"}',
    'TRX007', 'ORG001', 'B',
    TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'), 'SYSTEM'
);

-- ----------------------------------------------------------------
-- 2. FWK_WAS_EXEC_BATCH — WAS별 배치 실행 매핑
-- ----------------------------------------------------------------
-- WAS1: 정상 실행 배치 + 대용량 배치
-- WAS2: 실패 시나리오 배치

-- WAS1 담당 배치
INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_DAILY_REPORT', 'WAS1', 'Y');

INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_DATA_SYNC',    'WAS1', 'Y');

INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_SLOW_PROCESS', 'WAS1', 'Y');

-- WAS2 담당 배치 (실패 시나리오)
INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_INIT_FAIL',     'WAS2', 'Y');

INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_EXEC_NPE',      'WAS2', 'Y');

INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_EXEC_BIZ_FAIL', 'WAS2', 'Y');

INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_PARTIAL_FAIL',  'WAS2', 'Y');

-- 비활성화 예시 (USE_YN='N')
INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN)
    VALUES ('BATCH_DAILY_REPORT',  'WAS2', 'N');  -- WAS2에서는 실행 안 함

COMMIT;

-- ----------------------------------------------------------------
-- 3. 조회 확인 쿼리
-- ----------------------------------------------------------------

-- WAS1에서 실행할 배치 목록 확인
SELECT A.BATCH_APP_ID, A.BATCH_APP_NAME, A.CRON_TEXT, W.INSTANCE_ID, W.USE_YN
  FROM D_SPIDERLINK.FWK_WAS_EXEC_BATCH W
  JOIN D_SPIDERLINK.FWK_BATCH_APP A ON W.BATCH_APP_ID = A.BATCH_APP_ID
 WHERE W.INSTANCE_ID = 'WAS1' AND W.USE_YN = 'Y'
 ORDER BY A.BATCH_APP_ID;

-- WAS2에서 실행할 배치 목록 확인
SELECT A.BATCH_APP_ID, A.BATCH_APP_NAME, A.CRON_TEXT, W.INSTANCE_ID, W.USE_YN
  FROM D_SPIDERLINK.FWK_WAS_EXEC_BATCH W
  JOIN D_SPIDERLINK.FWK_BATCH_APP A ON W.BATCH_APP_ID = A.BATCH_APP_ID
 WHERE W.INSTANCE_ID = 'WAS2' AND W.USE_YN = 'Y'
 ORDER BY A.BATCH_APP_ID;

-- 배치 실행 이력 조회
SELECT BATCH_APP_ID, INSTANCE_ID, BATCH_DATE, BATCH_EXECUTE_SEQ,
       LOG_DTIME, BATCH_END_DTIME, RES_RT_CODE,
       RECORD_COUNT, EXECUTE_COUNT, SUCCESS_COUNT, FAIL_COUNT,
       ERROR_CODE, SUBSTR(ERROR_REASON, 1, 100) AS ERROR_REASON_SHORT
  FROM D_SPIDERLINK.FWK_BATCH_HIS
 ORDER BY LOG_DTIME DESC
 FETCH FIRST 20 ROWS ONLY;
