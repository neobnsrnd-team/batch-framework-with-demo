package com.hanabank.nbc.mis.batch.framework.mapper;

import com.hanabank.nbc.mis.batch.framework.vo.FwkBatchHisVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * FWK_BATCH_HIS 테이블 MyBatis Mapper.
 * 배치 실행 이력의 INSERT/UPDATE/SELECT 를 담당한다.
 */
@Mapper
public interface FwkBatchHisMapper {

    /**
     * 배치 시작 이력 INSERT (상태: RUNNING).
     * init() 호출 직전 프레임워크가 자동 호출한다.
     *
     * @param vo PK(batchAppId, instanceId, batchDate, batchExecuteSeq) + logDtime 필수
     */
    void insertInitLog(FwkBatchHisVo vo);

    /**
     * 배치 종료 이력 UPDATE (상태: SUCCESS/FAILED_INIT/FAILED_EXEC 등).
     * executeBatch() 완료 후 프레임워크가 finally 블록에서 반드시 호출한다.
     *
     * @param vo PK + batchEndDtime + resRtCode + 카운터 + 오류 정보 필수
     */
    void updateFinalLog(FwkBatchHisVo vo);

    /**
     * 당일 최대 실행 순번 조회.
     * 신규 실행 시 MAX(SEQ)+1 계산에 사용한다.
     * 이력이 없으면 0을 반환한다.
     *
     * @param batchAppId   배치 앱 ID
     * @param instanceId   WAS 인스턴스 ID
     * @param batchDate    배치 기준 날짜 (YYYYMMDD)
     * @return 최대 실행 순번 (없으면 0)
     */
    int selectMaxExecuteSeq(@Param("batchAppId")  String batchAppId,
                            @Param("instanceId")  String instanceId,
                            @Param("batchDate")   String batchDate);

    /**
     * 선행 배치의 당일 SUCCESS 이력 존재 여부.
     * 선행 배치 완료 확인에 사용 (WAS 인스턴스 무관 - 어떤 WAS에서 완료해도 조건 충족).
     *
     * @param batchAppId   선행 배치 앱 ID
     * @param batchDate    배치 기준 날짜 (YYYYMMDD)
     * @return true = SUCCESS 이력 존재
     */
    boolean existsSuccessToday(@Param("batchAppId") String batchAppId,
                               @Param("batchDate")  String batchDate);

    /**
     * 지정 배치의 RUNNING 상태 이력 존재 여부.
     * 중복 실행 방지 체크에 사용 (forceRerun=true 여도 항상 차단).
     *
     * @param batchAppId   배치 앱 ID
     * @param batchDate    배치 기준 날짜 (YYYYMMDD)
     * @return true = RUNNING 이력 존재 (실행 중)
     */
    boolean existsRunning(@Param("batchAppId") String batchAppId,
                          @Param("batchDate")  String batchDate);

    /**
     * 지정 배치의 SUCCESS 상태 이력 존재 여부.
     * force-rerun 전 확인에 사용.
     *
     * @param batchAppId   배치 앱 ID
     * @param batchDate    배치 기준 날짜 (YYYYMMDD)
     * @return true = SUCCESS 이력 존재
     */
    boolean existsSuccess(@Param("batchAppId") String batchAppId,
                          @Param("batchDate")  String batchDate);

    /**
     * 지정 배치의 SUCCESS 이력을 CANCELED 로 변경.
     * forceRerun=true 시 기존 성공 이력을 취소하고 재실행하기 위해 사용.
     *
     * @param batchAppId   배치 앱 ID
     * @param batchDate    배치 기준 날짜 (YYYYMMDD)
     * @param canceledBy   취소 요청자 (WAS 인스턴스 ID)
     * @return 취소된 건수
     */
    int cancelPreviousSuccess(@Param("batchAppId")  String batchAppId,
                              @Param("batchDate")   String batchDate,
                              @Param("canceledBy")  String canceledBy);

    /**
     * 배치 실행 이력 전체 조회 (최근 순).
     *
     * @param limit 조회 건수 제한
     * @return 이력 목록
     */
    java.util.List<FwkBatchHisVo> selectRecentHistory(@Param("limit") int limit);
}
