# Spring Boot 3.x 기반 배치 프레임워크 설계 계획
> **DB**: Oracle (`D_SPIDERLINK` 스키마) | **DB 라이브러리**: MyBatis
> **인프라**: 일반 WAS (Kubernetes 미사용) | **WAS 식별**: `INSTANCE_ID` (application.properties)
> **스케줄링**: `FWK_BATCH_APP.CRON_TEXT` → Spring `ThreadPoolTaskScheduler` 동적 등록

---

## 1. 전체 아키텍처

```
┌──────────────────────────────────────────────────────────────────────────┐
│  WAS 클러스터                                                             │
│                                                                          │
│  ┌──────────────────────────┐   ┌──────────────────────────┐             │
│  │  WAS Instance (WAS1)     │   │  WAS Instance (WAS2)     │             │
│  │                          │   │                          │             │
│  │  [기동 시]               │   │  [기동 시]               │             │
│  │  BatchSchedulerManager   │   │  BatchSchedulerManager   │             │
│  │    JOIN 쿼리 실행         │   │    JOIN 쿼리 실행         │             │
│  │    ↓                     │   │    ↓                     │             │
│  │  TaskScheduler 등록       │   │  TaskScheduler 등록       │             │
│  │   └ JobA: "0 0 1 * * ?"  │   │   └ JobB: "0 30 2 * * ?" │             │
│  │   └ JobB: "0 30 2 * * ?" │   │                          │             │
│  │                          │   │                          │             │
│  │  [Cron 트리거 발동 시]   │   │  [Cron 트리거 발동 시]   │             │
│  │  BatchJobExecutor        │   │  BatchJobExecutor        │             │
│  │   └ init()               │   │   └ init()               │             │
│  │   └ executeBatch()       │   │   └ executeBatch()       │             │
│  └────────────┬─────────────┘   └────────────┬─────────────┘             │
└───────────────┼────────────────────────────── ┼──────────────────────────┘
                │                               │
                └──────────────┬────────────────┘
                               │
                     ┌─────────▼──────────────┐
                     │  Oracle DB             │
                     │  D_SPIDERLINK 스키마   │
                     │                        │
                     │  FWK_BATCH_APP         │ ← 배치 정의 + CRON_TEXT
                     │  FWK_WAS_EXEC_BATCH    │ ← WAS별 배치 매핑
                     │  FWK_BATCH_HIS         │ ← 실행 이력
                     └────────────────────────┘
```

**핵심 설계 결정:**
- **Spring Boot Web Application** — 배치가 웹 앱 내부에 포함 (REST 수동 실행 지원)
- **MyBatis** 사용 (JPA/Spring Data 미사용)
- **Kubernetes 미사용** — WAS 인스턴스를 `INSTANCE_ID`(최대 4자)로 식별
- **기동 시 JOIN 쿼리** — `FWK_WAS_EXEC_BATCH` JOIN `FWK_BATCH_APP` 로 현재 WAS 담당 배치 로드
- **`CRON_TEXT`** — 각 배치의 Cron 표현식을 DB에서 읽어 `ThreadPoolTaskScheduler`에 동적 등록
- **REST 수동 실행** — 배치 실패 시 `POST /api/batch/{batchAppId}/run` 으로 수동 재실행
- **배치기준일자 파라미터** — 수동 실행 시 `baseBatchDate`(어제, 특정일 등) 지정 가능
- **`FWK_BATCH_HIS`** — 실행 이력 (기존 테이블 그대로 활용)
- 신규 테이블 없음 — 기존 3개 테이블만 사용

---

## 2. 기존 테이블 분석

### 2.1 `FWK_BATCH_APP` — 배치 앱 정의

| 컬럼 | 프레임워크 활용 |
|---|---|
| `BATCH_APP_ID` | 배치 고유 ID, `BatchJob.getBatchAppId()` 반환값과 매핑 |
| `BATCH_APP_FILE_NAME` | Spring Bean 클래스 단순명으로 매핑 |
| `PRE_BATCH_APP_ID` | 선행 배치 ID — 당일 완료 여부 확인 후 실행 |
| `RETRYABLE_YN` | 실패 시 재시도 허용 (BATCH_EXECUTE_SEQ 증가) |
| `PER_WAS_YN` | 참고용 (실제 어피니티는 FWK_WAS_EXEC_BATCH로 제어) |
| `PROPERTIES` | 배치 기본 파라미터 (JSON) — CLI 인자로 오버라이드 가능 |
| `BATCH_CYCLE` | 실행 주기 (D=일, W=주, M=월, H=시간, O=일회성) |

### 2.2 `FWK_WAS_EXEC_BATCH` — WAS별 배치 실행 매핑

```
PK: BATCH_APP_ID + INSTANCE_ID
```

| 컬럼 | 설명 |
|---|---|
| `BATCH_APP_ID` | 배치 앱 ID |
| `INSTANCE_ID` | WAS 인스턴스 ID (최대 4자: WAS1, WAS2...) |
| `USE_YN` | 'Y' = 현재 WAS에서 실행 / 'N' = 실행 안 함 |

**어피니티 규칙:**
- 현재 WAS의 `INSTANCE_ID`로 `FWK_WAS_EXEC_BATCH` 조회
- `USE_YN='Y'` 인 배치만 현재 WAS에서 실행
- 행 없거나 `USE_YN='N'` → 스킵

### 2.3 `FWK_BATCH_HIS` — 배치 실행 이력

```
PK: BATCH_APP_ID + INSTANCE_ID + BATCH_DATE + BATCH_EXECUTE_SEQ
```

| 컬럼 | 프레임워크 활용 |
|---|---|
| `BATCH_APP_ID` | 배치 앱 ID |
| `INSTANCE_ID` | 실행한 WAS 인스턴스 ID |
| `BATCH_DATE` | 실행 날짜 (YYYYMMDD) |
| `BATCH_EXECUTE_SEQ` | 당일 실행 순번 (재시도 시 증가, 기본 1) |
| `LOG_DTIME` | 시작 일시 (YYYYMMDDHH24MISSFF3) ← **init() 전 INSERT** |
| `BATCH_END_DTIME` | 종료 일시 ← **executeBatch() 완료 후 UPDATE** |
| `RES_RT_CODE` | 결과 코드 (SUCCESS / FAILED_INIT / FAILED_EXEC / SKIPPED) |
| `ERROR_CODE` | 오류 코드 |
| `ERROR_REASON` | 오류 상세 (최대 4000자) |
| `RECORD_COUNT` | 총 처리 대상 건수 |
| `EXECUTE_COUNT` | 실행 건수 |
| `SUCCESS_COUNT` | 성공 건수 |
| `FAIL_COUNT` | 실패 건수 |
| `LAST_UPDATE_USER_ID` | 배치 앱 ID (시스템 값) |

---

## 3. 프로젝트 모듈 구조

```
batch-demo/
├── pom.xml                                        (부모 POM)
├── batch-framework/                               (핵심 라이브러리)
│   ├── pom.xml
│   └── src/main/java/com/example/batch/
│       ├── config/
│       │   ├── BatchFrameworkAutoConfiguration.java
│       │   ├── BatchProperties.java
│       │   ├── BatchSchedulerConfig.java
│       │   └── WasInstanceConfiguration.java
│       ├── context/
│       │   └── BatchExecutionContext.java
│       ├── core/
│       │   ├── BatchJob.java
│       │   ├── AbstractBatchJob.java
│       │   └── BatchJobResult.java
│       ├── exception/
│       │   ├── BatchException.java
│       │   ├── BatchInitException.java
│       │   └── BatchExecutionException.java
│       ├── logging/
│       │   └── BatchLogService.java
│       ├── mapper/
│       │   ├── BatchScheduleMapper.java
│       │   └── FwkBatchHisMapper.java
│       ├── scheduler/
│       │   └── BatchSchedulerManager.java
│       ├── web/                                   ← REST 수동 실행 (신규)
│       │   ├── BatchManualController.java         ← POST /api/batch/{id}/run
│       │   ├── BatchRunRequest.java               ← 요청 DTO
│       │   └── BatchRunResponse.java              ← 응답 DTO
│       ├── vo/
│       │   ├── BatchScheduleVo.java
│       │   └── FwkBatchHisVo.java
│       └── WasInstanceIdentity.java
│
│   └── src/main/resources/
│       └── mapper/
│           ├── BatchScheduleMapper.xml
│           └── FwkBatchHisMapper.xml
│
└── batch-app/                                     (Spring Boot Web 애플리케이션)
    ├── pom.xml
    └── src/main/java/com/example/app/
        ├── BatchDemoApplication.java              ← @SpringBootApplication
        ├── job/
        │   ├── DailyReportJob.java
        │   └── DataSyncJob.java
        └── config/
            └── SecurityConfig.java               ← REST API 인증 설정
```

---

## 4. 의존성 (pom.xml)

```xml
<!-- batch-framework/pom.xml 주요 의존성 -->

<!-- Spring Boot Web (REST API 수동 실행 지원) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring Security (REST API 인증) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- MyBatis Spring Boot Starter -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>

<!-- Oracle JDBC -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>23.3.0.23.09</version>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Jackson (PROPERTIES JSON 파싱, REST 요청/응답) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## 5. MyBatis VO 클래스

### 5.1 `FwkBatchAppVo`

```java
package com.example.batch.vo;

public class FwkBatchAppVo {
    private String batchAppId;
    private String batchAppName;
    private String batchAppFileName;    // Spring Bean 클래스명과 매핑
    private String batchAppDesc;
    private String preBatchAppId;       // 선행 배치 ID
    private String batchCycle;          // D|W|M|H|O
    private String cronText;
    private String retryableYn;         // Y|N
    private String perWasYn;            // Y|N
    private String importantType;       // A|B|C
    private String properties;          // JSON 기본 파라미터
    private String trxId;
    private String orgId;
    private String ioType;              // I|O|B
    private String lastUpdateDtime;
    private String lastUpdateUserId;
    // Getter / Setter
}
```

### 5.2 `FwkWasExecBatchVo`

```java
package com.example.batch.vo;

public class FwkWasExecBatchVo {
    private String batchAppId;
    private String instanceId;
    private String useYn;               // Y|N
    private String lastUpdateDtime;
    private String lastUpdateUserId;
    // Getter / Setter
}
```

### 5.3 `FwkBatchHisVo`

```java
package com.example.batch.vo;

public class FwkBatchHisVo {
    // PK
    private String batchAppId;
    private String instanceId;
    private String baseBatchDate;           // YYYYMMDD
    private int    batchExecuteSeq;     // 당일 실행 순번

    // 시작 시 INSERT
    private String logDtime;            // YYYYMMDDHH24MISSFF3
    private String resRtCode;           // RUNNING → SUCCESS|FAILED_INIT|FAILED_EXEC

