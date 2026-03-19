-- ================================================================
-- Batch Framework 초기 데이터
-- Spring Boot 기동 시 자동 실행 (spring.sql.init.mode=always)
-- ================================================================

-- ----------------------------------------------------------------
-- FWK_BATCH_APP : 7개 데모 배치 앱 등록
-- ----------------------------------------------------------------

-- [데모 #1] 일일 리포트 배치 (정상 완료 시나리오)
MERGE INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE, PROPERTIES,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) KEY (BATCH_APP_ID) VALUES (
    'BATCH_DAILY_REPORT',
    '일일 리포트 배치',
    'DailyReportBatchJob',
    '[데모#1] 정상 완료 시나리오 - 10,000건 처리 후 SUCCESS',
    NULL,
    'D',
    '0 0 1 * * *',
    'Y', 'N', 'H',
    '{"totalRecords":"10000","msPerRecord":"1"}',
    '20260101000000', 'SYSTEM'
);

-- [데모 #2] 데이터 동기화 배치 (선행 배치 의존 시나리오)
MERGE INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE, PROPERTIES,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) KEY (BATCH_APP_ID) VALUES (
    'BATCH_DATA_SYNC',
    '데이터 동기화 배치',
    'DataSyncBatchJob',
    '[데모#2] 선행 배치 의존 시나리오 - BATCH_DAILY_REPORT 성공 후 실행',
    'BATCH_DAILY_REPORT',
    'D',
    '0 30 2 * * *',
    'Y', 'N', 'M',
    '{"sourceSystem":"CRM","targetSystem":"DW","batchSize":"500"}',
    '20260101000000', 'SYSTEM'
);

-- [데모 #3] 초기화 실패 배치 (FAILED_INIT 시나리오)
MERGE INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE, PROPERTIES,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) KEY (BATCH_APP_ID) VALUES (
    'BATCH_INIT_FAIL',
    '초기화 실패 배치',
    'InitFailBatchJob',
    '[데모#3] FAILED_INIT 시나리오 - init() 단계에서 BatchInitException 발생',
    NULL,
    'D',
    '0 0 3 * * *',
    'N', 'N', 'N',
    '{"shouldFail":"true","failMessage":"DB 연결 실패 (시뮬레이션)"}',
    '20260101000000', 'SYSTEM'
);

-- [데모 #4] 실행 NPE 배치 (FAILED_EXEC - NullPointerException 시나리오)
MERGE INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE, PROPERTIES,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) KEY (BATCH_APP_ID) VALUES (
    'BATCH_EXEC_NPE',
    '실행 NPE 배치',
    'ExecNpeBatchJob',
    '[데모#4] FAILED_EXEC 시나리오 - executeBatch()에서 NullPointerException 발생',
    NULL,
    'D',
    '0 0 4 * * *',
    'Y', 'N', 'N',
    '{"failAtRecord":"500"}',
    '20260101000000', 'SYSTEM'
);

-- [데모 #5] 비즈니스 실패 배치 (BatchExecutionException 시나리오)
MERGE INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE, PROPERTIES,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) KEY (BATCH_APP_ID) VALUES (
    'BATCH_EXEC_BIZ_FAIL',
    '비즈니스 실패 배치',
    'ExecBusinessFailBatchJob',
    '[데모#5] FAILED_EXEC 시나리오 - executeBatch()에서 BatchExecutionException 발생',
    NULL,
    'D',
    '0 30 3 * * *',
    'Y', 'N', 'N',
    '{"processCount":"300","errorCode":"BIZ_001","errorMessage":"잔액 부족으로 처리 불가"}',
    '20260101000000', 'SYSTEM'
);

-- [데모 #6] 부분 실패 배치 (fail-fast=false 시나리오)
MERGE INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE, PROPERTIES,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) KEY (BATCH_APP_ID) VALUES (
    'BATCH_PARTIAL_FAIL',
    '부분 실패 배치',
    'PartialFailBatchJob',
    '[데모#6] 부분 성공/실패 시나리오 - 일부 레코드 실패, 전체 중단 없이 계속 진행',
    NULL,
    'D',
    '0 0 5 * * *',
    'Y', 'N', 'M',
    '{"totalRecords":"1000","failRatePercent":"10","maxFailRatePercent":"20"}',
    '20260101000000', 'SYSTEM'
);

-- [데모 #7] 대용량 처리 배치 (처리 시간이 긴 배치)
MERGE INTO D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID, BATCH_APP_NAME, BATCH_APP_FILE_NAME, BATCH_APP_DESC,
    PRE_BATCH_APP_ID, BATCH_CYCLE, CRON_TEXT,
    RETRYABLE_YN, PER_WAS_YN, IMPORTANT_TYPE, PROPERTIES,
    LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID
) KEY (BATCH_APP_ID) VALUES (
    'BATCH_SLOW_PROCESS',
    '대용량 처리 배치',
    'SlowProcessBatchJob',
    '[데모#7] 처리 시간이 긴 배치 - 상세 진행 로그 + 소요 시간 모니터링',
    NULL,
    'D',
    '0 0 0 * * *',
    'Y', 'N', 'H',
    '{"totalRecords":"10000","msPerRecord":"1"}',
    '20260101000000', 'SYSTEM'
);

-- ----------------------------------------------------------------
-- FWK_WAS_EXEC_BATCH : WAS 인스턴스별 배치 실행 매핑
--   WAS1: BATCH_DAILY_REPORT, BATCH_DATA_SYNC, BATCH_SLOW_PROCESS
--   WAS2: BATCH_INIT_FAIL, BATCH_EXEC_NPE, BATCH_EXEC_BIZ_FAIL, BATCH_PARTIAL_FAIL
-- ----------------------------------------------------------------

MERGE INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN, LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID)
KEY (BATCH_APP_ID, INSTANCE_ID)
VALUES ('BATCH_DAILY_REPORT',  'WAS1', 'Y', '20260101000000', 'SYSTEM');

MERGE INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN, LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID)
KEY (BATCH_APP_ID, INSTANCE_ID)
VALUES ('BATCH_DATA_SYNC',     'WAS1', 'Y', '20260101000000', 'SYSTEM');

MERGE INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN, LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID)
KEY (BATCH_APP_ID, INSTANCE_ID)
VALUES ('BATCH_SLOW_PROCESS',  'WAS1', 'Y', '20260101000000', 'SYSTEM');

MERGE INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN, LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID)
KEY (BATCH_APP_ID, INSTANCE_ID)
VALUES ('BATCH_INIT_FAIL',     'WAS2', 'Y', '20260101000000', 'SYSTEM');

MERGE INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN, LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID)
KEY (BATCH_APP_ID, INSTANCE_ID)
VALUES ('BATCH_EXEC_NPE',      'WAS2', 'Y', '20260101000000', 'SYSTEM');

MERGE INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN, LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID)
KEY (BATCH_APP_ID, INSTANCE_ID)
VALUES ('BATCH_EXEC_BIZ_FAIL', 'WAS2', 'Y', '20260101000000', 'SYSTEM');

MERGE INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH (BATCH_APP_ID, INSTANCE_ID, USE_YN, LAST_UPDATE_DTIME, LAST_UPDATE_USER_ID)
KEY (BATCH_APP_ID, INSTANCE_ID)
VALUES ('BATCH_PARTIAL_FAIL',  'WAS2', 'Y', '20260101000000', 'SYSTEM');
