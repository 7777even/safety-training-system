package com.sts.training.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.training.entity.TrainingStandard;
import com.sts.training.entity.TrainingTemplate;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingStandardMapper;
import com.sts.training.mapper.TrainingTemplateMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingResourceAccessHelperTest {

  private TrainingTypeMapper typeMapper;
  private TrainingStandardMapper standardMapper;
  private TrainingTemplateMapper templateMapper;
  private TrainingResourceAccessHelper helper;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(1L);
    typeMapper = mock(TrainingTypeMapper.class);
    standardMapper = mock(TrainingStandardMapper.class);
    templateMapper = mock(TrainingTemplateMapper.class);
    helper = new TrainingResourceAccessHelper(
      typeMapper,
      standardMapper,
      templateMapper);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("requireTypeForUse：其他机构培训类型一律按不存在处理")
  void requireTypeForUse_otherOrganization_throws8001() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(10L);
    type.setOrganizationId(99L);
    when(typeMapper.selectById(10L)).thenReturn(type);

    assertThatThrownBy(() -> helper.requireTypeForUse(10L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(8001));
  }

  @Test
  @DisplayName("requireStandardForUse：其他机构培训标准一律按不存在处理")
  void requireStandardForUse_otherOrganization_throws8011() {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(20L);
    standard.setOrganizationId(99L);
    when(standardMapper.selectById(20L)).thenReturn(standard);

    assertThatThrownBy(() -> helper.requireStandardForUse(20L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(8011));
  }

  @Test
  @DisplayName("requireTemplateForUse：其他机构培训模板一律按不存在处理")
  void requireTemplateForUse_otherOrganization_throws8021() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(30L);
    template.setOrganizationId(99L);
    when(templateMapper.selectById(30L)).thenReturn(template);

    assertThatThrownBy(() -> helper.requireTemplateForUse(30L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(8021));
  }

  @Test
  @DisplayName("requireTypeForUse：本机构禁用培训类型不可用于新引用")
  void requireTypeForUse_disabled_throws8001() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(10L);
    type.setOrganizationId(1L);
    type.setStatus(0);
    when(typeMapper.selectById(10L)).thenReturn(type);

    assertThatThrownBy(() -> helper.requireTypeForUse(10L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(8001));
  }

  @Test
  @DisplayName("requireStandardForUse：本机构禁用培训标准不可用于新引用")
  void requireStandardForUse_disabled_throws8011() {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(20L);
    standard.setOrganizationId(1L);
    standard.setStatus(0);
    when(standardMapper.selectById(20L)).thenReturn(standard);

    assertThatThrownBy(() -> helper.requireStandardForUse(20L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(8011));
  }

  @Test
  @DisplayName("requireTemplateForUse：本机构禁用培训模板不可用于新引用")
  void requireTemplateForUse_disabled_throws8021() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(30L);
    template.setOrganizationId(1L);
    template.setStatus(0);
    when(templateMapper.selectById(30L)).thenReturn(template);

    assertThatThrownBy(() -> helper.requireTemplateForUse(30L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(8021));
  }

  @Test
  @DisplayName("本机构培训类型、标准和模板保持可用")
  void sameOrganizationResources_areReturned() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(10L);
    type.setOrganizationId(1L);
    type.setStatus(1);
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(20L);
    standard.setOrganizationId(1L);
    standard.setStatus(1);
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(30L);
    template.setOrganizationId(1L);
    template.setStatus(1);
    when(typeMapper.selectById(10L)).thenReturn(type);
    when(standardMapper.selectById(20L)).thenReturn(standard);
    when(templateMapper.selectById(30L)).thenReturn(template);

    assertThat(helper.requireTypeForUse(10L)).isSameAs(type);
    assertThat(helper.requireStandardForUse(20L)).isSameAs(standard);
    assertThat(helper.requireTemplateForUse(30L)).isSameAs(template);
  }
}