    // 종료 시 UPDATE
    private String batchEndDtime;
    private String lastUpdateUserId;
    private String errorCode;
    private String errorReason;
    private Long   recordCount;
    private Long   executeCount;
    private Long   successCount;
    private Long   failCount;
    // Getter / Setter
}
```

---

## 6. MyBatis Mapper 인터페이스

### 6.1 `FwkBatchAppMapper`

```java
package com.example.batch.mapper;

@Mapper
public interface FwkBatchAppMapper {

    /** BATCH_APP_ID로 단건 조회 */
    FwkBatchAppVo selectById(@Param("batchAppId") String batchAppId);
}
```

### 6.2 `FwkWasExecBatchMapper`

```java
package com.example.batch.mapper;

@Mapper
public interface FwkWasExecBatchMapper {

    /**
     * 특정 WAS 인스턴스에서 실행 가능한 배치 ID 목록 조회
     * (USE_YN = 'Y')
     */
    List<String> selectEligibleBatchAppIds(@Param("instanceId") String instanceId);
}
```

### 6.3 `FwkBatchHisMapper`

```java
package com.example.batch.mapper;

@Mapper
public interface FwkBatchHisMapper {

    /**
     * 배치 실행 이력 INSERT (시작 시)
     * LOG_DTIME, RES_RT_CODE='RUNNING', 기타 0 초기화
     */
    int insert(FwkBatchHisVo vo);

    /**
     * 배치 종료 시 UPDATE
     * BATCH_END_DTIME, RES_RT_CODE, 카운터, 오류 정보
     */
    int update(FwkBatchHisVo vo);

    /**
     * 당일 최대 BATCH_EXECUTE_SEQ 조회
     * 없으면 NULL 반환 → 서비스에서 1로 처리
     */
    Integer selectMaxExecuteSeq(@Param("batchAppId")  String batchAppId,
                                @Param("instanceId")  String instanceId,
                                @Param("baseBatchDate")   String baseBatchDate);

    /**
     * 선행 배치 당일 SUCCESS 이력 존재 여부
     * (어느 WAS 인스턴스에서든 SUCCESS이면 OK)
     */
    boolean existsSuccessToday(@Param("batchAppId") String batchAppId,
                                @Param("baseBatchDate")  String baseBatchDate);
}
```

---

## 7. MyBatis XML 매퍼

### 7.1 `FwkBatchAppMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.batch.mapper.FwkBatchAppMapper">

    <resultMap id="FwkBatchAppResultMap" type="com.example.batch.vo.FwkBatchAppVo">
        <id     property="batchAppId"       column="BATCH_APP_ID"/>
        <result property="batchAppName"     column="BATCH_APP_NAME"/>
        <result property="batchAppFileName" column="BATCH_APP_FILE_NAME"/>
        <result property="batchAppDesc"     column="BATCH_APP_DESC"/>
        <result property="preBatchAppId"    column="PRE_BATCH_APP_ID"/>
        <result property="batchCycle"       column="BATCH_CYCLE"/>
        <result property="cronText"         column="CRON_TEXT"/>
        <result property="retryableYn"      column="RETRYABLE_YN"/>
        <result property="perWasYn"         column="PER_WAS_YN"/>
        <result property="importantType"    column="IMPORTANT_TYPE"/>
        <result property="properties"       column="PROPERTIES"/>
        <result property="trxId"            column="TRX_ID"/>
        <result property="orgId"            column="ORG_ID"/>
        <result property="ioType"           column="IO_TYPE"/>
        <result property="lastUpdateDtime"  column="LAST_UPDATE_DTIME"/>
        <result property="lastUpdateUserId" column="LAST_UPDATE_USER_ID"/>
    </resultMap>

    <select id="selectById" resultMap="FwkBatchAppResultMap">
        SELECT BATCH_APP_ID,
               BATCH_APP_NAME,
               BATCH_APP_FILE_NAME,
               BATCH_APP_DESC,
               PRE_BATCH_APP_ID,
               BATCH_CYCLE,
               CRON_TEXT,
               RETRYABLE_YN,
               PER_WAS_YN,
               IMPORTANT_TYPE,
               PROPERTIES,
               TRX_ID,
               ORG_ID,
               IO_TYPE,
               LAST_UPDATE_DTIME,
               LAST_UPDATE_USER_ID
          FROM D_SPIDERLINK.FWK_BATCH_APP
         WHERE BATCH_APP_ID = #{batchAppId}
    </select>

</mapper>
```

### 7.2 `FwkWasExecBatchMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.batch.mapper.FwkWasExecBatchMapper">

    <!-- 특정 WAS에서 USE_YN='Y' 인 배치 ID 목록 -->
    <select id="selectEligibleBatchAppIds" resultType="string">
        SELECT BATCH_APP_ID
          FROM D_SPIDERLINK.FWK_WAS_EXEC_BATCH
         WHERE INSTANCE_ID = #{instanceId}
           AND USE_YN      = 'Y'
         ORDER BY BATCH_APP_ID
    </select>

</mapper>
```

### 7.3 `FwkBatchHisMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.batch.mapper.FwkBatchHisMapper">

    <!-- INSERT: 배치 시작 시 -->
    <insert id="insert" parameterType="com.example.batch.vo.FwkBatchHisVo">
        INSERT INTO D_SPIDERLINK.FWK_BATCH_HIS (
            BATCH_APP_ID,
            INSTANCE_ID,
            BATCH_DATE,
            BATCH_EXECUTE_SEQ,
            LOG_DTIME,
            BATCH_END_DTIME,
            RES_RT_CODE,
            LAST_UPDATE_USER_ID,
            ERROR_CODE,
            ERROR_REASON,
            RECORD_COUNT,
            EXECUTE_COUNT,
            SUCCESS_COUNT,
            FAIL_COUNT
        ) VALUES (
            #{batchAppId},
            #{instanceId},
            #{baseBatchDate},
            #{batchExecuteSeq},
            #{logDtime},
            NULL,
            #{resRtCode},
            #{lastUpdateUserId},
            NULL,
            NULL,
            0,
            0,
            0,
            0
        )
    </insert>

    <!-- UPDATE: 배치 종료 시 -->
    <update id="update" parameterType="com.example.batch.vo.FwkBatchHisVo">
        UPDATE D_SPIDERLINK.FWK_BATCH_HIS
           SET BATCH_END_DTIME    = #{batchEndDtime},
               RES_RT_CODE        = #{resRtCode},
               LAST_UPDATE_USER_ID = #{lastUpdateUserId},
               ERROR_CODE         = #{errorCode},
               ERROR_REASON       = #{errorReason},
               RECORD_COUNT       = #{recordCount},
               EXECUTE_COUNT      = #{executeCount},
               SUCCESS_COUNT      = #{successCount},
               FAIL_COUNT         = #{failCount}
         WHERE BATCH_APP_ID       = #{batchAppId}
           AND INSTANCE_ID        = #{instanceId}
           AND BATCH_DATE         = #{baseBatchDate}
           AND BATCH_EXECUTE_SEQ  = #{batchExecuteSeq}
    </update>

    <!-- 당일 최대 BATCH_EXECUTE_SEQ 조회 -->
    <select id="selectMaxExecuteSeq" resultType="java.lang.Integer">
        SELECT MAX(BATCH_EXECUTE_SEQ)
          FROM D_SPIDERLINK.FWK_BATCH_HIS
         WHERE BATCH_APP_ID = #{batchAppId}
           AND INSTANCE_ID  = #{instanceId}
           AND BATCH_DATE   = #{baseBatchDate}
    </select>

    <!-- 선행 배치 당일 SUCCESS 존재 여부 -->
    <select id="existsSuccessToday" resultType="boolean">
        SELECT CASE
                   WHEN COUNT(*) > 0 THEN 1
                   ELSE 0
               END
          FROM D_SPIDERLINK.FWK_BATCH_HIS
         WHERE BATCH_APP_ID = #{batchAppId}
           AND BATCH_DATE   = #{baseBatchDate}
           AND RES_RT_CODE  = 'SUCCESS'
    </select>

</mapper>
```

---

## 8. WAS 인스턴스 식별 (System Property 기반)

`INSTANCE_ID`는 JVM 기동 시 `-D` 옵션으로 전달하는 **System Property**에서 읽습니다.
`application.yml`에 하드코딩하지 않으므로 동일 패키지로 여러 WAS를 운영 가능합니다.

### 8.1 JVM 기동 옵션 (운영 설정)

```bash
# WAS1 기동 시
java -Dbatch.instance.id=WAS1 -jar batch-app.jar

# WAS2 기동 시
java -Dbatch.instance.id=WAS2 -jar batch-app.jar
```

### 8.2 `WasInstanceIdentity`

```java
package com.example.batch;

public class WasInstanceIdentity {
    private final String instanceId;   // FWK_WAS_EXEC_BATCH.INSTANCE_ID (최대 4자)
    private final String hostName;

    public WasInstanceIdentity(String instanceId, String hostName) {
        this.instanceId = instanceId;
        this.hostName   = hostName;
    }
    public String getInstanceId() { return instanceId; }
    public String getHostName()   { return hostName;   }
}
```

### 8.3 `WasInstanceConfiguration`

```java
@Configuration
public class WasInstanceConfiguration {

    /** System Property 키 */
    private static final String SYS_PROP_KEY = "batch.instance.id";

    @Bean
    public WasInstanceIdentity wasInstanceIdentity() {
        // System Property 우선, 없으면 환경변수 BATCH_INSTANCE_ID 폴백
        String instanceId = System.getProperty(SYS_PROP_KEY);
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = System.getenv("BATCH_INSTANCE_ID");
        }
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalStateException(
                "WAS 인스턴스 ID 미설정. JVM 옵션 -D" + SYS_PROP_KEY + "=WAS1 필수.");
        }
        if (instanceId.length() > 4) {
            throw new IllegalStateException(
                SYS_PROP_KEY + " 최대 4자 (FWK_WAS_EXEC_BATCH.INSTANCE_ID 컬럼 제약). " +
                "현재값: [" + instanceId + "]");
        }
        String host = resolveHostName();
        log.info("WAS 인스턴스 식별 완료: instanceId=[{}], host=[{}]", instanceId, host);
        return new WasInstanceIdentity(instanceId, host);
    }

    private String resolveHostName() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }
}
```

### 8.4 `BatchProperties`

```java
@ConfigurationProperties(prefix = "batch")
public class BatchProperties {

    /** true 이면 첫 번째 배치 실패 시 즉시 중단 (스케줄러 모드에서는 해당 회차만 실패 처리) */
    private boolean failFast = false;

    /** 스케줄러 스레드 풀 크기 (동시 실행 가능한 배치 수) */
    private int schedulerPoolSize = 5;

