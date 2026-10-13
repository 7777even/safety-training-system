package com.sts.regulation.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.training.dto.TrainingPlanQuery;
import com.sts.training.service.TrainingPlanService;
import com.sts.training.service.TrainingResultSyncService;
import com.sts.training.vo.TrainingParticipantResultsVO;
import com.sts.training.vo.TrainingParticipantVO;
import com.sts.training.vo.TrainingPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegulationTrainingRecordServiceImpl implements RegulationTrainingRecordService {

  private final RegulatorIdentityGuard regulatorIdentityGuard;
  private final TrainingPlanService trainingPlanService;
  private final TrainingResultSyncService resultSyncService;

  @Override
  public PageResult<TrainingPlanVO> pagePlans(PageRequest<TrainingPlanQuery> request) {
    prepare();
    return trainingPlanService.page(request);
  }

  @Override
  public TrainingPlanVO getPlan(Long trainingPlanId) {
    prepare();
    return trainingPlanService.get(trainingPlanId);
  }

  @Override
  public List<TrainingParticipantVO> listParticipants(Long trainingPlanId) {
    prepare();
    return trainingPlanService.listParticipants(trainingPlanId);
  }

  @Override
  public TrainingParticipantResultsVO getResults(Long trainingPlanId, Long trainingParticipantId) {
    prepare();
    return resultSyncService.getResults(trainingPlanId, trainingParticipantId);
  }

  private void prepare() {
    regulatorIdentityGuard.requireRegulator();
    Long currentTenantId = TenantContext.currentTenantIdOrNull();
    SaSession session = StpUtil.getTokenSession();
    Object loginOrg = session == null ? null : session.get(TenantSessionKeys.ORGANIZATION_ID);
    long loginOrgId = loginOrg instanceof Number number ? number.longValue() : -1L;
    if (currentTenantId == null || currentTenantId == loginOrgId) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9860,
                                  "须通过 X-Target-Tenant-Id 切换到目标企业后再查询培训记录");
    }
  }
}
