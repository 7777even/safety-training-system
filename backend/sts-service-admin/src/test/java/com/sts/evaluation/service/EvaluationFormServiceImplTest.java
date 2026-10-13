package com.sts.evaluation.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.exception.BusinessException;
import com.sts.evaluation.dto.*;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.entity.EvaluationItem;
import com.sts.evaluation.entity.EvaluationScoreRule;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.evaluation.mapper.EvaluationItemMapper;
import com.sts.evaluation.mapper.EvaluationScoreRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvaluationFormServiceImplTest {

  private EvaluationFormMapper formMapper;
  private EvaluationFormVersionMapper versionMapper;
  private EvaluationItemMapper itemMapper;
  private EvaluationScoreRuleMapper scoreRuleMapper;
  private EvaluationFormServiceImpl service;

  @BeforeEach
  void setUp() {
    formMapper = mock(EvaluationFormMapper.class);
    versionMapper = mock(EvaluationFormVersionMapper.class);
    itemMapper = mock(EvaluationItemMapper.class);
    scoreRuleMapper = mock(EvaluationScoreRuleMapper.class);
    service = new EvaluationFormServiceImpl(formMapper, versionMapper, itemMapper, scoreRuleMapper);
  }

  @Test
  @DisplayName("create：创建表单")
  void create_ok() {
    when(formMapper.insert(any(EvaluationForm.class))).thenAnswer(inv -> {
      EvaluationForm f = inv.getArgument(0);
      f.setEvaluationFormId(1L);
      return 1;
    });
    EvaluationFormCreateRequest req = new EvaluationFormCreateRequest();
    req.setFormCode("F1");
    req.setFormName("表单1");
    req.setFormType("COMPOSITE");
    req.setStatus(1);
    req.setDescription("desc");
    assertThat(service.create(req)).isEqualTo(1L);
  }

  @Test
  @DisplayName("create：拒绝非综合评价和非专家查课表类型")
  void create_rejectsOtherFormTypes() {
    EvaluationFormCreateRequest req = new EvaluationFormCreateRequest();
    req.setFormCode("F2");
    req.setFormName("人员评价");
    req.setFormType("PARTICIPANT");
    req.setStatus(1);
    req.setDescription("desc");
    assertThatThrownBy(() -> service.create(req))
      .isInstanceOf(BusinessException.class)
      .extracting("code")
      .isEqualTo(8508);
    verify(formMapper, never()).insert(any(EvaluationForm.class));
  }

  @Test
  @DisplayName("publishNewVersion：递增版本号")
  void publishNewVersion_ok() {
    EvaluationForm form = new EvaluationForm();
    form.setEvaluationFormId(1L);
    form.setCurrentVersionNo(1);
    when(formMapper.selectById(1L)).thenReturn(form);
    when(versionMapper.insert(any(EvaluationFormVersion.class))).thenAnswer(inv -> {
      EvaluationFormVersion v = inv.getArgument(0);
      v.setFormVersionId(11L);
      return 1;
    });
    when(itemMapper.selectList(any())).thenReturn(List.of());
    when(scoreRuleMapper.selectList(any())).thenReturn(List.of());
    EvaluationFormPublishRequest req = new EvaluationFormPublishRequest();
    req.setVersion(0);
    req.setTotalScore(new BigDecimal("100"));
    EvaluationItemRequest item = new EvaluationItemRequest();
    item.setItemCode("I1");
    item.setItemName("项目1");
    item.setItemType("SCORE");
    item.setMaxScore(new BigDecimal("100"));
    item.setWeight(new BigDecimal("1"));
    item.setSortOrder(1);
    item.setIsRequired(1);
    req.setItems(List.of(item));
    EvaluationScoreRuleRequest scoreRule = new EvaluationScoreRuleRequest();
    scoreRule.setEvaluationItemCode("I1");
    scoreRule.setCalculationType("WEIGHT");
    scoreRule.setSourceType("EXAM");
    scoreRule.setWeight(new BigDecimal("1"));
    scoreRule.setSortOrder(1);
    scoreRule.setRuleDescription("考试成绩权重");
    req.setScoreRules(List.of(scoreRule));
    when(itemMapper.insert(any(EvaluationItem.class))).thenAnswer(inv -> {
      EvaluationItem evaluationItem = inv.getArgument(0);
      evaluationItem.setEvaluationItemId(11L);
      return 1;
    });
    when(formMapper.updateById(any(EvaluationForm.class))).thenReturn(1);
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      var vo = service.publishNewVersion(1L, req);
      assertThat(vo.getVersionNo()).isEqualTo(2);
      assertThat(form.getCurrentVersionNo()).isEqualTo(2);
      assertThat(form.getCurrentVersionNo()).isEqualTo(2);
      verify(itemMapper).insert(any(EvaluationItem.class));
      ArgumentCaptor<EvaluationScoreRule> ruleCaptor = ArgumentCaptor.forClass(EvaluationScoreRule.class);
      verify(scoreRuleMapper).insert(ruleCaptor.capture());
      assertThat(ruleCaptor.getValue().getEvaluationItemId()).isEqualTo(11L);
    }
  }

  @Test
  @DisplayName("publishNewVersion：综合评价拒绝重复来源和非闭合权重")
  void publishNewVersion_compositeRulesMustBeUniqueAndClosed() {
    EvaluationForm form = new EvaluationForm();
    form.setEvaluationFormId(1L);
    form.setFormType("COMPOSITE");
    when(formMapper.selectById(1L)).thenReturn(form);
    EvaluationItemRequest item = new EvaluationItemRequest();
    item.setItemCode("EXAM_SCORE");
    item.setItemName("考试成绩");
    item.setItemType("SCORE");
    item.setMaxScore(new BigDecimal("100"));
    item.setWeight(new BigDecimal("0.6"));
    item.setSortOrder(1);
    item.setIsRequired(1);
    EvaluationScoreRuleRequest rule = new EvaluationScoreRuleRequest();
    rule.setEvaluationItemCode("EXAM_SCORE");
    rule.setCalculationType("WEIGHT");
    rule.setSourceType("EXAM");
    rule.setWeight(new BigDecimal("0.6"));
    rule.setSortOrder(1);
    rule.setRuleDescription("考试");
    EvaluationFormPublishRequest request = new EvaluationFormPublishRequest();
    request.setVersion(0);
    request.setTotalScore(new BigDecimal("100"));
    request.setItems(List.of(item));
    request.setScoreRules(List.of(rule));

    assertThatThrownBy(() -> service.publishNewVersion(1L, request))
      .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(8508));
  }

  @Test
  @DisplayName("publishNewVersion：无项目失败")
  void publishNewVersion_emptyItems_throws() {
    EvaluationForm form = new EvaluationForm();
    form.setEvaluationFormId(1L);
    when(formMapper.selectById(1L)).thenReturn(form);
    EvaluationFormPublishRequest req = new EvaluationFormPublishRequest();
    req.setVersion(0);
    req.setTotalScore(new BigDecimal("100"));
    req.setItems(List.of());
    assertThatThrownBy(() -> service.publishNewVersion(1L, req)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("listVersionSummaries：按版本号倒序返回表单版本历史")
  void listVersionSummaries_ordersByVersionDesc() {
    EvaluationForm form = new EvaluationForm();
    form.setEvaluationFormId(1L);
    when(formMapper.selectById(1L)).thenReturn(form);
    EvaluationFormVersion older = new EvaluationFormVersion();
    older.setFormVersionId(10L);
    older.setVersionNo(1);
    EvaluationFormVersion newer = new EvaluationFormVersion();
    newer.setFormVersionId(11L);
    newer.setVersionNo(2);
    when(versionMapper.selectList(any())).thenReturn(List.of(older, newer));

    var versions = service.listVersionSummaries(1L);

    assertThat(versions).extracting(item -> item.getFormVersionId()).containsExactly(11L, 10L);
  }

  @Test
  @DisplayName("update：版本冲突返回 409/8504")
  void update_versionConflict_throws() {
    EvaluationForm form = new EvaluationForm();
    form.setEvaluationFormId(1L);
    form.setVersion(2);
    when(formMapper.selectById(1L)).thenReturn(form);
    when(formMapper.updateById(any(EvaluationForm.class))).thenReturn(0);
    EvaluationFormUpdateRequest req = new EvaluationFormUpdateRequest();
    req.setVersion(1);
    req.setFormName("表单1");
    req.setFormType("COMPOSITE");
    req.setStatus(1);
    req.setDescription("desc");

    assertThatThrownBy(() -> service.update(1L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8504);
  }

  @Test
  @DisplayName("delete：已有发布版本时返回冲突且不删除表单")
  void delete_withPublishedVersion_rejectsWithoutDeleting() {
    EvaluationForm form = new EvaluationForm();
    form.setEvaluationFormId(1L);
    when(formMapper.selectById(1L)).thenReturn(form);
    when(versionMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.delete(1L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8507);

    verify(formMapper, never()).deleteById(anyLong());
  }
}