    // Getter / Setter
}
```

### 8.5 `application.yml`

```yaml
spring:
  application:
    name: batch-demo-app
  datasource:
    url: jdbc:oracle:thin:@//db-host:1521/DBSID
    username: D_SPIDERLINK
    password: ${DB_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver

mybatis:
  mapper-locations: classpath:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: false
    jdbc-type-for-null: NULL
  type-aliases-package: com.example.batch.vo

batch:
  fail-fast: false
  scheduler-pool-size: 5
  # instance-id 는 JVM 옵션으로만 설정: -Dbatch.instance.id=WAS1
```

---

## 9. 핵심 인터페이스 및 추상 클래스

### 9.1 `BatchJob` 인터페이스

```java
package com.example.batch.core;

public interface BatchJob {

    /**
     * FWK_BATCH_APP.BATCH_APP_ID 와 1:1 매핑되는 배치 ID.
     * FWK_BATCH_APP.BATCH_APP_FILE_NAME 과도 일치해야 함.
     */
    String getBatchAppId();

    /**
     * 1단계: 초기화.
     * FWK_BATCH_HIS INSERT 후 프레임워크가 호출.
     * 리소스 로드, 파라미터 검증 등 수행. 비즈니스 로직 포함 불가.
     *
     * @param context 실행 컨텍스트
     * @throws BatchInitException 초기화 실패 시
     */
    void init(BatchExecutionContext context) throws BatchInitException;

    /**
     * 2단계: 비즈니스 실행.
     * init() 성공 후 프레임워크가 호출.
     * 완료 전 context.setCounters() 또는 개별 setter로 집계 카운터 설정 필수.
     *
     * @param context init()과 동일한 컨텍스트 객체
     * @throws BatchExecutionException 비즈니스 로직 실패 시
     */
    void executeBatch(BatchExecutionContext context) throws BatchExecutionException;
}
```

### 9.2 `BatchExecutionContext` — 실행 컨텍스트

```java
package com.example.batch.context;

import com.example.batch.vo.FwkBatchAppVo;

public class BatchExecutionContext {

    // 불변 — 프레임워크가 설정
    private final String            batchAppId;
    private final String            instanceId;
    private final String            baseBatchDate;          // YYYYMMDD
    private final int               batchExecuteSeq;    // 당일 실행 순번
    private final Map<String,String> parameters;        // PROPERTIES + CLI 인자 병합
    private final LocalDateTime     startTime;
    private final FwkBatchAppVo     batchAppMeta;       // FWK_BATCH_APP 전체 메타

    // 가변 — 잡이 executeBatch()에서 설정
    private long recordCount  = 0L;
    private long executeCount = 0L;
    private long successCount = 0L;
    private long failCount    = 0L;
    private Object jobData;

    // 집계 카운터 편의 메서드
    public void setCounters(long total, long success, long fail) {
        this.recordCount  = total;
        this.successCount = success;
        this.failCount    = fail;
        this.executeCount = success + fail;
    }
    public void setRecordCount(long n)  { this.recordCount  = n; }
    public void setExecuteCount(long n) { this.executeCount = n; }
    public void setSuccessCount(long n) { this.successCount = n; }
    public void setFailCount(long n)    { this.failCount    = n; }

    public void setJobData(Object data) { this.jobData = data; }
    public <T> T getJobData(Class<T> type) { return type.cast(jobData); }

    // FWK_BATCH_APP 메타 편의 접근자
    public boolean isRetryable()     { return "Y".equals(batchAppMeta.getRetryableYn()); }
    public String  getOrgId()        { return batchAppMeta.getOrgId(); }
    public String  getTrxId()        { return batchAppMeta.getTrxId(); }

    // 불변 필드 Getter
    public String getBatchAppId()    { return batchAppId; }
    public String getInstanceId()    { return instanceId; }
    public String getBaseBatchDate() { return baseBatchDate; }
    public int    getBatchExecuteSeq() { return batchExecuteSeq; }
    public Map<String,String> getParameters() { return parameters; }
    public LocalDateTime getStartTime() { return startTime; }

    // 카운터 Getter
    public long getRecordCount()  { return recordCount; }
    public long getExecuteCount() { return executeCount; }
    public long getSuccessCount() { return successCount; }
    public long getFailCount()    { return failCount; }
}
```

### 9.3 `AbstractBatchJob` — 편의 기반 클래스

```java
package com.example.batch.core;

public abstract class AbstractBatchJob implements BatchJob {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 기본값: 클래스 단순명. FWK_BATCH_APP.BATCH_APP_FILE_NAME과 일치해야 함. */
    @Override
    public String getBatchAppId() {
        return getClass().getSimpleName();
    }

    /** 초기화 로직 없으면 오버라이드 불필요 */
    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}] init() 기본 no-op", getBatchAppId());
    }

    @Override
    public abstract void executeBatch(BatchExecutionContext context)
            throws BatchExecutionException;
}
```

---

## 10. 예외 계층

```
RuntimeException
└── BatchException                    (기반, 비검사 예외)
    ├── BatchInitException            (init() 단계)
    └── BatchExecutionException       (executeBatch() 단계)
```

```java
public class BatchException extends RuntimeException {
    private final String batchAppId;
    private final String errorCode;     // FWK_BATCH_HIS.ERROR_CODE 연계

    public BatchException(String message, Throwable cause,
                          String batchAppId, String errorCode) {
        super(message, cause);
        this.batchAppId = batchAppId;
        this.errorCode  = errorCode;
    }
    public String getBatchAppId() { return batchAppId; }
    public String getErrorCode()  { return errorCode;  }
}

public class BatchInitException extends BatchException {
    public BatchInitException(String message, Throwable cause,
                              String batchAppId, String errorCode) {
        super(message, cause, batchAppId, errorCode);
    }
}

public class BatchExecutionException extends BatchException {
    public BatchExecutionException(String message, Throwable cause,
                                   String batchAppId, String errorCode) {
        super(message, cause, batchAppId, errorCode);
    }
}
```

---

## 11. `BatchJobResult` — 실행 결과

```java
package com.example.batch.core;

public class BatchJobResult {

    public enum ResRtCode { SUCCESS, FAILED_INIT, FAILED_EXEC, SKIPPED }

    private final ResRtCode resRtCode;
    private final long recordCount;
    private final long executeCount;
    private final long successCount;
    private final long failCount;
    private final Throwable error;
    private final String errorCode;
    private final String errorReason;

    public static BatchJobResult success(BatchExecutionContext ctx) {
        return new BatchJobResult(ResRtCode.SUCCESS,
            ctx.getRecordCount(), ctx.getExecuteCount(),
            ctx.getSuccessCount(), ctx.getFailCount(),
            null, null, null);
    }

    public static BatchJobResult failedInit(BatchException e) {
        return new BatchJobResult(ResRtCode.FAILED_INIT,
            0, 0, 0, 0, e, e.getErrorCode(), getRootMessage(e));
    }

    public static BatchJobResult failedExec(BatchException e) {
        return new BatchJobResult(ResRtCode.FAILED_EXEC,
            0, 0, 0, 0, e, e.getErrorCode(), getRootMessage(e));
    }

    public static BatchJobResult skipped() {
        return new BatchJobResult(ResRtCode.SKIPPED, 0, 0, 0, 0, null, null, null);
    }

    public boolean isFailed() {
        return resRtCode == ResRtCode.FAILED_INIT || resRtCode == ResRtCode.FAILED_EXEC;
    }

    private static String getRootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage();
    }
    // All-args constructor + Getter
}
```

---

## 12. DB 로깅 (`BatchLogService`)

```java
@Service
@Transactional
public class BatchLogService {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final FwkBatchHisMapper hisMapper;

    /**
     * [1] init() 호출 직전 — FWK_BATCH_HIS INSERT
     *
     * - BATCH_EXECUTE_SEQ = MAX(SEQ) + 1, 없으면 1
     * - LOG_DTIME = 현재 시각
     * - RES_RT_CODE = 'RUNNING'
     *
     * @return 당일 실행 순번 (batchExecuteSeq)
     */
    public int writeInitLog(String batchAppId, String instanceId,
                             LocalDateTime startTime) {
        String baseBatchDate = startTime.format(DateTimeFormatter.BASIC_ISO_DATE);

        Integer maxSeq = hisMapper.selectMaxExecuteSeq(batchAppId, instanceId, baseBatchDate);
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;

        FwkBatchHisVo vo = new FwkBatchHisVo();
        vo.setBatchAppId(batchAppId);
        vo.setInstanceId(instanceId);
        vo.setBaseBatchDate(baseBatchDate);
        vo.setBatchExecuteSeq(nextSeq);
        vo.setLogDtime(startTime.format(FMT));
        vo.setResRtCode("RUNNING");
        vo.setLastUpdateUserId(batchAppId);

        hisMapper.insert(vo);
        return nextSeq;
    }

    /**
     * [2] executeBatch() 완료 후 — FWK_BATCH_HIS UPDATE
     *
     * - BATCH_END_DTIME, RES_RT_CODE, 카운터, 오류 정보 업데이트
     * - 이 메서드는 BatchJobExecutor.safeWriteFinalLog()에서 호출됨
     *   → DB 오류 시 예외가 발생해도 executor가 삼킴 (fail-safe 보장)
     * - 단, 이 메서드 자체는 예외를 삼키지 않음
     *   (executor의 safeWriteFinalLog가 책임지므로 여기서는 그대로 throw)
     */
    public void writeFinalLog(String batchAppId, String instanceId,
                               String baseBatchDate, int batchExecuteSeq,
                               BatchJobResult result, LocalDateTime endTime) {
        FwkBatchHisVo vo = new FwkBatchHisVo();
        vo.setBatchAppId(batchAppId);
        vo.setInstanceId(instanceId);
        vo.setBaseBatchDate(baseBatchDate);
        vo.setBatchExecuteSeq(batchExecuteSeq);
        vo.setBatchEndDtime(endTime.format(FMT));
        vo.setResRtCode(result.getResRtCode().name());
        vo.setLastUpdateUserId(batchAppId);
        vo.setRecordCount(result.getRecordCount());
        vo.setExecuteCount(result.getExecuteCount());
        vo.setSuccessCount(result.getSuccessCount());
        vo.setFailCount(result.getFailCount());

        if (result.getErrorCode() != null) {
            vo.setErrorCode(result.getErrorCode());
            // 4000자 초과 시 truncate — Oracle VARCHAR2(4000) 컬럼 제약
            vo.setErrorReason(truncate(result.getErrorReason(), 4000));
        }
        hisMapper.update(vo);
        // DB 오류 시 예외 발생 → BatchJobExecutor.safeWriteFinalLog()가 캐치
    }
}
```

---

## 12-A. 다층 예외 처리 전략 (방어적 프레임워크 설계)

개발자가 `init()` / `executeBatch()` 내부에서 예외 처리를 누락해도
프레임워크가 4개 레이어에서 반드시 포착하여 `FWK_BATCH_HIS`에 기록합니다.

### 예외 처리 레이어 구조

```
┌───────────────────────────────────────────────────────────────┐
│  Layer 4: ThreadPoolTaskScheduler.ErrorHandler (최후 방어선)   │
│           — 프레임워크 자체 버그 등 절대 놓쳐선 안 되는 오류  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Layer 3: BatchSchedulerManager Runnable try-catch      │  │
│  │           — executor 외부로 나온 모든 Throwable 포착     │  │
│  │  ┌───────────────────────────────────────────────────┐  │  │
│  │  │  Layer 2: BatchJobExecutor (단계별 독립 처리)      │  │  │
│  │  │           — init() / executeBatch() 각각 catch     │  │  │
│  │  │           — DB 로그 오류도 catch (fail-safe)        │  │  │
│  │  │  ┌─────────────────────────────────────────────┐  │  │  │
│  │  │  │  Layer 1: BatchJob 구현체                    │  │  │  │
│  │  │  │           (개발자 작성 — 누락 가능)           │  │  │  │
│  │  │  │  init() / executeBatch() 내부 try-catch      │  │  │  │
│  │  │  └─────────────────────────────────────────────┘  │  │  │
│  │  └───────────────────────────────────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

