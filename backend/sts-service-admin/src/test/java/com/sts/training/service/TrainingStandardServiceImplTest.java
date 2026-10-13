package com.sts.training.service;

import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingStandardCreateRequest;
import com.sts.training.dto.TrainingStandardRuleRequest;
import com.sts.training.dto.TrainingStandardRulesReplaceRequest;
import com.sts.training.dto.TrainingStandardUpdateRequest;
import com.sts.training.entity.TrainingStandard;
import com.sts.training.entity.TrainingStandardRule;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingStandardMapper;
import com.sts.training.mapper.TrainingStandardRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingStandardServiceImplTest {

  private TrainingStandardMapper standardMapper;
  private TrainingStandardRuleMapper ruleMapper;
  private TrainingResourceAccessHelper resourceAccessHelper;
  private TrainingStandardServiceImpl service;

  @BeforeEach
  void setUp() {
    standardMapper = mock(TrainingStandardMapper.class);
    ruleMapper = mock(TrainingStandardRuleMapper.class);
    resourceAccessHelper = mock(TrainingResourceAccessHelper.class);
    service = new TrainingStandardServiceImpl(standardMapper, ruleMapper, resourceAccessHelper);
  }

  @Test
  @DisplayName("create：创建草稿标准")
  void create_ok() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(type);
    when(standardMapper.insert(any(TrainingStandard.class))).thenAnswer(inv -> {
      TrainingStandard s = inv.getArgument(0);
      s.setTrainingStandardId(10L);
      return 1;
    });
    TrainingStandardCreateRequest req = new TrainingStandardCreateRequest();
    req.setStandardCode("STD1");
    req.setStandardName("标准1");
    req.setTrainingTypeId(1L);
    req.setStandardVersion("v1");
    req.setStatus(1);
    assertThat(service.create(req)).isEqualTo(10L);
  }

  @Test
  @DisplayName("publish：草稿可发布")
  void publish_draft_ok() {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(10L);
    standard.setPublishStatus("DRAFT");
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(1);
    service.publish(10L, 0);
    assertThat(standard.getPublishStatus()).isEqualTo("PUBLISHED");
    verify(standardMapper).updateById(standard);
  }

  @Test
  @DisplayName("update：已发布不可编辑")
  void update_published_throws() {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(10L);
    standard.setPublishStatus("PUBLISHED");
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(1);
    TrainingStandardUpdateRequest req = new TrainingStandardUpdateRequest();
    req.setVersion(0);
    req.setStandardName("x");
    req.setTrainingTypeId(1L);
    req.setStandardVersion("v1");
    req.setStatus(1);
    assertThatThrownBy(() -> service.update(10L, req)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("replaceRules：替换规则")
  void replaceRules_ok() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(1);
    when(ruleMapper.update(any(), any())).thenReturn(1);
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    TrainingStandardRuleRequest rule = new TrainingStandardRuleRequest();
    rule.setRuleType("TOTAL_HOURS");
    rule.setRequiredValue(new BigDecimal("24"));
    rule.setSortOrder(1);
    rule.setStatus(1);
    rule.setDescription("desc");
    req.setRules(List.of(rule));
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(typeHours());
    service.replaceRules(10L, req);
    verify(ruleMapper).update(isNull(), any());
    verify(ruleMapper).insert(any(TrainingStandardRule.class));
  }

  @Test
  @DisplayName("update：版本冲突返回 409/8014")
  void update_versionConflict_throws() {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(10L);
    standard.setPublishStatus("DRAFT");
    standard.setVersion(2);
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(0);
    TrainingStandardUpdateRequest req = new TrainingStandardUpdateRequest();
    req.setVersion(1);
    req.setStandardName("标准1");
    req.setTrainingTypeId(1L);
    req.setStandardVersion("v1");
    req.setStatus(1);

    assertThatThrownBy(() -> service.update(10L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8014);
  }

  @Test
  @DisplayName("replaceRules：版本冲突时不删除既有规则")
  void replaceRules_versionConflict_doesNotDeleteRules() {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(10L);
    standard.setPublishStatus("DRAFT");
    standard.setVersion(2);
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(0);
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(1);
    assertThatThrownBy(() -> service.replaceRules(10L, req)).isInstanceOf(BusinessException.class);
    verify(ruleMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("replaceRules：重复总学时返回 409/8016 且不改规则")
  void replaceRules_duplicateTotalHours_throwsWithoutMutating() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    req.setRules(List.of(rule("TOTAL_HOURS", 1), rule("TOTAL_HOURS", 2)));

    assertThatThrownBy(() -> service.replaceRules(10L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8016)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.CONFLICT)
      .hasMessageContaining("总学时");
    verify(standardMapper, never()).updateById(any(TrainingStandard.class));
    verify(ruleMapper, never()).update(any(), any());
    verify(ruleMapper, never()).insert(any(TrainingStandardRule.class));
  }

  @Test
  @DisplayName("replaceRules：重复线上占比返回 409/8016 且不改规则")
  void replaceRules_duplicateOnlineRatio_throwsWithoutMutating() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    req.setRules(List.of(rule("ONLINE_RATIO", 1), rule("COURSE", 2), rule("ONLINE_RATIO", 3)));

    assertThatThrownBy(() -> service.replaceRules(10L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8016)
      .hasMessageContaining("线上学时占比");
    verify(standardMapper, never()).updateById(any(TrainingStandard.class));
    verify(ruleMapper, never()).insert(any(TrainingStandardRule.class));
  }

  @Test
  @DisplayName("replaceRules：基础规则各一条且指定资源可多条")
  void replaceRules_singletonBasicsWithMultipleResources_ok() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(1);
    when(ruleMapper.update(any(), any())).thenReturn(1);
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    req.setRules(List.of(
      hourRule("TOTAL_HOURS", 1, "24"),
      hourRule("ONLINE_RATIO", 2, "0.3000"),
      rule("COURSE", 3),
      rule("COURSE", 4)));
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(typeHours());

    service.replaceRules(10L, req);

    verify(ruleMapper, times(4)).insert(any(TrainingStandardRule.class));
  }

  @Test
  @DisplayName("replaceRules：空规则列表清空且不因缺少基础规则失败")
  void replaceRules_emptyList_clearsWithoutRequiringBasics() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(1);
    when(ruleMapper.update(any(), any())).thenReturn(1);
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    req.setRules(List.of());

    service.replaceRules(10L, req);

    verify(ruleMapper).update(isNull(), any());
    verify(ruleMapper, never()).insert(any(TrainingStandardRule.class));
  }

  @Test
  @DisplayName("publish：已有重复总学时不可发布")
  void publish_duplicateTotalHours_throwsAndKeepsDraft() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    TrainingStandardRule first = persistedRule("TOTAL_HOURS");
    TrainingStandardRule second = persistedRule("TOTAL_HOURS");
    when(ruleMapper.selectList(any())).thenReturn(List.of(first, second));

    assertThatThrownBy(() -> service.publish(10L, 0))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8016)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.CONFLICT);
    assertThat(standard.getPublishStatus()).isEqualTo("DRAFT");
    verify(standardMapper, never()).updateById(any(TrainingStandard.class));
  }

  @Test
  @DisplayName("replaceRules：总学时低于类型较小基线返回 400/8017 且不改规则")
  void replaceRules_totalHoursBelowTypeMin_throwsWithoutMutating() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(typeHours());
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    req.setRules(List.of(hourRule("TOTAL_HOURS", 1, "20")));

    assertThatThrownBy(() -> service.replaceRules(10L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8017)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    verify(standardMapper, never()).updateById(any(TrainingStandard.class));
    verify(ruleMapper, never()).insert(any(TrainingStandardRule.class));
  }

  @Test
  @DisplayName("replaceRules：线上占比宽于类型上限返回 400/8018 且不改规则")
  void replaceRules_onlineRatioWiderThanType_throwsWithoutMutating() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(typeHours());
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    req.setRules(List.of(hourRule("ONLINE_RATIO", 1, "0.5000")));

    assertThatThrownBy(() -> service.replaceRules(10L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8018)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    verify(ruleMapper, never()).insert(any(TrainingStandardRule.class));
  }

  @Test
  @DisplayName("replaceRules：指定课程只写入规则，不生成阶段")
  void replaceRules_courseRuleOnlyWritesRules() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(standardMapper.updateById(any(TrainingStandard.class))).thenReturn(1);
    when(ruleMapper.update(any(), any())).thenReturn(1);
    TrainingStandardRuleRequest course = rule("COURSE", 1);
    course.setResourceId(99L);
    course.setResourceType("COURSE");
    TrainingStandardRulesReplaceRequest req = new TrainingStandardRulesReplaceRequest();
    req.setVersion(0);
    req.setRules(List.of(course));

    service.replaceRules(10L, req);

    verify(ruleMapper).insert(any(TrainingStandardRule.class));
    verify(resourceAccessHelper, never()).requireTypeForUse(any());
  }

  @Test
  @DisplayName("publish：总学时低于类型较小基线返回 400/8017 且保持草稿")
  void publish_totalHoursBelowTypeMin_keepsDraft() {
    TrainingStandard standard = draftStandard();
    when(standardMapper.selectById(10L)).thenReturn(standard);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(typeHours());
    TrainingStandardRule hours = persistedRule("TOTAL_HOURS");
    hours.setRequiredValue(new BigDecimal("20"));
    hours.setStatus(1);
    when(ruleMapper.selectList(any())).thenReturn(List.of(hours));

    assertThatThrownBy(() -> service.publish(10L, 0))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8017);
    assertThat(standard.getPublishStatus()).isEqualTo("DRAFT");
    verify(standardMapper, never()).updateById(any(TrainingStandard.class));
  }

  private static TrainingStandard draftStandard() {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(10L);
    standard.setTrainingTypeId(1L);
    standard.setPublishStatus("DRAFT");
    standard.setVersion(0);
    return standard;
  }

  private static TrainingType typeHours() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    type.setInitialHours(new BigDecimal("80"));
    type.setReviewHours(new BigDecimal("24"));
    type.setOnlineMaxRatio(new BigDecimal("0.3000"));
    return type;
  }

  private static TrainingStandardRuleRequest rule(String ruleType, int sortOrder) {
    TrainingStandardRuleRequest rule = new TrainingStandardRuleRequest();
    rule.setRuleType(ruleType);
    rule.setSortOrder(sortOrder);
    rule.setStatus(1);
    rule.setDescription("desc");
    return rule;
  }

  private static TrainingStandardRuleRequest hourRule(String ruleType, int sortOrder, String requiredValue) {
    TrainingStandardRuleRequest rule = rule(ruleType, sortOrder);
    rule.setRequiredValue(new BigDecimal(requiredValue));
    return rule;
  }

  private static TrainingStandardRule persistedRule(String ruleType) {
    TrainingStandardRule rule = new TrainingStandardRule();
    rule.setRuleType(ruleType);
    rule.setStatus(0);
    return rule;
  }
}
