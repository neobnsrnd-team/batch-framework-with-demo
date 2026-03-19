-- ================================================================
-- Batch Framework DDL (H2 In-Memory, Oracle 호환 모드)
-- Spring Boot 기동 시 자동 실행 (spring.sql.init.mode=always)
-- ================================================================

-- D_SPIDERLINK 스키마 생성
CREATE SCHEMA IF NOT EXISTS D_SPIDERLINK;

-- ----------------------------------------------------------------
-- FWK_BATCH_APP : 배치 앱 메타 정보
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS D_SPIDERLINK.FWK_BATCH_APP (
    BATCH_APP_ID          VARCHAR(50)   NOT NULL,   -- 배치 앱 ID (PK)
    BATCH_APP_NAME        VARCHAR(100),              -- 배치 앱 명
    BATCH_APP_FILE_NAME   VARCHAR(100),              -- 배치 클래스명
    BATCH_APP_DESC        VARCHAR(500),              -- 배치 설명
    PRE_BATCH_APP_ID      VARCHAR(50),               -- 선행 배치 앱 ID
    BATCH_CYCLE           VARCHAR(1),                -- 배치 주기 (D:일, W:주, M:월)
    CRON_TEXT             VARCHAR(100),              -- Cron 표현식
    RETRYABLE_YN          VARCHAR(1)  DEFAULT 'Y',  -- 재시도 가능 여부
    PER_WAS_YN            VARCHAR(1)  DEFAULT 'N',  -- WAS별 독립 실행 여부
    IMPORTANT_TYPE        VARCHAR(1)  DEFAULT 'N',  -- 중요도 (H/M/N)
    PROPERTIES            VARCHAR(2000),             -- 배치 파라미터 (JSON)
    TRX_ID                VARCHAR(50),
    ORG_ID                VARCHAR(50),
    IO_TYPE               VARCHAR(1),
    LAST_UPDATE_DTIME     VARCHAR(20),
    LAST_UPDATE_USER_ID   VARCHAR(50),
    PRIMARY KEY (BATCH_APP_ID)
);

-- ----------------------------------------------------------------
-- FWK_WAS_EXEC_BATCH : WAS 인스턴스별 실행 배치 매핑
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS D_SPIDERLINK.FWK_WAS_EXEC_BATCH (
    BATCH_APP_ID          VARCHAR(50)  NOT NULL,    -- 배치 앱 ID (PK)
    INSTANCE_ID           VARCHAR(4)   NOT NULL,    -- WAS 인스턴스 ID (PK, 최대 4자)
    USE_YN                VARCHAR(1)   DEFAULT 'Y', -- 사용 여부
    LAST_UPDATE_DTIME     VARCHAR(20),
    LAST_UPDATE_USER_ID   VARCHAR(50),
    PRIMARY KEY (BATCH_APP_ID, INSTANCE_ID)
);

-- ----------------------------------------------------------------
-- FWK_BATCH_HIS : 배치 실행 이력
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS D_SPIDERLINK.FWK_BATCH_HIS (
    BATCH_APP_ID          VARCHAR(50)  NOT NULL,    -- 배치 앱 ID (PK)
    INSTANCE_ID           VARCHAR(4)   NOT NULL,    -- WAS 인스턴스 ID (PK)
    BATCH_DATE            VARCHAR(8)   NOT NULL,    -- 배치 기준 일자 (PK, YYYYMMDD)
    BATCH_EXECUTE_SEQ     INTEGER      NOT NULL,    -- 실행 순번 (PK)
    LOG_DTIME             VARCHAR(20),              -- 시작 일시 (YYYYMMDDHH24MISS)
    BATCH_END_DTIME       VARCHAR(20),              -- 종료 일시 (YYYYMMDDHH24MISS)
    RES_RT_CODE           VARCHAR(20),              -- 결과코드 (RUNNING/SUCCESS/FAILED_INIT/FAILED_EXEC/CANCELED)
    ERROR_CODE            VARCHAR(100),             -- 오류 코드
    ERROR_REASON          VARCHAR(4000),            -- 오류 사유
    RECORD_COUNT          BIGINT,                   -- 처리 대상 건수
    EXECUTE_COUNT         BIGINT,                   -- 실행 건수
    SUCCESS_COUNT         BIGINT,                   -- 성공 건수
    FAIL_COUNT            BIGINT,                   -- 실패 건수
    LAST_UPDATE_USER_ID   VARCHAR(50),
    PRIMARY KEY (BATCH_APP_ID, INSTANCE_ID, BATCH_DATE, BATCH_EXECUTE_SEQ)
);