### 각 레이어의 책임

| 레이어 | 위치 | 포착 대상 | 처리 방법 |
|---|---|---|---|
| **Layer 1** | `BatchJob` 구현체 | 비즈니스 로직 예외 | `BatchInitException` / `BatchExecutionException` 으로 래핑 (개발자 재량) |
| **Layer 2** | `BatchJobExecutor` | Layer 1 누락 포함 모든 `Throwable` | DB에 오류 기록 (`FWK_BATCH_HIS` UPDATE), `BatchJobResult` 반환 |
| **Layer 3** | `BatchSchedulerManager` Runnable | executor 외부 `Throwable` | 에러 로그, 스케줄러 스레드 보호 (다음 Cron 실행 유지) |
| **Layer 4** | `ThreadPoolTaskScheduler` ErrorHandler | Runnable 밖으로 나온 `Throwable` | 에러 로그 (마지막 방어선) |

### Layer 2 핵심 원칙

```
① init() 단계   : catch(Throwable) → DB FAILED_INIT 기록 → 단락(executeBatch 미실행)
② execute 단계  : catch(Throwable) → DB FAILED_EXEC 기록
③ DB 로깅 자체가 실패할 경우 : writeFinalLog()도 try-catch → 로그만 남기고 진행
④ 선행 배치 확인 실패 : catch(Throwable) → 안전하게 스킵 처리
⑤ 모든 catch는 Exception이 아닌 Throwable — Error 계열(OOM 등)도 포착
```

---

## 13. 핵심 오케스트레이터 (`BatchJobExecutor`)

### baseBatchDate 설계 원칙

| 실행 방식 | `BATCH_DATE` (FWK_BATCH_HIS) | `LOG_DTIME` |
|---|---|---|
| **스케줄러 자동 실행** | 오늘 날짜 (`LocalDate.now()`) | 실제 실행 시각 |
| **REST 수동 실행 (baseBatchDate 미지정)** | 오늘 날짜 | 실제 실행 시각 |
| **REST 수동 실행 (baseBatchDate=어제)** | 어제 날짜 (`20240117`) | 실제 실행 시각 |

- `BATCH_DATE` = **업무 기준일자** (배치가 처리하는 데이터 기준일)
- `LOG_DTIME` = **물리적 실행 시각** (항상 실제 현재 시각)
- `baseBatchDate`는 `BatchExecutionContext.parameters`에도 포함되어 잡 내부에서 참조 가능

`writeFinalLog`는 **어떤 경우에도 반드시 호출**되도록 설계합니다.
- `init()` 성공/실패, `executeBatch()` 성공/실패 모든 경로에서 호출
- `writeFinalLog()` 자체가 DB 오류로 실패해도 예외를 외부로 전파하지 않음 (fail-safe)
- 전체 흐름을 `try-finally` 구조로 보장

```java
@Component
public class BatchJobExecutor {

    private final BatchLogService     logService;
    private final FwkBatchHisMapper   hisMapper;
    private final WasInstanceIdentity currentInstance;

    /**
     * 스케줄러 자동 실행 — baseBatchDate = 오늘
     */
    public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta,
                                  Map<String, String> params) {
        String todayDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return execute(job, appMeta, params, todayDate);
    }

    /**
     * REST 수동 실행 — baseBatchDate 지정 가능 (어제, 특정일 등)
     *
     * @param baseBatchDate 업무 기준일자 (YYYYMMDD). FWK_BATCH_HIS.BATCH_DATE에 기록됨.
     *                  null 이면 오늘 날짜 사용.
     */
    public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta,
                                  Map<String, String> params, String baseBatchDate) {

        LocalDateTime startTime = LocalDateTime.now();
        // baseBatchDate가 null이거나 빈 값이면 오늘 날짜로 폴백
        if (baseBatchDate == null || baseBatchDate.isBlank()) {
            baseBatchDate = startTime.format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        String batchAppId       = appMeta.getBatchAppId();
        String instanceId       = currentInstance.getInstanceId();

        // ─── 0. 선행 배치 완료 확인 (DB 오류 시 안전하게 스킵) ───
        try {
            if (hasUncompletedPreBatch(appMeta, baseBatchDate)) {
                log.warn("[{}] 선행 배치 [{}] 당일 미완료 — 스킵",
                         batchAppId, appMeta.getPreBatchAppId());
                return BatchJobResult.skipped();
            }
        } catch (Throwable t) {
            log.error("[{}] 선행 배치 확인 중 오류 — 안전을 위해 스킵 처리: {}",
                      batchAppId, t.getMessage(), t);
            return BatchJobResult.skipped();
        }

        // ─── 1. FWK_BATCH_HIS INSERT (LOG_DTIME 기록) ───
        // writeInitLog 실패 시 이후 실행 불가 → 예외를 외부로 전파
        int batchExecuteSeq;
        try {
            batchExecuteSeq = logService.writeInitLog(batchAppId, instanceId, startTime);
        } catch (Throwable t) {
            log.error("[{}] FWK_BATCH_HIS INSERT 실패 — 배치 실행 불가: {}",
                      batchAppId, t.getMessage(), t);
            throw new BatchException("이력 INSERT 실패로 배치 실행 불가", t,
                                      batchAppId, "LOG_INSERT_FAIL");
        }

        // ─── 2~4: init → executeBatch → writeFinalLog ───
        // writeFinalLog는 finally 블록으로 누락 방지
        BatchJobResult result = null;
        final String finalBaseBatchDate = baseBatchDate;   // effectively final for lambda

        try {
            // baseBatchDate를 파라미터 맵에 포함 → 잡 내부에서 context.getParameters().get("baseBatchDate")로 접근
            Map<String, String> mergedParams = mergeParams(appMeta.getProperties(), params);
            mergedParams.putIfAbsent("baseBatchDate", finalBaseBatchDate);  // CLI/DB에서 오지 않은 경우 기본값

            BatchExecutionContext context = new BatchExecutionContext(
                batchAppId, instanceId, finalBaseBatchDate, batchExecuteSeq,
                mergedParams, startTime, appMeta
            );

            // ─── 2. init() — 독립 예외 처리 ───
            try {
                job.init(context);
            } catch (Throwable t) {
                BatchInitException wrapped = wrapInitException(t, batchAppId);
                log.error("[{}] init() 실패: [{}] {}", batchAppId,
                          wrapped.getErrorCode(), wrapped.getMessage(), wrapped);
                result = BatchJobResult.failedInit(wrapped);
                return result;   // finally 에서 writeFinalLog 호출됨
            }

            // ─── 3. executeBatch() — 독립 예외 처리 ───
            try {
                job.executeBatch(context);
                result = BatchJobResult.success(context);
            } catch (Throwable t) {
                BatchExecutionException wrapped = wrapExecException(t, batchAppId);
                log.error("[{}] executeBatch() 실패: [{}] {}", batchAppId,
                          wrapped.getErrorCode(), wrapped.getMessage(), wrapped);
                result = BatchJobResult.failedExec(wrapped);
            }

            return result;

        } finally {
            // ─── 4. writeFinalLog — 절대 누락 불가 (finally 보장) ───
            // result == null 은 context 생성 단계에서 예외 발생한 경우
            if (result == null) {
                result = BatchJobResult.failedExec(
                    new BatchExecutionException("컨텍스트 생성 실패 (예상치 못한 오류)",
                                               null, batchAppId, "CONTEXT_ERROR"));
            }
            safeWriteFinalLog(batchAppId, instanceId, baseBatchDate,
                              batchExecuteSeq, result);
        }
    }

    /**
     * writeFinalLog를 fail-safe로 실행.
     * DB 오류가 발생해도 예외를 외부로 전파하지 않음.
     * 로그만 남기고 배치 결과(BatchJobResult) 는 그대로 반환.
     */
    private void safeWriteFinalLog(String batchAppId, String instanceId,
                                    String baseBatchDate, int seq,
                                    BatchJobResult result) {
        try {
            logService.writeFinalLog(batchAppId, instanceId, baseBatchDate,
                                     seq, result, LocalDateTime.now());
        } catch (Throwable t) {
            // DB 오류로 이력 UPDATE 실패 — 배치 결과 자체는 유지
            log.error("[{}] FWK_BATCH_HIS UPDATE 실패 (결과={}) — 이력 기록 누락: {}",
                      batchAppId, result.getResRtCode(), t.getMessage(), t);
        }
    }

    /** Throwable → BatchInitException 래핑 (이미 래핑된 경우 재사용) */
    private BatchInitException wrapInitException(Throwable t, String batchAppId) {
        if (t instanceof BatchInitException bie) return bie;
        return new BatchInitException(
            "init() 미처리 예외: " + t.getClass().getSimpleName() + " - " + t.getMessage(),
            t, batchAppId, "INIT_UNHANDLED");
    }

    /** Throwable → BatchExecutionException 래핑 */
    private BatchExecutionException wrapExecException(Throwable t, String batchAppId) {
        if (t instanceof BatchExecutionException bee) return bee;
        return new BatchExecutionException(
            "executeBatch() 미처리 예외: " + t.getClass().getSimpleName() + " - " + t.getMessage(),
            t, batchAppId, "EXEC_UNHANDLED");
    }

    /** PRE_BATCH_APP_ID 있고 당일 SUCCESS 이력 없으면 true */
    private boolean hasUncompletedPreBatch(FwkBatchAppVo appMeta, String baseBatchDate) {
        String preId = appMeta.getPreBatchAppId();
        if (preId == null || preId.isBlank()) return false;
        return !hisMapper.existsSuccessToday(preId, baseBatchDate);
    }

    /** FWK_BATCH_APP.PROPERTIES(JSON) + 파라미터 병합, CLI 우선 */
    private Map<String, String> mergeParams(String dbProperties,
                                             Map<String, String> cliParams) {
        try {
            Map<String, String> merged = new LinkedHashMap<>(parseProperties(dbProperties));
            merged.putAll(cliParams);
            return merged;
        } catch (Throwable t) {
            log.warn("PROPERTIES 파싱 실패 — CLI 파라미터만 사용: {}", t.getMessage());
            return new LinkedHashMap<>(cliParams);
        }
    }
}
```

### `writeFinalLog` 누락 방지 흐름

