package com.sts.training.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.training.entity.TrainingStandard;
import com.sts.training.entity.TrainingTemplate;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingStandardMapper;
import com.sts.training.mapper.TrainingTemplateMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TrainingResourceAccessHelper {

  private final TrainingTypeMapper trainingTypeMapper;
  private final TrainingStandardMapper trainingStandardMapper;
  private final TrainingTemplateMapper trainingTemplateMapper;

  public TrainingType requireTypeForUse(Long trainingTypeId) {
    TrainingType type = trainingTypeMapper.selectById(trainingTypeId);
    if (type == null || !belongsToCurrentOrganization(type.getOrganizationId()) || !isEnabled(type.getStatus())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8001, "培训类型不存在");
    }
    return type;
  }

  public TrainingStandard requireStandardForUse(Long standardId) {
    if (standardId == null) {
      return null;
    }
    TrainingStandard standard = trainingStandardMapper.selectById(standardId);
    if (standard == null || !belongsToCurrentOrganization(standard.getOrganizationId())
      || !isEnabled(standard.getStatus())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8011, "培训标准不存在");
    }
    return standard;
  }

  public TrainingTemplate requireTemplateForUse(Long templateId) {
    if (templateId == null) {
      return null;
    }
    TrainingTemplate template = trainingTemplateMapper.selectById(templateId);
    if (template == null || !belongsToCurrentOrganization(template.getOrganizationId())
      || !isEnabled(template.getStatus())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8021, "培训模板不存在");
    }
    return template;
  }

  private boolean belongsToCurrentOrganization(Long ownerOrganizationId) {
    return Objects.equals(TenantContext.requireTenantId(), ownerOrganizationId);
  }

  private boolean isEnabled(Integer status) {
    return Objects.equals(1, status);
  }
}
