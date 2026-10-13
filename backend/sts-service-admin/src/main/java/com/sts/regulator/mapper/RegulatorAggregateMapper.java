package com.sts.regulator.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.sts.regulator.vo.InstitutionSummaryVO;
import com.sts.regulator.vo.LearningOfferingRowVO;
import com.sts.regulator.vo.LearningLearnerRowVO;
import com.sts.regulator.vo.LearningStatsVO;
import com.sts.regulator.vo.SpecialOperationStatsVO;
import com.sts.regulator.vo.TenantAggregateItemVO;
import com.sts.regulator.vo.TrainingTypeShareVO;
import com.sts.regulator.vo.WorkbenchTotalsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 跨租户只读聚合查询。MUST 只提供只读 SQL；调用方 MUST 先校验 REGULATOR 身份。
 */
@Mapper
public interface RegulatorAggregateMapper {

  @InterceptorIgnore(tenantLine = "true")
  List<TenantAggregateItemVO> selectTenantAggregates();

  @InterceptorIgnore(tenantLine = "true")
  WorkbenchTotalsVO selectWorkbenchTotals();

  @InterceptorIgnore(tenantLine = "true")
  List<InstitutionSummaryVO> selectInstitutionSummaries();

  @InterceptorIgnore(tenantLine = "true")
  List<TrainingTypeShareVO> selectTrainingTypeDistribution();

  @InterceptorIgnore(tenantLine = "true")
  SpecialOperationStatsVO selectSpecialOperationStats();

  @InterceptorIgnore(tenantLine = "true")
  long selectUnreadWarningCount();

  @InterceptorIgnore(tenantLine = "true")
  LearningStatsVO selectLearningStats(@Param("organizationId") Long organizationId);

  @InterceptorIgnore(tenantLine = "true")
  List<LearningStatsVO> selectLearningStatsByInstitution();

  @InterceptorIgnore(tenantLine = "true")
  List<LearningLearnerRowVO> selectLearningLearners(@Param("organizationId") Long organizationId);

  @InterceptorIgnore(tenantLine = "true")
  List<LearningOfferingRowVO> selectLearningOfferings(@Param("organizationId") Long organizationId);
}