```
execute() 진입
    │
    ├─ 0. 선행 배치 확인  →  예외 시: 스킵 반환 (finally 미진입)
    │
    ├─ 1. writeInitLog()  →  실패 시: 예외 전파 (finally 미진입, HIS 행 없음)
    │
    └─ try {
    │       context 생성
    │       try { init() }      catch(Throwable) → result = FAILED_INIT; return
    │       try { executeBatch() } catch(Throwable) → result = FAILED_EXEC
    │       result = SUCCESS
    │       return result
    │  } finally {
    │       // ★ 무조건 실행 — return 후에도, 예외 후에도
    │       result == null 이면 → CONTEXT_ERROR 로 설정
    │       safeWriteFinalLog()   // 내부 DB 오류도 삼킴
    │  }
```

---

## 13-A. 스케줄러 등록 (핵심 신규)

### JOIN 쿼리 VO: `BatchScheduleVo`

```java
package com.example.batch.vo;

/**
 * FWK_WAS_EXEC_BATCH JOIN FWK_BATCH_APP 결과 VO.
 * 기동 시 현재 WAS가 수행할 배치 목록 + Cron 표현식 로드에 사용.
 */
public class BatchScheduleVo {
    // FWK_BATCH_APP
    private String batchAppId;
    private String batchAppName;
    private String batchAppFileName;   // Spring Bean 클래스명과 매핑
    private String batchCycle;         // D|W|M|H|O
    private String cronText;           // Cron 표현식 (null 이면 스케줄 등록 불가)
    private String retryableYn;
    private String preBatchAppId;
    private String properties;         // 기본 파라미터 JSON
    private String importantType;
    private String orgId;
    private String trxId;
    // FWK_WAS_EXEC_BATCH
    private String instanceId;
    private String useYn;
    // Getter / Setter
}
```

### JOIN 쿼리 Mapper: `BatchScheduleMapper`

```java
package com.example.batch.mapper;

@Mapper
public interface BatchScheduleMapper {

    /**
     * 현재 WAS 인스턴스에서 실행할 배치 목록 조회
     * FWK_WAS_EXEC_BATCH JOIN FWK_BATCH_APP
     * 조건: INSTANCE_ID = #{instanceId}, USE_YN = 'Y'
     */
    List<BatchScheduleVo> selectSchedulesForInstance(
            @Param("instanceId") String instanceId);
}
```

### `BatchScheduleMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.batch.mapper.BatchScheduleMapper">

    <resultMap id="BatchScheduleResultMap"
               type="com.example.batch.vo.BatchScheduleVo">
        <result property="batchAppId"       column="BATCH_APP_ID"/>
        <result property="batchAppName"     column="BATCH_APP_NAME"/>
        <result property="batchAppFileName" column="BATCH_APP_FILE_NAME"/>
        <result property="batchCycle"       column="BATCH_CYCLE"/>
        <result property="cronText"         column="CRON_TEXT"/>
        <result property="retryableYn"      column="RETRYABLE_YN"/>
        <result property="preBatchAppId"    column="PRE_BATCH_APP_ID"/>
        <result property="properties"       column="PROPERTIES"/>
        <result property="importantType"    column="IMPORTANT_TYPE"/>
        <result property="orgId"            column="ORG_ID"/>
        <result property="trxId"            column="TRX_ID"/>
        <result property="instanceId"       column="INSTANCE_ID"/>
        <result property="useYn"            column="USE_YN"/>
    </resultMap>

    <!--
        현재 WAS에서 실행할 배치 목록 조회
        FWK_WAS_EXEC_BATCH (매핑 테이블) JOIN FWK_BATCH_APP (정의 마스터)
    -->
    <select id="selectSchedulesForInstance" resultMap="BatchScheduleResultMap">
        SELECT
            A.BATCH_APP_ID,
            A.BATCH_APP_NAME,
            A.BATCH_APP_FILE_NAME,
            A.BATCH_CYCLE,
            A.CRON_TEXT,
            A.RETRYABLE_YN,
            A.PRE_BATCH_APP_ID,
            A.PROPERTIES,
            A.IMPORTANT_TYPE,
            A.ORG_ID,
            A.TRX_ID,
            W.INSTANCE_ID,
            W.USE_YN
          FROM D_SPIDERLINK.FWK_WAS_EXEC_BATCH W
          JOIN D_SPIDERLINK.FWK_BATCH_APP A
            ON W.BATCH_APP_ID = A.BATCH_APP_ID
         WHERE W.INSTANCE_ID = #{instanceId}
           AND W.USE_YN      = 'Y'
         ORDER BY A.BATCH_APP_ID
    </select>

</mapper>
```

### `BatchSchedulerConfig` — ThreadPoolTaskScheduler 빈

```java
@Configuration
@EnableScheduling
public class BatchSchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler batchTaskScheduler(BatchProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix("batch-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);   // 실행 중 배치 완료 대기
        scheduler.setAwaitTerminationSeconds(300);             // 최대 5분 대기

        // ─── Layer 4: 최후 방어선 ErrorHandler ───
        // Layer 3(Runnable catch)까지 벗어난 Throwable만 도달
        // 여기서 예외를 삼켜야 스케줄러 스레드 풀이 유지됨
        // (삼키지 않으면 해당 스레드가 종료되어 이후 Cron 실행 불가)
        scheduler.setErrorHandler(t -> {
            Logger log = LoggerFactory.getLogger(BatchSchedulerConfig.class);
            log.error("[Layer4] 스케줄러 ErrorHandler 도달 — 심각한 프레임워크 오류. " +
                      "해당 배치의 FWK_BATCH_HIS 상태를 수동 확인 필요: {}",
                      t.getMessage(), t);
            // TODO: 슬랙/이메일 등 운영 알림 연동 권장
        });

        scheduler.initialize();
        return scheduler;
    }
}
```

### `BatchSchedulerManager` — 기동 시 Cron 등록

```java
package com.example.batch.scheduler;

@Component
public class BatchSchedulerManager implements ApplicationRunner {

    private final BatchScheduleMapper     scheduleMapper;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final BatchJobExecutor        executor;
    private final WasInstanceIdentity     currentInstance;
    private final List<BatchJob>          allJobs;

    // 등록된 스케줄 관리 (향후 동적 갱신 대비)
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * Spring ApplicationContext 완전 초기화 후 실행 (ApplicationRunner).
     * 모든 Spring 빈이 준비된 상태에서 스케줄 등록.
     */
    @Override
    public void run(ApplicationArguments args) {
        String instanceId = currentInstance.getInstanceId();

        // 1. FWK_WAS_EXEC_BATCH JOIN FWK_BATCH_APP — 현재 WAS 담당 배치 로드
        List<BatchScheduleVo> schedules =
            scheduleMapper.selectSchedulesForInstance(instanceId);

        log.info("[{}] 로드된 배치 수: {}건", instanceId, schedules.size());

        // 2. BatchJob 빈 맵 구성 (BATCH_APP_FILE_NAME → BatchJob 빈)
        Map<String, BatchJob> jobMap = allJobs.stream()
            .collect(Collectors.toMap(
                j -> j.getClass().getSimpleName(),
                j -> j,
                (a, b) -> a
            ));

        // 3. 각 배치에 대해 Cron 스케줄 등록
        for (BatchScheduleVo schedule : schedules) {
            registerSchedule(schedule, jobMap);
        }

        log.info("[{}] 스케줄 등록 완료: {}건 등록",
                 instanceId, scheduledTasks.size());
    }

    private void registerSchedule(BatchScheduleVo schedule,
                                   Map<String, BatchJob> jobMap) {
        String batchAppId = schedule.getBatchAppId();

        // CRON_TEXT 없으면 스케줄 등록 불가 → 경고만 남기고 스킵
        if (schedule.getCronText() == null || schedule.getCronText().isBlank()) {
            log.warn("[{}] CRON_TEXT 미설정 — 스케줄 등록 스킵 (수동 실행만 가능)",
                     batchAppId);
            return;
        }

        // BATCH_APP_FILE_NAME으로 BatchJob 빈 매핑
        BatchJob job = jobMap.get(schedule.getBatchAppFileName());
        if (job == null) {
            log.warn("[{}] BATCH_APP_FILE_NAME={} 매핑 빈 없음 — 스케줄 등록 스킵",
                     batchAppId, schedule.getBatchAppFileName());
            return;
        }

        // Cron 표현식 유효성 검사
        CronTrigger cronTrigger;
        try {
            cronTrigger = new CronTrigger(schedule.getCronText());
        } catch (IllegalArgumentException e) {
            log.error("[{}] CRON_TEXT=[{}] 형식 오류 — 스케줄 등록 스킵: {}",
                      batchAppId, schedule.getCronText(), e.getMessage());
            return;
        }

        // TaskScheduler에 Cron 등록
        ScheduledFuture<?> future = taskScheduler.schedule(
            createBatchTask(job, schedule),
            cronTrigger
        );

        scheduledTasks.put(batchAppId, future);
        log.info("[{}] 스케줄 등록 완료: cron=[{}]", batchAppId, schedule.getCronText());
    }

    /**
     * 실제 배치 실행 Runnable 생성 (Layer 3 방어선).
     *
     * 설계 원칙:
     *  - executor.execute() 내부(Layer 2)에서 모든 예외를 DB에 기록하고 result 반환
     *  - 여기서 catch 하는 Throwable은 프레임워크 자체 버그 또는 OOM 등 심각한 오류
     *  - 어떤 경우에도 스케줄러 스레드가 죽지 않도록 보장
     *    → 다음 Cron 트리거 때 정상 실행 유지
     */
    private Runnable createBatchTask(BatchJob job, BatchScheduleVo schedule) {
        return () -> {
            String batchAppId = schedule.getBatchAppId();
            log.info("▶ [{}] Cron 트리거 발동: cron=[{}]", batchAppId, schedule.getCronText());
            try {
                FwkBatchAppVo appMeta = toAppVo(schedule);
                BatchJobResult result = executor.execute(job, appMeta,
                                                         Collections.emptyMap());
                log.info("◀ [{}] 완료: resRtCode={}, success={}, fail={}",
                         batchAppId, result.getResRtCode(),
                         result.getSuccessCount(), result.getFailCount());

            } catch (Throwable t) {
                // Layer 2(BatchJobExecutor)를 벗어난 예외 — 프레임워크 이상 상황
                // 스케줄러 스레드를 보호하기 위해 예외를 삼키고 로그만 남김
                log.error("[{}] [Layer3] executor 외부 Throwable 포착 " +
                          "(스케줄러 스레드 보호, 다음 실행은 정상 진행): {}",
                          batchAppId, t.getMessage(), t);
                // 이 시점에는 FWK_BATCH_HIS가 이미 RUNNING 상태일 수 있음
                // 운영자가 수동으로 상태를 정리해야 하므로 별도 알림(슬랙/이메일 등) 권장
            }
        };
    }

