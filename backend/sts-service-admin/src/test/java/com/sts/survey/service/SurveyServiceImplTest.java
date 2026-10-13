package com.sts.survey.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.survey.dto.*;
import com.sts.survey.entity.*;
import com.sts.survey.mapper.*;
import com.sts.survey.vo.SurveyPreviewVO;
import com.sts.survey.vo.SurveyVersionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SurveyServiceImplTest {

  private SurveyMapper surveyMapper;
  private SurveyVersionMapper versionMapper;
  private SurveyQuestionMapper questionMapper;
  private SurveyOptionMapper optionMapper;
  private SurveySubItemMapper subItemMapper;
  private SurveyInstanceMapper instanceMapper;
  private SurveyServiceImpl service;

  @BeforeEach
  void setUp() {
    surveyMapper = mock(SurveyMapper.class);
    versionMapper = mock(SurveyVersionMapper.class);
    questionMapper = mock(SurveyQuestionMapper.class);
    optionMapper = mock(SurveyOptionMapper.class);
    subItemMapper = mock(SurveySubItemMapper.class);
    instanceMapper = mock(SurveyInstanceMapper.class);
    service = new SurveyServiceImpl(surveyMapper, versionMapper, questionMapper, optionMapper, subItemMapper,
                                    instanceMapper, new ObjectMapper());
  }

  @Test
  @DisplayName("publish：创建不可变版本并更新 currentVersionId")
  void publish_createsImmutableVersion() {
    Survey survey = new Survey();
    survey.setSurveyId(1L);
    survey.setSurveyCode("S1");
    survey.setSurveyName("问卷1");
    survey.setSurveyType("TRAINING");
    when(surveyMapper.selectById(1L)).thenReturn(survey);
    SurveyQuestion q = new SurveyQuestion();
    q.setSurveyQuestionId(10L);
    q.setQuestionType("SINGLE");
    q.setQuestionText("Q1");
    q.setSortOrder(1);
    when(questionMapper.selectList(any())).thenReturn(List.of(q));
    when(optionMapper.selectList(any())).thenReturn(List.of());
    when(versionMapper.selectCount(any())).thenReturn(0L);
    when(versionMapper.insert(any(SurveyVersion.class))).thenAnswer(inv -> {
      SurveyVersion v = inv.getArgument(0);
      v.setSurveyVersionId(100L);
      return 1;
    });

    SurveyVersionVO vo;
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(77L);
      vo = service.publish(1L, new SurveyPublishRequest());
    }
    assertThat(vo.getVersionNo()).isEqualTo(1);
    assertThat(vo.getSnapshotHash()).isNotBlank();
    assertThat(survey.getCurrentVersionId()).isEqualTo(100L);
    assertThat(survey.getContentStatus()).isEqualTo("PUBLISHED");
    ArgumentCaptor<SurveyVersion> captor = ArgumentCaptor.forClass(SurveyVersion.class);
    verify(versionMapper).insert(captor.capture());
    assertThat(captor.getValue().getPublishedBy()).isEqualTo(77L);
  }

  @Test
  @DisplayName("publish：无题目失败")
  void publish_emptyQuestions_throws() {
    Survey survey = new Survey();
    survey.setSurveyId(1L);
    when(surveyMapper.selectById(1L)).thenReturn(survey);
    when(questionMapper.selectList(any())).thenReturn(List.of());
    assertThatThrownBy(() -> service.publish(1L, new SurveyPublishRequest())).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("create：拒绝匿名/通用等超范围问卷类型")
  void create_rejectsOutOfScopeType() {
    SurveyCreateRequest request = new SurveyCreateRequest();
    request.setSurveyCode("S-1");
    request.setSurveyName("问卷");
    request.setSurveyType("GENERAL");
    request.setStatus(1);
    assertThatThrownBy(() -> service.create(request))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 9012);
    verify(surveyMapper, never()).insert(any(Survey.class));
  }

  @Test
  @DisplayName("replaceQuestions：拒绝矩阵/附件等超范围题型")
  void replaceQuestions_rejectsAdvancedType() {
    Survey survey = draftSurvey();
    when(surveyMapper.selectById(1L)).thenReturn(survey);
    when(instanceMapper.selectCount(any())).thenReturn(0L);
    SurveyQuestionRequest question = new SurveyQuestionRequest();
    question.setQuestionType("UPLOAD");
    question.setQuestionText("请上传附件");
    question.setIsRequired(1);
    question.setSortOrder(1);
    question.setStatus(1);
    SurveyQuestionsReplaceRequest request = new SurveyQuestionsReplaceRequest();
    request.setVersion(0);
    request.setQuestions(List.of(question));
    assertThatThrownBy(() -> service.replaceQuestions(1L, request))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 9011);
    verify(questionMapper, never()).insert(any(SurveyQuestion.class));
  }

  @Test
  @DisplayName("copy：复制为新草稿并带上题目选项")
  void copy_createsDraftWithQuestions() {
    Survey source = draftSurvey();
    source.setDescription("说明");
    when(surveyMapper.selectById(1L)).thenReturn(source);
    when(surveyMapper.insert(any(Survey.class))).thenAnswer(inv -> {
      Survey created = inv.getArgument(0);
      created.setSurveyId(9L);
      return 1;
    });
    SurveyQuestion question = new SurveyQuestion();
    question.setSurveyQuestionId(10L);
    question.setQuestionType("SINGLE");
    question.setQuestionText("满意度");
    question.setIsRequired(1);
    question.setSortOrder(1);
    question.setStatus(1);
    when(questionMapper.selectList(any())).thenReturn(List.of(question));
    SurveyOption option = new SurveyOption();
    option.setOptionKey("A");
    option.setOptionContent("满意");
    option.setSortOrder(1);
    option.setStatus(1);
    when(optionMapper.selectList(any())).thenReturn(List.of(option));

    SurveyCopyRequest request = new SurveyCopyRequest();
    request.setSurveyCode("S-COPY");
    request.setSurveyName("复制问卷");
    Long copiedId = service.copy(1L, request);

    assertThat(copiedId).isEqualTo(9L);
    ArgumentCaptor<Survey> surveyCaptor = ArgumentCaptor.forClass(Survey.class);
    verify(surveyMapper).insert(surveyCaptor.capture());
    assertThat(surveyCaptor.getValue().getContentStatus()).isEqualTo("DRAFT");
    assertThat(surveyCaptor.getValue().getCurrentVersionId()).isNull();
    assertThat(surveyCaptor.getValue().getIsAnonymous()).isEqualTo(0);
    verify(questionMapper).insert(any(SurveyQuestion.class));
    verify(optionMapper).insert(any(SurveyOption.class));
  }

  @Test
  @DisplayName("preview：按电脑或手机客户端返回同一题目")
  void preview_returnsQuestionsForClient() {
    when(surveyMapper.selectById(1L)).thenReturn(draftSurvey());
    SurveyQuestion question = new SurveyQuestion();
    question.setSurveyQuestionId(10L);
    question.setQuestionType("TEXT");
    question.setQuestionText("建议");
    question.setSortOrder(1);
    when(questionMapper.selectList(any())).thenReturn(List.of(question));
    when(optionMapper.selectList(any())).thenReturn(List.of());

    SurveyPreviewVO preview = service.preview(1L, "MOBILE");
    assertThat(preview.getClient()).isEqualTo("MOBILE");
    assertThat(preview.getQuestions()).hasSize(1);
    assertThat(preview.getQuestions().get(0).getQuestionType()).isEqualTo("TEXT");
  }

  @Test
  @DisplayName("delete：已投放问卷不可删除")
  void delete_launchedSurveyRejected() {
    when(surveyMapper.selectById(1L)).thenReturn(draftSurvey());
    when(instanceMapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.delete(1L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 9013);
  }

  private Survey draftSurvey() {
    Survey survey = new Survey();
    survey.setSurveyId(1L);
    survey.setSurveyCode("S1");
    survey.setSurveyName("问卷1");
    survey.setSurveyType("TRAINING");
    survey.setContentStatus("DRAFT");
    survey.setIsAnonymous(0);
    survey.setStatus(1);
    return survey;
  }
}
