package com.hanabank.nbc.mis.batch.framework.mapper;

import com.hanabank.nbc.mis.batch.framework.vo.BatchScheduleVo;
import com.hanabank.nbc.mis.batch.framework.vo.FwkBatchAppVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 배치 스케줄 조회 MyBatis Mapper.
 * WAS 기동 시 해당 인스턴스에서 실행할 배치 목록을 조회한다.
 */
@Mapper
public interface BatchScheduleMapper {

    /**
     * 지정 WAS 인스턴스가 실행해야 할 배치 스케줄 목록 조회.
     *
     * <p>FWK_WAS_EXEC_BATCH (USE_YN='Y') JOIN FWK_BATCH_APP 쿼리.
     * Cron 표현식, 선행배치 ID, 파라미터 등 모든 실행 정보를 포함한다.
     *
     * @param instanceId WAS 인스턴스 ID (예: "WAS1")
     * @return 실행할 배치 목록 (USE_YN='Y' 인 것만)
     */
    List<BatchScheduleVo> selectSchedulesForInstance(@Param("instanceId") String instanceId);

    /**
     * 배치 앱 ID로 FWK_BATCH_APP 단건 조회.
     * REST 수동 실행 시 배치 앱 메타 정보 로드에 사용.
     *
     * @param batchAppId 배치 앱 ID
     * @return 배치 앱 VO (없으면 null)
     */
    FwkBatchAppVo selectBatchAppById(@Param("batchAppId") String batchAppId);
}
