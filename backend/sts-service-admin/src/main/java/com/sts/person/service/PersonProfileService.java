package com.sts.person.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.person.dto.PersonProfileRequest;
import com.sts.person.entity.PersonProfile;
import com.sts.person.mapper.*;
import com.sts.person.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.sts.training.service.TrainingResultSyncService;
import org.springframework.beans.factory.ObjectProvider;

/** 人工特征与系统培训事实分开读写。 */
@Service @RequiredArgsConstructor
public class PersonProfileService {
  /** 既有人员归属。 */
  private final SysUserMapper users;
  /** 手工特征持久化。 */
  private final PersonProfileMapper profiles;
  /** 培训事实只读查询。 */
  private final PersonClassificationMapper facts;
  /** 与培训统计同一只读结果来源。 */
  private final ObjectProvider<TrainingResultSyncService> results;

  /** 空特征返回版本0，允许首次维护。 */
  public PersonProfileVO get(Long userId) {
    requireUser(userId); PersonProfile profile = find(userId); PersonProfileVO vo = new PersonProfileVO();
    if (profile != null) BeanUtils.copyProperties(profile, vo);
    else { vo.setUserId(userId); vo.setVersion(0); }
    return vo;
  }

  /** 全量保存人工字段；空值可清除，不能修改培训事实。 */
  @Transactional
  public void save(Long userId, PersonProfileRequest request) {
    requireUser(userId); PersonProfile existing = find(userId);
    if (existing == null && !Integer.valueOf(0).equals(request.getVersion())) throw conflict();
    if (existing != null && !request.getVersion().equals(existing.getVersion())) throw conflict();
    PersonProfile profile = existing == null ? new PersonProfile() : existing;
    BeanUtils.copyProperties(request, profile);
    profile.setUserId(userId); profile.setOrganizationId(TenantContext.requireTenantId());
    try {
      if (existing == null) profiles.insert(profile);
      else if (profiles.updateById(profile) == 0) throw conflict();
    } catch (DuplicateKeyException e) { throw conflict(); }
  }

  /** 只读展示真实培训与奖惩，不把人工说明当正式业务事实。 */
  public List<PersonTrainingFactVO> trainingFacts(Long userId) {
    requireUser(userId);
    return facts.trainingFacts(TenantContext.requireTenantId(), userId).stream().map(fact -> {
      var current = results.getObject().getCurrentResults(fact.getTrainingPlanId(), fact.getTrainingParticipantId());
      fact.setResultStatus(current.getResultStatus()); fact.setOnlineHours(current.getOnlineHours());
      fact.setOfflineHours(current.getOfflineHours()); return fact;
    }).toList();
  }

  private PersonProfile find(Long userId) {
    return profiles.selectOne(new LambdaQueryWrapper<PersonProfile>()
      .eq(PersonProfile::getOrganizationId, TenantContext.requireTenantId()).eq(PersonProfile::getUserId, userId));
  }
  private void requireUser(Long userId) {
    SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>()
      .eq(SysUser::getOrganizationId, TenantContext.requireTenantId()).eq(SysUser::getUserId, userId));
    if (user == null) throw new BusinessException(HttpStatus.BAD_REQUEST, 4120, "人员不存在或不属于当前机构");
  }
  private BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT, 4122, "人员特征版本冲突，请刷新"); }
}