    /** BatchScheduleVo → FwkBatchAppVo 변환 */
    private FwkBatchAppVo toAppVo(BatchScheduleVo s) {
        FwkBatchAppVo vo = new FwkBatchAppVo();
        vo.setBatchAppId(s.getBatchAppId());
        vo.setBatchAppFileName(s.getBatchAppFileName());
        vo.setPreBatchAppId(s.getPreBatchAppId());
        vo.setRetryableYn(s.getRetryableYn());
        vo.setProperties(s.getProperties());
        vo.setImportantType(s.getImportantType());
        vo.setOrgId(s.getOrgId());
        vo.setTrxId(s.getTrxId());
        return vo;
    }
}
```

---

## 13-B. REST 수동 실행 API

배치 실패 시 REST API를 통해 수동으로 재실행합니다.
`baseBatchDate`를 지정하면 어제 날짜 등 업무 기준일자를 바꿔서 실행할 수 있습니다.
이미 정상 실행 이력(`SUCCESS`)이 있는 경우, `forceRerun=true`로 해당 이력을 `CANCELED`로
업데이트한 뒤 재실행할 수 있습니다.

### REST API 명세

| 항목 | 내용 |
|---|---|
| **Method** | `POST` |
| **URL** | `/api/batch/{batchAppId}/run` |
| **인증** | Basic Auth 또는 API Key (Spring Security) |
| **요청 Body** | `BatchRunRequest` (JSON) |
| **응답 Body** | `BatchRunResponse` (JSON) |

```
# 일반 수동 실행
POST /api/batch/JOB_DAILY_RPT/run
{
  "baseBatchDate": "20240117",
  "params": { "report_type": "FULL" }
}

# 이미 SUCCESS 이력이 있는 경우 강제 재실행
POST /api/batch/JOB_DAILY_RPT/run
{
  "baseBatchDate": "20240117",
  "forceRerun": true,
  "params": { "report_type": "FULL" }
}
```

### `FWK_BATCH_HIS.RES_RT_CODE` 값 정의

| 값 | 의미 | 발생 시점 |
|---|---|---|
| `RUNNING` | 실행 중 | `writeInitLog()` INSERT 시 |
| `SUCCESS` | 정상 완료 | `writeFinalLog()` UPDATE 시 |
| `FAILED_INIT` | `init()` 단계 실패 | `writeFinalLog()` UPDATE 시 |
| `FAILED_EXEC` | `executeBatch()` 단계 실패 | `writeFinalLog()` UPDATE 시 |
| `CANCELED` | **강제 재실행으로 무효화된 이전 이력** | `cancelPreviousSuccess()` UPDATE 시 |
| `SKIPPED` | 선행 배치 미완료로 스킵 | `writeInitLog()` INSERT 시 |

### `BatchRunRequest` — 요청 DTO

```java
package com.example.batch.web;

public class BatchRunRequest {

    /**
     * 업무 기준일자 (YYYYMMDD).
     * null 또는 빈 값이면 오늘 날짜 사용.
     * FWK_BATCH_HIS.BATCH_DATE에 기록됨.
     */
    @Pattern(regexp = "^\\d{8}$", message = "baseBatchDate 형식: YYYYMMDD")
    private String baseBatchDate;

    /**
     * 강제 재실행 여부.
     * true: baseBatchDate의 기존 SUCCESS 이력을 CANCELED 처리 후 재실행.
     *       선행 배치(PRE_BATCH_APP_ID) 완료 체크도 건너뜀.
     * false(기본값): SUCCESS 이력 있으면 409 Conflict 반환.
     */
    private boolean forceRerun = false;

    /**
     * 추가 실행 파라미터 (선택).
     * FWK_BATCH_APP.PROPERTIES의 기본값을 오버라이드함.
     */
    private Map<String, String> params = new HashMap<>();

    // Getter / Setter
}
```

### `BatchRunResponse` — 응답 DTO

```java
package com.example.batch.web;

public class BatchRunResponse {

    private String  batchAppId;
    private String  instanceId;
    private String  baseBatchDate;        // 실제 사용된 업무 기준일자
    private int     batchExecuteSeq;      // FWK_BATCH_HIS.BATCH_EXECUTE_SEQ
    private String  resRtCode;            // SUCCESS | FAILED_INIT | FAILED_EXEC | SKIPPED
    private boolean forceRerun;           // 강제 재실행 여부
    private int     canceledCount;        // CANCELED 처리된 이전 이력 건수
    private long    recordCount;
    private long    successCount;
    private long    failCount;
    private String  errorCode;
    private String  errorReason;
    private String  startTime;            // 실제 실행 시작 시각
    private String  endTime;              // 실제 실행 종료 시각

    // Getter / Setter / 정적 팩토리
}
```

### `FwkBatchHisMapper` — 강제 재실행용 메서드 추가

```java
@Mapper
public interface FwkBatchHisMapper {

    // ... 기존 메서드 ...

    /**
     * 강제 재실행: 특정 baseBatchDate의 SUCCESS 이력을 CANCELED 로 업데이트.
     * 어떤 WAS 인스턴스에서 실행된 이력이든 모두 대상.
     *
     * @return 업데이트된 행 수 (0이면 취소할 SUCCESS 이력 없음)
     */
    int cancelPreviousSuccess(@Param("batchAppId")      String batchAppId,
                              @Param("baseBatchDate")   String baseBatchDate,
                              @Param("canceledBy")      String canceledBy);

    /**
     * 특정 baseBatchDate에 RUNNING 상태 이력 존재 여부.
     * 이미 실행 중인 배치를 중복 실행 방지에 사용.
     */
    boolean existsRunning(@Param("batchAppId")    String batchAppId,
                          @Param("baseBatchDate") String baseBatchDate);

    /**
     * 특정 baseBatchDate에 SUCCESS 상태 이력 존재 여부.
     * forceRerun=false 시 중복 실행 방지에 사용.
     */
    boolean existsSuccess(@Param("batchAppId")    String batchAppId,
                          @Param("baseBatchDate") String baseBatchDate);
}
```

### `FwkBatchHisMapper.xml` — 추가 SQL

```xml
<!-- 강제 재실행: SUCCESS → CANCELED 업데이트 -->
<update id="cancelPreviousSuccess">
    UPDATE D_SPIDERLINK.FWK_BATCH_HIS
       SET RES_RT_CODE         = 'CANCELED',
           BATCH_END_DTIME     = TO_CHAR(SYSTIMESTAMP, 'YYYYMMDDHH24MISSFF3'),
           LAST_UPDATE_USER_ID = #{canceledBy}
     WHERE BATCH_APP_ID = #{batchAppId}
       AND BATCH_DATE   = #{baseBatchDate}
       AND RES_RT_CODE  = 'SUCCESS'
</update>

<!-- RUNNING 이력 존재 여부 (중복 실행 방지) -->
<select id="existsRunning" resultType="boolean">
    SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
      FROM D_SPIDERLINK.FWK_BATCH_HIS
     WHERE BATCH_APP_ID = #{batchAppId}
       AND BATCH_DATE   = #{baseBatchDate}
       AND RES_RT_CODE  = 'RUNNING'
</select>

<!-- SUCCESS 이력 존재 여부 -->
<select id="existsSuccess" resultType="boolean">
    SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
      FROM D_SPIDERLINK.FWK_BATCH_HIS
     WHERE BATCH_APP_ID = #{batchAppId}
       AND BATCH_DATE   = #{baseBatchDate}
       AND RES_RT_CODE  = 'SUCCESS'
</select>
```

### `BatchJobExecutor` — `skipPreBatchCheck` 파라미터 추가

강제 재실행 시 선행 배치 완료 체크를 건너뛸 수 있도록 오버로드를 추가합니다.

```java
// 스케줄러 자동 실행 (선행 배치 체크 O)
public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta,
                               Map<String, String> params) { ... }

// REST 수동 실행 (baseBatchDate 지정, 선행 배치 체크 O)
public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta,
                               Map<String, String> params, String baseBatchDate) { ... }

// REST 강제 재실행 (baseBatchDate 지정, 선행 배치 체크 건너뜀)
public BatchJobResult execute(BatchJob job, FwkBatchAppVo appMeta,
                               Map<String, String> params, String baseBatchDate,
                               boolean skipPreBatchCheck) {
    // skipPreBatchCheck=true 이면 0단계(선행 배치 확인) 생략
    ...
}
```

### `BatchManualController` — 강제 재실행 포함 전체 컨트롤러

```java
package com.example.batch.web;

@RestController
@RequestMapping("/api/batch")
public class BatchManualController {

    private final BatchScheduleMapper   scheduleMapper;
    private final FwkBatchHisMapper     hisMapper;
    private final BatchJobExecutor      executor;
    private final WasInstanceIdentity   currentInstance;
    private final List<BatchJob>        allJobs;

