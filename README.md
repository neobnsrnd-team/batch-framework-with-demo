# HanaBank NBC MIS 배치 프레임워크 데모

Spring Boot 기반 배치 프레임워크 데모 프로젝트입니다.
WAS 어피니티(인스턴스별 배치 분리), DB 기반 스케줄링, REST 수동 실행 API, 실행 이력 관리를 포함합니다.

---

## 목차

- [아키텍처](#아키텍처)
- [모듈 구조](#모듈-구조)
- [빠른 시작](#빠른-시작)
- [데이터베이스](#데이터베이스)
- [REST API](#rest-api)
- [데모 배치 잡 7종](#데모-배치-잡-7종)
- [Postman 테스트 시나리오](#postman-테스트-시나리오)
- [예외 처리 구조](#예외-처리-구조)
- [로그](#로그)

---

## 아키텍처

```
┌─────────────────────┐    ┌─────────────────────┐
│       WAS1          │    │       WAS2          │
│  BATCH_DAILY_REPORT │    │  BATCH_INIT_FAIL    │
│  BATCH_DATA_SYNC    │    │  BATCH_EXEC_NPE     │
│  BATCH_SLOW_PROCESS │    │  BATCH_EXEC_BIZ_FAIL│
│                     │    │  BATCH_PARTIAL_FAIL │
└──────────┬──────────┘    └──────────┬──────────┘
           │  REST API :8080           │
           └──────────────┬───────────┘
                          │
               ┌──────────▼──────────┐
               │   H2 In-Memory DB   │
               │  (Oracle 호환 모드) │
               │  FWK_BATCH_APP      │
               │  FWK_WAS_EXEC_BATCH │
               │  FWK_BATCH_HIS      │
               └─────────────────────┘
```

**WAS 어피니티**: `FWK_WAS_EXEC_BATCH` 테이블로 배치 잡과 WAS 인스턴스를 매핑.
각 WAS는 자신에게 할당된 배치만 스케줄링하며, 다른 WAS의 배치는 실행하지 않습니다.

---

## 모듈 구조

```
batch-demo/
├── batch-framework/                  ← 프레임워크 라이브러리 (JAR)
│   └── src/main/java/
│       └── framework/
│           ├── config/               ← BatchProperties, WasInstanceConfiguration
│           ├── context/              ← BatchExecutionContext
│           ├── core/                 ← BatchJob, AbstractBatchJob, BatchJobExecutor
│           ├── exception/            ← BatchInitException, BatchExecutionException
│           ├── mapper/               ← MyBatis Mapper (BatchSchedule, FwkBatchHis)
│           ├── scheduler/            ← BatchSchedulerManager (DB-driven Cron)
│           ├── vo/                   ← FwkBatchAppVo, FwkBatchHisVo, WasInstanceIdentity
│           └── web/                  ← BatchManualController (REST API)
│
├── batch-app/                        ← Spring Boot Web Application (실행 파일)
│   └── src/main/
│       ├── java/
│       │   ├── BatchDemoApplication.java
│       │   ├── config/SecurityConfig.java
│       │   └── demo/                 ← 데모 배치 잡 7종
│       └── resources/
│           ├── application.yml
│           ├── schema.sql            ← 기동 시 DDL 자동 실행
│           └── data.sql              ← 기동 시 초기 데이터 자동 삽입
│
├── batch-manual-execution.postman_collection.json  ← Postman 컬렉션
└── pom.xml                           ← 부모 POM (Java 17, Spring Boot 3.2.3)
```

---

## 빠른 시작

### 1. 빌드

```bash
mvn clean package -DskipTests
```

### 2. 기동

```bash
# WAS1 기동 (BATCH_DAILY_REPORT, BATCH_DATA_SYNC, BATCH_SLOW_PROCESS 담당)
java -Dbatch.instance.id=WAS1 -jar batch-app/target/batch-app-1.0.0-SNAPSHOT.jar

# WAS2 기동 (BATCH_INIT_FAIL, BATCH_EXEC_NPE, BATCH_EXEC_BIZ_FAIL, BATCH_PARTIAL_FAIL 담당)
java -Dbatch.instance.id=WAS2 -jar batch-app/target/batch-app-1.0.0-SNAPSHOT.jar
```

> 기동 시 `schema.sql` → `data.sql` 순으로 자동 실행되어 테이블과 초기 데이터가 생성됩니다.

### 3. H2 Console 접속 (개발용)

```
URL  : http://localhost:8080/h2-console
JDBC : jdbc:h2:mem:batchdb
User : sa
Pass : (없음)
```

---

## 데이터베이스

H2 In-Memory DB (Oracle 호환 모드 `MODE=Oracle`)를 사용합니다.
모든 테이블은 `D_SPIDERLINK` 스키마 아래에 생성됩니다.

### 테이블 구조

| 테이블 | 역할 |
|---|---|
| `FWK_BATCH_APP` | 배치 앱 메타 정보 (ID, 클래스명, Cron, 파라미터 등) |
| `FWK_WAS_EXEC_BATCH` | WAS 인스턴스별 실행 배치 매핑 (어피니티) |
| `FWK_BATCH_HIS` | 배치 실행 이력 (상태, 건수, 오류 정보) |

### 실행 결과 코드

| 코드 | 의미 |
|---|---|
| `RUNNING` | 실행 중 |
| `SUCCESS` | 정상 완료 |
| `FAILED_INIT` | `init()` 단계 실패 |
| `FAILED_EXEC` | `executeBatch()` 단계 실패 |
| `CANCELED` | `forceRerun`으로 이전 SUCCESS 무효화 |

---

## REST API

### 수동 실행 엔드포인트

```
POST /api/batch/{batchAppId}/run
Authorization: Basic batchadmin:batchpass
Content-Type: application/json
```

### 요청 본문

```json
{
  "baseBatchDate": "20260319",
  "forceRerun": false,
  "params": {
    "key": "value"
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `baseBatchDate` | String (YYYYMMDD) | 배치 기준 일자. 생략 시 오늘 날짜 |
| `forceRerun` | boolean | `true`이면 SUCCESS → CANCELED 후 재실행 |
| `params` | Map | 배치 잡에 전달할 추가 파라미터 |

### 응답

```json
{
  "batchAppId": "BATCH_DAILY_REPORT",
  "instanceId": "WAS1",
  "batchDate": "20260319",
  "executeSeq": 1,
  "resultCode": "SUCCESS",
  "message": "배치 실행 완료",
  "startDtime": "20260319010000",
  "endDtime": "20260319010012",
  "recordCount": 10000,
  "successCount": 10000,
  "failCount": 0
}
```

### HTTP 상태 코드

| 코드 | 상황 |
|---|---|
| `200 OK` | 실행 완료 (성공/실패 무관) |
| `400 Bad Request` | 요청 형식 오류 (날짜 포맷 등) |
| `401 Unauthorized` | 인증 실패 |
| `404 Not Found` | 존재하지 않는 BATCH_APP_ID |
| `409 Conflict` | 이미 SUCCESS 이력 있음 (forceRerun 필요) |

### cURL 예시

```bash
# 기본 실행
curl -X POST http://localhost:8080/api/batch/BATCH_DAILY_REPORT/run \
  -u batchadmin:batchpass \
  -H "Content-Type: application/json" \
  -d '{"baseBatchDate":"20260319","forceRerun":false,"params":{}}'

# 강제 재실행 (이미 SUCCESS인 경우)
curl -X POST http://localhost:8080/api/batch/BATCH_DAILY_REPORT/run \
  -u batchadmin:batchpass \
  -H "Content-Type: application/json" \
  -d '{"baseBatchDate":"20260319","forceRerun":true,"params":{}}'

# 파라미터 전달
curl -X POST http://localhost:8080/api/batch/BATCH_INIT_FAIL/run \
  -u batchadmin:batchpass \
  -H "Content-Type: application/json" \
  -d '{"baseBatchDate":"20260319","forceRerun":false,"params":{"apiKey":"VALID123"}}'
```

---

## 데모 배치 잡 7종

| # | BATCH_APP_ID | 클래스 | 시나리오 | 처리 건수 | WAS | Cron |
|---|---|---|---|---|---|---|
| 1 | `BATCH_DAILY_REPORT` | DailyReportBatchJob | **정상 완료** — 기본 처리 흐름 시연 | 10,000건 | WAS1 | 매일 01:00 |
| 2 | `BATCH_DATA_SYNC` | DataSyncBatchJob | **선행 배치 의존** — 500건 청크 처리 | 500건 | WAS1 | 매일 02:30 |
| 3 | `BATCH_INIT_FAIL` | InitFailBatchJob | **init() 실패** — `apiKey` 파라미터 누락 시 FAILED_INIT | — | WAS2 | 매일 03:00 |
| 4 | `BATCH_EXEC_NPE` | ExecNpeBatchJob | **미처리 예외** — NPE를 프레임워크가 포착 → FAILED_EXEC | — | WAS2 | 매일 04:00 |
| 5 | `BATCH_EXEC_BIZ_FAIL` | ExecBusinessFailBatchJob | **비즈니스 예외** — 특정 레코드에서 BatchExecutionException | ~150건 | WAS2 | 매일 03:30 |
| 6 | `BATCH_PARTIAL_FAIL` | PartialFailBatchJob | **부분 실패** — 10% 무작위 실패, 임계 20% 초과 시 중단 | 1,000건 | WAS2 | 매일 05:00 |
| 7 | `BATCH_SLOW_PROCESS` | SlowProcessBatchJob | **대용량** — 1,000건마다 진행률·속도·잔여시간 로그 | 10,000건 | WAS1 | 매일 자정 |

### 배치 잡 개발 패턴

```java
@Component
public class MyBatchJob extends AbstractBatchJob {

    @Override
    public String getBatchAppId() { return "BATCH_MY_JOB"; }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        // 파라미터 읽기, 유효성 검증
        String param = context.getParam("key", "defaultValue");
        context.setRecordCount(totalCount);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        for (Record r : records) {
            try {
                process(r);
                context.countSuccess();
            } catch (Exception e) {
                context.countFail();   // fail-fast=false: 계속 진행
            }
        }
    }
}
```

---

## Postman 테스트 시나리오

컬렉션 파일: `batch-manual-execution.postman_collection.json`

**Import 방법**: Postman → `File` → `Import` → 파일 선택

**컬렉션 변수**

| 변수 | 기본값 | 설명 |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | 서버 주소 |
| `username` | `batchadmin` | Basic Auth 사용자명 |
| `password` | `batchpass` | Basic Auth 패스워드 |
| `today` | `20260319` | 오늘 날짜 (YYYYMMDD) |
| `yesterday` | `20260318` | 어제 날짜 (YYYYMMDD) |

---

### 01. BATCH_DAILY_REPORT — 정상 완료 시나리오 (7건)

| # | 요청명 | 주요 파라미터 | 기대 결과 |
|---|---|---|---|
| 01-01 | 기본 실행 (파라미터 없음) | `forceRerun: false` | `200 OK` / `SUCCESS` |
| 01-02 | 커스텀 파라미터 | `reportType: DAILY`, `threshold: 500` | `200 OK` / `SUCCESS` |
| 01-03 | 어제 날짜 기준 실행 | `baseBatchDate: {{yesterday}}` | `200 OK` / `SUCCESS` |
| 01-04 | 중복 실행 시도 | 01-01 실행 후 동일 요청 | `409 Conflict` |
| 01-05 | 강제 재실행 | `forceRerun: true` | `200 OK` / `SUCCESS` (이전 SUCCESS → CANCELED) |
| 01-06 | threshold=0 초과 | `threshold: 0` | `200 OK` / `FAILED_EXEC` |
| 01-07 | threshold 형식 오류 | `threshold: "abc"` | `200 OK` / `FAILED_INIT` |

---

### 02. BATCH_DATA_SYNC — 선행 배치 의존 시나리오 (4건)

> **주의**: `BATCH_DATA_SYNC`는 `BATCH_DAILY_REPORT`의 당일 SUCCESS가 있어야 실행됩니다.

| # | 요청명 | 주요 파라미터 | 기대 결과 |
|---|---|---|---|
| 02-01 | 선행 배치 완료 후 실행 | (BATCH_DAILY_REPORT 실행 후) | `200 OK` / `SUCCESS` |
| 02-02 | 선행 배치 미완료 | (BATCH_DAILY_REPORT 없이 실행) | `200 OK` / `SKIPPED` |
| 02-03 | forceRerun으로 재실행 | `forceRerun: true` | `200 OK` / `SUCCESS` |
| 02-04 | chunkSize 변경 | `chunkSize: 100` | `200 OK` / `SUCCESS` |

---

### 03. BATCH_INIT_FAIL — init() 실패 시나리오 (3건)

> **포인트**: `apiKey` 파라미터 유무와 값에 따라 결과가 달라집니다.

| # | 요청명 | 주요 파라미터 | 기대 결과 |
|---|---|---|---|
| 03-01 | apiKey 없음 | `params: {}` | `200 OK` / `FAILED_INIT` (필수 파라미터 누락) |
| 03-02 | apiKey 무효 | `apiKey: INVALID` | `200 OK` / `FAILED_INIT` (API 인증 실패) |
| 03-03 | apiKey 유효 | `apiKey: VALID123` | `200 OK` / `SUCCESS` |

---

### 04. BATCH_EXEC_NPE — 미처리 예외 시나리오 (2건)

> **포인트**: `executeBatch()` 내 NullPointerException을 프레임워크 Layer2가 포착하여 FAILED_EXEC으로 기록합니다.

| # | 요청명 | 주요 파라미터 | 기대 결과 |
|---|---|---|---|
| 04-01 | NPE 발생 | `params: {}` | `200 OK` / `FAILED_EXEC` (errorCode: `UNEXPECTED_ERROR`) |
| 04-02 | 강제 재실행 | `forceRerun: true` | `200 OK` / `FAILED_EXEC` (동일 결과 반복) |

---

### 05. BATCH_EXEC_BIZ_FAIL — 비즈니스 예외 시나리오 (3건)

> **포인트**: `failAtRecord` 파라미터로 실패 시점을 제어합니다. 총 건수보다 크면 전체 성공.

| # | 요청명 | 주요 파라미터 | 기대 결과 |
|---|---|---|---|
| 05-01 | 기본 실행 | `failAtRecord: 150` (기본값) | `200 OK` / `FAILED_EXEC` (150번째 레코드에서 중단) |
| 05-02 | 조기 실패 | `failAtRecord: 10` | `200 OK` / `FAILED_EXEC` (10번째에서 중단) |
| 05-03 | 전체 성공 | `failAtRecord: 9999` | `200 OK` / `SUCCESS` (failAtRecord > totalRecords) |

---

### 06. BATCH_PARTIAL_FAIL — 부분 실패 시나리오 (4건)

> **포인트**: 단건 실패는 계속 진행(fail-fast=false). 누적 실패율이 `maxFailRatePercent` 초과 시 배치 중단.

| # | 요청명 | 주요 파라미터 | 기대 결과 |
|---|---|---|---|
| 06-01 | 기본 실행 (10% 실패, 임계 20%) | `failRatePercent: 10`, `maxFailRatePercent: 20` | `200 OK` / `SUCCESS` (실패율 < 임계치) |
| 06-02 | 실패율 초과 | `failRatePercent: 25`, `maxFailRatePercent: 20` | `200 OK` / `FAILED_EXEC` (실패율 임계치 초과) |
| 06-03 | 소량 처리 | `totalRecords: 100`, `failRatePercent: 5` | `200 OK` / `SUCCESS` |
| 06-04 | 실패율 0% | `failRatePercent: 0` | `200 OK` / `SUCCESS` (실패 없음) |

---

### 07. BATCH_SLOW_PROCESS — 대용량 처리 시나리오 (2건)

> **포인트**: 1,000건마다 처리 속도(건/초), 경과 시간, 예상 잔여 시간을 로그로 출력합니다.

| # | 요청명 | 주요 파라미터 | 기대 결과 |
|---|---|---|---|
| 07-01 | 소량 빠른 실행 | `totalRecords: 100`, `msPerRecord: 1` | `200 OK` / `SUCCESS` |
| 07-02 | 1,000건 처리 | `totalRecords: 1000`, `msPerRecord: 1` | `200 OK` / `SUCCESS` |

---

### 08. 에러 케이스 — HTTP 예외 처리 (5건)

| # | 요청명 | 상황 | 기대 결과 |
|---|---|---|---|
| 08-01 | 존재하지 않는 배치 ID | `BATCH_NONEXISTENT` | `404 Not Found` |
| 08-02 | 인증 헤더 없음 | Authorization 헤더 제거 | `401 Unauthorized` |
| 08-03 | 잘못된 비밀번호 | `password: wrongpass` | `401 Unauthorized` |
| 08-04 | 날짜 형식 오류 | `baseBatchDate: "2026-03-19"` | `400 Bad Request` |
| 08-05 | 날짜 숫자 아님 | `baseBatchDate: "YYYYMMDD"` | `400 Bad Request` |

---

### 09. forceRerun 연속 흐름 — Step-by-step (4건)

> SUCCESS 이력이 있는 상태에서 재실행 시 409, forceRerun=true로 CANCELED 후 재실행되는 흐름을 단계별로 확인합니다.

| 단계 | 요청명 | 조건 | 기대 결과 |
|---|---|---|---|
| Step 1 | 최초 실행 | 이력 없음 | `200 OK` / `SUCCESS` (executeSeq=1) |
| Step 2 | 중복 실행 시도 | Step1 완료 후 동일 날짜 재실행 | `409 Conflict` |
| Step 3 | 강제 재실행 | `forceRerun: true` | `200 OK` / `SUCCESS` (이전 SUCCESS → CANCELED, executeSeq=2) |
| Step 4 | 3차 강제 재실행 | `forceRerun: true` | `200 OK` / `SUCCESS` (executeSeq=3) |

---

### Postman 테스트 스크립트

각 요청에는 자동 검증 스크립트가 포함되어 있습니다:

```javascript
// 예시: 01-01 기본 실행
pm.test("HTTP 200 응답", () => {
    pm.response.to.have.status(200);
});
pm.test("SUCCESS 결과 코드", () => {
    const body = pm.response.json();
    pm.expect(body.resultCode).to.eql("SUCCESS");
});
pm.test("응답 필드 존재 확인", () => {
    const body = pm.response.json();
    pm.expect(body).to.have.all.keys("batchAppId","instanceId","batchDate","executeSeq","resultCode");
});
```

---

## 예외 처리 구조

배치 프레임워크는 4중 방어 구조로 어떤 예외도 스케줄러 스레드를 종료시키지 않습니다.

```
[Layer 1] BatchJob.init()
          └─ BatchInitException → FAILED_INIT 기록 후 종료
          └─ 기타 Throwable   → FAILED_INIT 기록 후 종료

[Layer 2] BatchJob.executeBatch()
          └─ BatchExecutionException → FAILED_EXEC 기록 후 종료
          └─ 기타 Throwable          → FAILED_EXEC 기록 후 종료

[Layer 3] execute() finally 블록
          └─ writeFinalLog 누락 방지 (RUNNING 상태 잔류 방지)

[Layer 4] Scheduler ErrorHandler
          └─ 스케줄러 스레드 사망 방지 (배치 중단 없이 다음 Cron 실행 보장)
```

---

## 로그

| 파일 | 내용 |
|---|---|
| `logs/batch-demo-{instanceId}.log` | 전체 로그 (Rolling, 최대 100MB) |
| `logs/batch-demo-{instanceId}-error.log` | ERROR 레벨만 별도 수집 |

로그 패턴:
```
[BATCH_DAILY_REPORT][INIT] 파라미터: {totalRecords=10000, msPerRecord=1}
[BATCH_DAILY_REPORT][EXEC] 진행: 1000/10000 (10.0%) | 성공=1000 | 속도=987건/초 | 경과=1초 | 예상잔여=9초
[BATCH_DAILY_REPORT][EXEC] 처리 완료 | 대상=10000, 성공=10000, 실패=0, 총소요=10초
```

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.3 |
| MyBatis Spring Boot Starter | 3.0.3 |
| H2 Database | (Spring Boot 관리) |
| Spring Security | (Spring Boot 관리) |
| Maven | 3.x |
