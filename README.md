# HanaBank NBC MIS 배치 프레임워크 데모

## 패키지 구조

| 패키지 | 역할 |
|---|---|
| `com.hanabank.nbc.mis.batch.framework` | 배치 프레임워크 핵심 (스케줄링, 이력, REST API) |
| `com.hanabank.nbc.mis.batch.demo` | 데모 배치 잡 7종 |
| `com.hanabank.nbc.mis.batch.config` | Spring Security 설정 |

## 모듈 구조

```
batch-demo/
├── batch-framework/    ← 프레임워크 라이브러리 (JAR)
└── batch-app/          ← Spring Boot Web Application (실행 파일)
```

## 기동 방법

```bash
# WAS1 기동 (배치: BATCH_DAILY_REPORT, BATCH_DATA_SYNC, BATCH_SLOW_PROCESS)
java -Dbatch.instance.id=WAS1 -jar batch-app/target/batch-app-1.0.0-SNAPSHOT.jar

# WAS2 기동 (배치: BATCH_INIT_FAIL, BATCH_EXEC_NPE, BATCH_EXEC_BIZ_FAIL, BATCH_PARTIAL_FAIL)
java -Dbatch.instance.id=WAS2 -jar batch-app/target/batch-app-1.0.0-SNAPSHOT.jar
```

## 빌드

```bash
cd batch-demo
mvn clean package -DskipTests
```

## DB 초기 데이터 등록

```bash
sqlplus D_SPIDERLINK/password@localhost:1521/ORCL @batch-app/src/main/resources/sql/setup_demo_data.sql
```

## 데모 배치 잡 목록

| # | BATCH_APP_ID | 클래스 | 시나리오 | Cron | WAS |
|---|---|---|---|---|---|
| 1 | BATCH_DAILY_REPORT | DailyReportBatchJob | **정상** — 100건 처리 | 매일 01:00 | WAS1 |
| 2 | BATCH_DATA_SYNC | DataSyncBatchJob | **정상** — 500건 청크처리, 선행배치 의존 | 매일 02:30 | WAS1 |
| 3 | BATCH_INIT_FAIL | InitFailBatchJob | **init() 실패** — apiKey 파라미터 누락 | 매일 03:00 | WAS2 |
| 4 | BATCH_EXEC_NPE | ExecNpeBatchJob | **NPE 발생** — 미처리 예외 프레임워크 포착 | 매일 04:00 | WAS2 |
| 5 | BATCH_EXEC_BIZ_FAIL | ExecBusinessFailBatchJob | **비즈니스 오류** — 150번째에서 중단 | 매일 03:30 | WAS2 |
| 6 | BATCH_PARTIAL_FAIL | PartialFailBatchJob | **부분 실패** — 1,000건 중 10% 실패 | 매일 05:00 | WAS2 |
| 7 | BATCH_SLOW_PROCESS | SlowProcessBatchJob | **대용량** — 10,000건 상세 진행률 로그 | 매일 자정 | WAS1 |

## REST API 수동 실행

```bash
# 정상 실행
curl -X POST http://localhost:8080/api/batch/BATCH_DAILY_REPORT/run \
  -u batchadmin:batchpass \
  -H "Content-Type: application/json" \
  -d '{"baseBatchDate":"20240114","forceRerun":false,"params":{}}'

# 강제 재실행 (SUCCESS → CANCELED 후 재실행)
curl -X POST http://localhost:8080/api/batch/BATCH_DAILY_REPORT/run \
  -u batchadmin:batchpass \
  -H "Content-Type: application/json" \
  -d '{"baseBatchDate":"20240114","forceRerun":true,"params":{}}'

# init() 실패 배치에 apiKey 지정 후 실행
curl -X POST http://localhost:8080/api/batch/BATCH_INIT_FAIL/run \
  -u batchadmin:batchpass \
  -H "Content-Type: application/json" \
  -d '{"baseBatchDate":"20240114","forceRerun":false,"params":{"apiKey":"valid-key-123"}}'
```

## 예외 처리 4중 방어 구조

```
[Layer 1] BatchJob.init()         ──→ catch(Throwable) → FAILED_INIT
[Layer 2] BatchJob.executeBatch() ──→ catch(Throwable) → FAILED_EXEC
[Layer 3] execute() finally       ──→ writeFinalLog 절대 누락 방지
[Layer 4] Scheduler ErrorHandler  ──→ 스케줄러 스레드 보호 (재시작 불필요)
```

## 로그 파일

| 파일 | 내용 |
|---|---|
| `logs/batch-demo-WAS1.log` | 전체 로그 (Rolling, 100MB) |
| `logs/batch-demo-WAS1-error.log` | ERROR 레벨만 |