    /**
     * 배치 수동 실행 / 강제 재실행.
     *
     * [일반 실행]  forceRerun=false (기본값)
     *   - RUNNING 이력 있음 → 409 Conflict (중복 실행 방지)
     *   - SUCCESS 이력 있음 → 409 Conflict (이미 완료)
     *   - 위 이력 없음     → 정상 실행
     *
     * [강제 재실행] forceRerun=true
     *   - RUNNING 이력 있음 → 409 Conflict (실행 중 재실행 불가)
     *   - SUCCESS 이력 있음 → CANCELED 처리 후 재실행 (선행 배치 체크 건너뜀)
     *   - 위 이력 없음     → 정상 실행 (선행 배치 체크 건너뜀)
     */
    @PostMapping("/{batchAppId}/run")
    public ResponseEntity<BatchRunResponse> runBatch(
            @PathVariable String batchAppId,
            @RequestBody @Valid BatchRunRequest request) {

        String instanceId    = currentInstance.getInstanceId();
        String baseBatchDate = resolveBaseBatchDate(request.getBaseBatchDate());

        // ─── 1. 현재 WAS 권한 확인 ───
        BatchScheduleVo schedule = scheduleMapper
            .selectSchedulesForInstance(instanceId).stream()
            .filter(s -> s.getBatchAppId().equals(batchAppId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "배치 [" + batchAppId + "] 는 WAS [" + instanceId + "] 실행 권한 없음"));

        // ─── 2. BatchJob 빈 조회 ───
        BatchJob job = findJob(schedule.getBatchAppFileName())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "BATCH_APP_FILE_NAME=[" + schedule.getBatchAppFileName() + "] 매핑 빈 없음"));

        // ─── 3. RUNNING 중복 실행 방지 (forceRerun 무관하게 항상 차단) ───
        if (hisMapper.existsRunning(batchAppId, baseBatchDate)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "배치 [" + batchAppId + "] baseBatchDate=[" + baseBatchDate
                + "] 가 현재 RUNNING 중 — 중복 실행 불가");
        }

        // ─── 4. SUCCESS 이력 존재 시 처리 ───
        int canceledCount = 0;
        if (hisMapper.existsSuccess(batchAppId, baseBatchDate)) {
            if (!request.isForceRerun()) {
                // forceRerun=false → 이미 성공한 배치는 재실행 불가
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "배치 [" + batchAppId + "] baseBatchDate=[" + baseBatchDate
                    + "] 의 SUCCESS 이력 존재. 강제 재실행은 forceRerun=true 로 요청하세요.");
            }
            // forceRerun=true → 기존 SUCCESS 이력을 CANCELED 처리
            canceledCount = hisMapper.cancelPreviousSuccess(
                batchAppId, baseBatchDate, instanceId);
            log.info("[{}] 강제 재실행: baseBatchDate=[{}] 의 {} 건 이력 CANCELED 처리",
                     batchAppId, baseBatchDate, canceledCount);
        }

        // ─── 5. 실행 ───
        FwkBatchAppVo appMeta = toAppVo(schedule);
        // forceRerun=true 이면 선행 배치 체크 건너뜀
        BatchJobResult result = executor.execute(
            job, appMeta, request.getParams(),
            baseBatchDate, request.isForceRerun()   // skipPreBatchCheck
        );

        // ─── 6. 응답 구성 ───
        BatchRunResponse response = new BatchRunResponse();
        response.setBatchAppId(batchAppId);
        response.setInstanceId(instanceId);
        response.setBaseBatchDate(baseBatchDate);
        response.setBatchExecuteSeq(result.getBatchExecuteSeq());
        response.setResRtCode(result.getResRtCode().name());
        response.setForceRerun(request.isForceRerun());
        response.setCanceledCount(canceledCount);
        response.setRecordCount(result.getRecordCount());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailCount(result.getFailCount());
        response.setErrorCode(result.getErrorCode());
        response.setErrorReason(result.getErrorReason());

        HttpStatus httpStatus = result.isFailed()
            ? HttpStatus.INTERNAL_SERVER_ERROR
            : HttpStatus.OK;

        return ResponseEntity.status(httpStatus).body(response);
    }

    /** baseBatchDate가 null/공백이면 오늘 날짜 반환 */
    private String resolveBaseBatchDate(String baseBatchDate) {
        if (baseBatchDate == null || baseBatchDate.isBlank()) {
            return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        return baseBatchDate;
    }

    /** 현재 WAS 담당 배치 목록 조회 (모니터링용) */
    @GetMapping("/list")
    public ResponseEntity<List<BatchScheduleVo>> listSchedules() {
        return ResponseEntity.ok(
            scheduleMapper.selectSchedulesForInstance(currentInstance.getInstanceId()));
    }

    private Optional<BatchJob> findJob(String batchAppFileName) {
        return allJobs.stream()
            .filter(j -> j.getClass().getSimpleName().equals(batchAppFileName))
            .findFirst();
    }
}
```

### Spring Security 설정 (Basic Auth 예시)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/batch/**").hasRole("BATCH_ADMIN")
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

### 수동 실행 / 강제 재실행 흐름

```
POST /api/batch/JOB_DAILY_RPT/run
Body: { "baseBatchDate": "20240117", "forceRerun": true }
│
├─ [1] FWK_WAS_EXEC_BATCH 권한 확인
│       → 없으면 403 Forbidden
│
├─ [2] BatchJob 빈 조회
│       → 없으면 404 Not Found
│
├─ [3] RUNNING 이력 존재 여부 확인 (항상)
│       → RUNNING 있으면 409 Conflict (실행 중 중복 방지)
│
├─ [4] SUCCESS 이력 존재 여부 확인
│   │
│   ├─ forceRerun=false + SUCCESS 있음
│   │       → 409 Conflict ("이미 성공, forceRerun=true 로 재요청")
│   │
│   └─ forceRerun=true + SUCCESS 있음
│           UPDATE FWK_BATCH_HIS
│             SET RES_RT_CODE = 'CANCELED'
│           WHERE BATCH_APP_ID = 'JOB_DAILY_RPT'
│             AND BATCH_DATE   = '20240117'
│             AND RES_RT_CODE  = 'SUCCESS'
│           → canceledCount = 1 (응답에 포함)
│
├─ [5] BatchJobExecutor.execute(job, appMeta, params,
│                               baseBatchDate="20240117",
│                               skipPreBatchCheck=true)  ← forceRerun=true 시
│       ├─ writeInitLog() → INSERT FWK_BATCH_HIS (BATCH_DATE='20240117', SEQ=2)
│       ├─ job.init()
│       ├─ job.executeBatch()
│       └─ writeFinalLog() → UPDATE RES_RT_CODE='SUCCESS'
│
└─ [6] 응답
    성공: 200 { resRtCode:"SUCCESS", canceledCount:1, forceRerun:true, successCount:100 }
    실패: 500 { resRtCode:"FAILED_EXEC", errorCode:"...", errorReason:"..." }
```

### HTTP 상태 코드 정리

| 상황 | HTTP 상태 | 사유 |
|---|---|---|
| 정상 실행 성공 | `200 OK` | |
| 강제 재실행 성공 | `200 OK` | `canceledCount > 0` |
| 배치 실행 실패 | `500` | `FAILED_INIT` / `FAILED_EXEC` |
| WAS 권한 없음 | `403 Forbidden` | FWK_WAS_EXEC_BATCH 미등록 |
| BatchJob 빈 없음 | `404 Not Found` | BATCH_APP_FILE_NAME 불일치 |
| RUNNING 중 | `409 Conflict` | 중복 실행 방지 |
| SUCCESS 이력 있음 (`forceRerun=false`) | `409 Conflict` | forceRerun=true 안내 |

---

## 14. 배치 러너 (`BatchJobCommandLineRunner`)

```java
@Component
public class BatchJobCommandLineRunner implements CommandLineRunner {

    private final List<BatchJob>          allJobs;
    private final FwkBatchAppMapper       appMapper;
    private final FwkWasExecBatchMapper   wasExecMapper;
    private final BatchJobExecutor        executor;
    private final WasInstanceIdentity     currentInstance;
    private final BatchProperties         properties;

    @Override
    public void run(String... args) throws Exception {
        Map<String, String> cliParams = parseArgs(args);
        String targetAppId = cliParams.remove("batchAppId");

        // BATCH_APP_FILE_NAME(=클래스 단순명) → BatchJob 빈 맵
        Map<String, BatchJob> jobMap = allJobs.stream()
            .collect(Collectors.toMap(
                j -> j.getClass().getSimpleName(),
                j -> j,
                (a, b) -> a
            ));

        // FWK_WAS_EXEC_BATCH에서 현재 WAS의 실행 가능 배치 ID 조회
        List<String> eligibleIds = wasExecMapper.selectEligibleBatchAppIds(
            currentInstance.getInstanceId()
        );

        log.info("[{}] 실행 가능 배치: {}", currentInstance.getInstanceId(), eligibleIds);

        for (String batchAppId : eligibleIds) {

            if (targetAppId != null && !targetAppId.equals(batchAppId)) continue;

            // FWK_BATCH_APP 메타데이터 조회
            FwkBatchAppVo appMeta = appMapper.selectById(batchAppId);
            if (appMeta == null) {
                log.warn("[{}] FWK_BATCH_APP 정의 없음. 스킵.", batchAppId);
                continue;
            }

            // BATCH_APP_FILE_NAME으로 BatchJob 빈 매핑
            BatchJob job = jobMap.get(appMeta.getBatchAppFileName());
            if (job == null) {
                log.warn("[{}] BATCH_APP_FILE_NAME={} 매핑 빈 없음. 스킵.",
                         batchAppId, appMeta.getBatchAppFileName());
                continue;
            }

            log.info("▶ [{}] 배치 시작 (instanceId={})",
                     batchAppId, currentInstance.getInstanceId());

            BatchJobResult result = executor.execute(job, appMeta, cliParams);

            log.info("◀ [{}] 완료: resRtCode={}, success={}, fail={}",
                     batchAppId, result.getResRtCode(),
                     result.getSuccessCount(), result.getFailCount());

            if (properties.isFailFast() && result.isFailed()) {
                log.error("failFast=true, 이후 배치 실행 중단");
                break;
            }
        }
    }

    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                String[] parts = arg.substring(2).split("=", 2);
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }
}
```

---

## 15. 전체 실행 시퀀스 다이어그램

```
[배치 앱 기동]
  WasInstanceConfiguration
      → System.getProperty("batch.instance.id") 읽기   ← JVM -D 옵션
      → 없으면 System.getenv("BATCH_INSTANCE_ID") 폴백
      → WasInstanceIdentity 빈 생성 (최대 4자 유효성 검사)

  BatchSchedulerConfig
      → ThreadPoolTaskScheduler 빈 초기화 (pool-size 설정)


[BatchSchedulerManager.run() — ApplicationRunner, 기동 직후]
  │
  ├─ BatchScheduleMapper.selectSchedulesForInstance('WAS1')
  │     SELECT A.*, W.INSTANCE_ID, W.USE_YN
  │       FROM FWK_WAS_EXEC_BATCH W
  │       JOIN FWK_BATCH_APP A ON W.BATCH_APP_ID = A.BATCH_APP_ID
  │      WHERE W.INSTANCE_ID = 'WAS1' AND W.USE_YN = 'Y'
  │     → [BatchScheduleVo(JOB_DAILY_RPT, cron="0 0 1 * * ?"), ...]
  │
  ├─ jobMap 구성 (BATCH_APP_FILE_NAME → BatchJob 빈)
  │
  └─ (배치별 반복)
      ├─ CRON_TEXT 없음 → 경고 로그, 스킵 (수동 실행만 가능)
      ├─ BatchJob 빈 없음 → 경고 로그, 스킵
      ├─ CronTrigger 생성 (형식 오류 시 경고 로그, 스킵)
      └─ TaskScheduler.schedule(batchTask, cronTrigger)  ← 등록 완료
             batchTask = () → BatchJobExecutor.execute(job, appMeta, {})


[Cron 트리거 발동 시 — 스케줄된 시각마다]
  BatchSchedulerManager.createBatchTask Runnable 실행
  └─ BatchJobExecutor.execute(job, appMeta, params)


[수동/일회성 실행 — BatchJobCommandLineRunner.run()]
  │
  ├─ BatchScheduleMapper.selectSchedulesForInstance('WAS1')
  │     → 동일한 JOIN 쿼리 재사용
  │
  └─ (배치별 반복)
      ├─ FwkBatchAppMapper.selectById(batchAppId)
      │     SELECT * FROM FWK_BATCH_APP WHERE BATCH_APP_ID = ?
      │
      ├─ jobMap.get(BATCH_APP_FILE_NAME) → BatchJob 빈 매핑
      │
      └─ BatchJobExecutor.execute(job, appMeta, cliParams)
              │
              ├─ [선행 배치 확인]
              │    FwkBatchHisMapper.existsSuccessToday(PRE_BATCH_APP_ID, today)
              │    미완료 → return SKIPPED
              │
              ├─ BatchLogService.writeInitLog()
              │    SELECT MAX(BATCH_EXECUTE_SEQ) FROM FWK_BATCH_HIS ...
              │    INSERT INTO FWK_BATCH_HIS
              │      (BATCH_APP_ID, INSTANCE_ID, BATCH_DATE, SEQ=nextSeq,
              │       LOG_DTIME=시작시각, RES_RT_CODE='RUNNING')
              │
              ├─ try: job.init(context)
              │    catch Throwable
              │        BatchLogService.writeFinalLog(FAILED_INIT)
              │        UPDATE FWK_BATCH_HIS SET
              │          BATCH_END_DTIME=?, RES_RT_CODE='FAILED_INIT',
              │          ERROR_CODE=?, ERROR_REASON=?
              │        return FAILED_INIT
              │
              ├─ try: job.executeBatch(context)
              │    catch Throwable
              │        BatchLogService.writeFinalLog(FAILED_EXEC)
              │        UPDATE FWK_BATCH_HIS SET ... RES_RT_CODE='FAILED_EXEC'
              │        return FAILED_EXEC
              │
              └─ BatchLogService.writeFinalLog(SUCCESS)
                    UPDATE FWK_BATCH_HIS SET
                      BATCH_END_DTIME=종료시각,
                      RES_RT_CODE='SUCCESS',
                      RECORD_COUNT=?, EXECUTE_COUNT=?,
                      SUCCESS_COUNT=?, FAIL_COUNT=?
                    return SUCCESS
```

---

## 16. 구체 잡 구현 예시

```java
// FWK_BATCH_APP:
//   BATCH_APP_ID='JOB_DAILY_RPT', BATCH_APP_FILE_NAME='DailyReportJob'
//   PRE_BATCH_APP_ID='JOB_DATA_COLLECT', RETRYABLE_YN='Y'
//   PROPERTIES='{"report_date":"${TODAY}","output_dir":"/data/reports"}'
//
// FWK_WAS_EXEC_BATCH:
//   ('JOB_DAILY_RPT', 'WAS1', 'Y')   ← WAS1에서 실행
//   ('JOB_DAILY_RPT', 'WAS2', 'N')   ← WAS2에서 실행 안 함

@Component
public class DailyReportJob extends AbstractBatchJob {

    private final ReportRepository reportRepo;

    public DailyReportJob(ReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    @Override
    public String getBatchAppId() { return "JOB_DAILY_RPT"; }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        try {
            String reportDate = context.getParameters().get("report_date");
            if (reportDate == null || reportDate.isBlank()) {
                throw new IllegalArgumentException("report_date 파라미터 필수");
            }
            log.info("[{}] 초기화 완료, date={}, instanceId={}, seq={}",
                     getBatchAppId(), reportDate,
                     context.getInstanceId(), context.getBatchExecuteSeq());
        } catch (Exception e) {
            throw new BatchInitException("초기화 실패: " + e.getMessage(),
                                         e, getBatchAppId(), "PARAM_INVALID");
        }
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        try {
            String reportDate = context.getParameters().get("report_date");
            String outputDir  = context.getParameters().get("output_dir");

            List<ReportData> data = reportRepo.findByDate(reportDate);
            long success = 0L, fail = 0L;

            for (ReportData item : data) {
                try {
                    generateReport(item, outputDir);
                    success++;
                } catch (Exception e) {
                    log.error("[{}] 항목 처리 실패: id={}", getBatchAppId(), item.getId(), e);
                    fail++;
                }
            }

            // FWK_BATCH_HIS에 기록될 집계 카운터 설정
            context.setCounters(data.size(), success, fail);
            log.info("[{}] 완료: total={}, success={}, fail={}",
                     getBatchAppId(), data.size(), success, fail);

        } catch (Exception e) {
            throw new BatchExecutionException("실행 실패: " + e.getMessage(),
                                              e, getBatchAppId(), "EXEC_FAIL");
        }
    }
}
```

---

## 17. WAS 인스턴스별 배치 매핑 운영 예시

```sql
-- WAS1: 일간리포트, 데이터동기화 실행
INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH
    VALUES ('JOB_DAILY_RPT', 'WAS1', 'Y',
            TO_CHAR(SYSDATE,'YYYYMMDDHH24MISS'), 'ADMIN');
INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH
    VALUES ('JOB_DATA_SYNC', 'WAS1', 'Y',
            TO_CHAR(SYSDATE,'YYYYMMDDHH24MISS'), 'ADMIN');

-- WAS2: 데이터동기화만 실행
INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH
    VALUES ('JOB_DAILY_RPT', 'WAS2', 'N',
            TO_CHAR(SYSDATE,'YYYYMMDDHH24MISS'), 'ADMIN');
INSERT INTO D_SPIDERLINK.FWK_WAS_EXEC_BATCH
    VALUES ('JOB_DATA_SYNC', 'WAS2', 'Y',
            TO_CHAR(SYSDATE,'YYYYMMDDHH24MISS'), 'ADMIN');

-- 코드 변경 없이 WAS1의 특정 배치 일시 중단
UPDATE D_SPIDERLINK.FWK_WAS_EXEC_BATCH
   SET USE_YN = 'N',
       LAST_UPDATE_DTIME = TO_CHAR(SYSDATE,'YYYYMMDDHH24MISS'),
       LAST_UPDATE_USER_ID = 'ADMIN'
 WHERE BATCH_APP_ID = 'JOB_DATA_SYNC'
   AND INSTANCE_ID  = 'WAS1';
```

---

## 18. 핵심 설계 결정 및 근거

| 결정 사항 | 선택 | 근거 |
|---|---|---|
| DB 라이브러리 | **MyBatis** (JPA 미사용) | 복잡한 Oracle SQL 제어 용이, 기존 프레임워크 패턴과 일치 |
| WAS 식별 | `application.yml`의 `batch.instance-id` | 단순 설정 파일 관리, 재배포 없이 변경 가능 |
| 어피니티 기준 | `FWK_WAS_EXEC_BATCH.USE_YN='Y'` | DB에서 즉시 매핑 변경, 코드/재배포 불필요 |
| 실행 이력 | `FWK_BATCH_HIS` (기존 테이블) | 신규 테이블 불필요, 기존 운영 인프라 유지 |
| 이력 PK | `BATCH_APP_ID + INSTANCE_ID + BATCH_DATE + SEQ` | 당일 재시도 지원, WAS 인스턴스별 이력 분리 |
| init() 시점 INSERT | `LOG_DTIME` 기록 후 즉시 INSERT | 배치 시작 사실 즉시 DB 반영, 장애 시 진행 중 상태 확인 가능 |
| 예외 격리 | init() / executeBatch() 별도 try/catch | 어느 단계 실패든 `FWK_BATCH_HIS` UPDATE 보장 |
| 선행 배치 확인 | `existsSuccessToday()` (WAS 무관) | 어느 WAS에서 완료해도 선행 조건 충족 |
| 잡-앱 매핑 | `BATCH_APP_FILE_NAME` ↔ 클래스 단순명 | 설정 파일 없이 클래스명 컨벤션으로 자동 매핑 |
| 파라미터 우선순위 | DB PROPERTIES < CLI 인자 | 기본값은 DB, 실행 시점 오버라이드는 CLI |

---

## 19. 구현 순서 (권장)

1. **예외 계층** — `BatchException`, `BatchInitException`, `BatchExecutionException`
2. **VO 클래스** — `FwkBatchAppVo`, `BatchScheduleVo`, `FwkBatchHisVo`
3. **`BatchExecutionContext`** — VO 포함 실행 컨텍스트 + 집계 카운터 + `baseBatchDate` 필드
4. **`BatchJobResult`** — ResRtCode, 카운터, 오류 결과 객체
5. **`BatchJob` 인터페이스 + `AbstractBatchJob`** — 핵심 계약
6. **`WasInstanceIdentity` + `WasInstanceConfiguration`** — System Property 읽기
7. **`BatchProperties`** — `scheduler-pool-size`, `fail-fast` 설정
8. **`FwkBatchHisMapper.xml` + `FwkBatchHisMapper`** — INSERT/UPDATE/SELECT SQL
9. **`BatchScheduleMapper.xml` + `BatchScheduleMapper`** — JOIN 쿼리 (핵심)
10. **`BatchLogService`** — `writeInitLog` (SEQ계산+INSERT), `writeFinalLog` (UPDATE)
11. **`BatchJobExecutor`** — 오버로드 메서드 2개 (`baseBatchDate` 유무), 단계별 예외 격리, finally 보장
12. **`BatchSchedulerConfig`** — `ThreadPoolTaskScheduler` 빈 설정
13. **`BatchSchedulerManager`** — ApplicationRunner: JOIN → Cron 등록
14. **`BatchRunRequest` / `BatchRunResponse`** — REST 요청/응답 DTO
15. **`BatchManualController`** — `POST /api/batch/{batchAppId}/run`
16. **`SecurityConfig`** — Basic Auth / API Key 인증 설정
17. **`BatchFrameworkAutoConfiguration`** — `@MapperScan` 포함 자동 설정
18. **샘플 구체 잡** — `DailyReportJob` 등으로 E2E 검증

---

## 20. 핵심 파일 목록

| 파일 | 역할 |
|---|---|
| `vo/BatchScheduleVo.java` | JOIN 결과 VO (기동 시 배치 목록 + CRON_TEXT 로드) |
| `vo/FwkBatchHisVo.java` | `FWK_BATCH_HIS` MyBatis 매핑 VO (INSERT/UPDATE 겸용) |
| `mapper/BatchScheduleMapper.java` | `selectSchedulesForInstance` (JOIN 쿼리) |
| `mapper/FwkBatchHisMapper.java` | `insert`, `update`, `selectMaxExecuteSeq`, `existsSuccessToday` |
| `resources/mapper/BatchScheduleMapper.xml` | **JOIN SQL** — FWK_WAS_EXEC_BATCH ⋈ FWK_BATCH_APP |
| `resources/mapper/FwkBatchHisMapper.xml` | INSERT/UPDATE/SELECT SQL |
| `scheduler/BatchSchedulerManager.java` | **핵심**: 기동 시 JOIN → Cron 등록 → TaskScheduler |
| `config/BatchSchedulerConfig.java` | `ThreadPoolTaskScheduler` 빈 (Layer 4 ErrorHandler) |
| `WasInstanceConfiguration.java` | System Property `batch.instance.id` 읽기, 빈 생성 |
| `core/BatchJobExecutor.java` | 오버로드 2개(baseBatchDate), finally 보장, 4레이어 예외처리 |
| `logging/BatchLogService.java` | `writeInitLog` (SEQ계산+INSERT), `writeFinalLog` (UPDATE) |
| `core/BatchJob.java` | 모든 배치 잡의 계약 인터페이스 |
| `web/BatchManualController.java` | `POST /api/batch/{batchAppId}/run` REST 수동 실행 |
| `web/BatchRunRequest.java` | 수동 실행 요청 DTO (`baseBatchDate`, `params`) |
| `web/BatchRunResponse.java` | 수동 실행 응답 DTO (`resRtCode`, 카운터, 오류 정보) |
