package com.sts.survey.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.survey.config.SurveyProperties;
import com.sts.survey.dto.SurveyAnswerRequest;
import com.sts.survey.dto.SurveyInstanceCreateRequest;
import com.sts.survey.dto.SurveySubmitRequest;
import com.sts.survey.entity.*;
import com.sts.survey.mapper.*;
import com.sts.survey.vo.SurveyFillVO;
import com.sts.survey.vo.SurveyInstanceVO;
import com.sts.survey.vo.SurveyStatsVO;
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

class SurveyInstanceServiceImplTest {

  private SurveyMapper surveyMapper;
  private SurveyInstanceMapper instanceMapper;
  private SurveyParticipantMapper participantMapper;
  private SurveyResponseMapper responseMapper;
  private SurveyAnswerMapper answerMapper;
  private SurveyAnswerOptionMapper answerOptionMapper;
  private SurveyQuestionMapper questionMapper;
  private SurveyOptionMapper optionMapper;
  private SurveyVersionMapper versionMapper;
  private SurveyService surveyService;
  private SurveyInstanceServiceImpl service;

  @BeforeEach
  void setUp() {
    surveyMapper = mock(SurveyMapper.class);
    instanceMapper = mock(SurveyInstanceMapper.class);
    participantMapper = mock(SurveyParticipantMapper.class);
    responseMapper = mock(SurveyResponseMapper.class);
    answerMapper = mock(SurveyAnswerMapper.class);
    answerOptionMapper = mock(SurveyAnswerOptionMapper.class);
    questionMapper = mock(SurveyQuestionMapper.class);
    optionMapper = mock(SurveyOptionMapper.class);
    versionMapper = mock(SurveyVersionMapper.class);
    surveyService = mock(SurveyService.class);
    service = new SurveyInstanceServiceImpl(surveyMapper, instanceMapper, participantMapper,
                                            responseMapper, answerMapper, answerOptionMapper,
                                            questionMapper, optionMapper, versionMapper,
                                            surveyService, new SurveyProperties(), new ObjectMapper());
  }

  @Test
  @DisplayName("submit：已提交拒绝重复")
  void submit_duplicateRejected() {
    when(instanceMapper.selectById(1L)).thenReturn(publishedInstance());
    SurveyParticipant participant = new SurveyParticipant();
    participant.setSurveyParticipantId(10L);
    participant.setParticipantStatus("SUBMITTED");
    when(participantMapper.selectOne(any())).thenReturn(participant);

    SurveySubmitRequest req = new SurveySubmitRequest();
    req.setAnswers(List.of(new SurveyAnswerRequest()));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
      assertThatThrownBy(() -> service.submit(1L, req))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 9008);
    }
    verify(responseMapper, never()).insert(any(SurveyResponse.class));
  }

  @Test
  @DisplayName("submit：扫码登录用户首次提交时自动登记参与人")
  void submit_unboundParticipant_autoRegisters() {
    when(instanceMapper.selectById(1L)).thenReturn(publishedInstance());
    when(participantMapper.selectOne(any())).thenReturn(null);
    when(participantMapper.insert(any(SurveyParticipant.class))).thenAnswer(inv -> {
      SurveyParticipant participant = inv.getArgument(0);
      participant.setSurveyParticipantId(10L);
      return 1;
    });
    when(responseMapper.selectOne(any())).thenReturn(null);
    when(responseMapper.insert(any(SurveyResponse.class))).thenAnswer(inv -> {
      SurveyResponse response = inv.getArgument(0);
      response.setSurveyResponseId(20L);
      return 1;
    });

    SurveyAnswerRequest answer = new SurveyAnswerRequest();
    answer.setSurveyQuestionId(3L);
    answer.setAnswerText("很好");
    SurveySubmitRequest request = new SurveySubmitRequest();
    request.setAnswers(List.of(answer));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
      service.submit(1L, request);
    }

    ArgumentCaptor<SurveyParticipant> captor = ArgumentCaptor.forClass(SurveyParticipant.class);
    verify(participantMapper).insert(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(100L);
    verify(responseMapper).insert(any(SurveyResponse.class));
  }

  @Test
  @DisplayName("create：未形成快照时由投放内部冻结，并返回填写链接与二维码内容")
  void create_unpublishedSurvey_snapshotsInternally() {
    Survey unpublished = new Survey();
    unpublished.setSurveyId(2L);
    unpublished.setCurrentVersionId(null);
    unpublished.setSurveyName("培训调查");
    Survey published = new Survey();
    published.setSurveyId(2L);
    published.setCurrentVersionId(88L);
    published.setIsAnonymous(0);
    published.setSurveyName("培训调查");
    when(surveyMapper.selectById(2L)).thenReturn(unpublished, published);
    when(instanceMapper.insert(any(SurveyInstance.class))).thenAnswer(inv -> {
      SurveyInstance instance = inv.getArgument(0);
      instance.setSurveyInstanceId(5L);
      return 1;
    });

    SurveyInstanceCreateRequest request = new SurveyInstanceCreateRequest();
    request.setInstanceCode("I1");
    request.setInstanceName("投放1");
    request.setSurveyId(2L);
    request.setPurpose("TRAINING_SURVEY");
    request.setResultVisibility("ADMIN_ONLY");

    SurveyInstanceVO vo = service.create(request);
    verify(surveyService).publish(2L, null);
    assertThat(vo.getSurveyVersionId()).isEqualTo(88L);
    assertThat(vo.getPurpose()).isEqualTo("TRAINING_SURVEY");
    assertThat(vo.getResultVisibility()).isEqualTo("ADMIN_ONLY");
    assertThat(vo.getFillUrl()).isEqualTo("/survey/fill/5");
    assertThat(vo.getQrContent()).isEqualTo("/survey/fill/5");
  }

  @Test
  @DisplayName("stats：仅管理员可见时填写人不得读取")
  void stats_submitterForbiddenWhenAdminOnly() {
    SurveyInstance instance = publishedInstance();
    instance.setResultVisibility("ADMIN_ONLY");
    when(instanceMapper.selectById(1L)).thenReturn(instance);
    SurveyParticipant participant = new SurveyParticipant();
    participant.setParticipantStatus("SUBMITTED");
    when(participantMapper.selectOne(any())).thenReturn(participant);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
      stp.when(() -> StpUtil.hasPermission("survey:instance:stats")).thenReturn(false);
      stp.when(() -> StpUtil.hasPermission("survey:instance:list")).thenReturn(false);
      assertThatThrownBy(() -> service.stats(1L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 9017);
    }
  }

  @Test
  @DisplayName("stats：提交后可见时填写人可读真实计数且不含评分均值")
  void stats_submitterCanReadAfterSubmit() {
    SurveyInstance instance = publishedInstance();
    instance.setResultVisibility("SUBMITTER_AFTER_SUBMIT");
    when(instanceMapper.selectById(1L)).thenReturn(instance);
    SurveyParticipant participant = new SurveyParticipant();
    participant.setParticipantStatus("SUBMITTED");
    when(participantMapper.selectOne(any())).thenReturn(participant);
    when(responseMapper.selectCount(any())).thenReturn(1L);
    SurveyResponse response = new SurveyResponse();
    response.setSurveyResponseId(20L);
    when(responseMapper.selectList(any())).thenReturn(List.of(response));
    SurveyQuestion single = new SurveyQuestion();
    single.setSurveyQuestionId(3L);
    single.setQuestionType("SINGLE");
    single.setQuestionText("满意度");
    single.setSortOrder(1);
    SurveyQuestion text = new SurveyQuestion();
    text.setSurveyQuestionId(4L);
    text.setQuestionType("TEXT");
    text.setQuestionText("建议");
    text.setSortOrder(2);
    when(questionMapper.selectList(any())).thenReturn(List.of(single, text));
    SurveyOption option = new SurveyOption();
    option.setSurveyOptionId(30L);
    option.setOptionContent("满意");
    when(optionMapper.selectList(any())).thenReturn(List.of(option));
    SurveyAnswer choice = new SurveyAnswer();
    choice.setSurveyAnswerId(40L);
    choice.setSurveyQuestionId(3L);
    SurveyAnswer textAnswer = new SurveyAnswer();
    textAnswer.setSurveyAnswerId(41L);
    textAnswer.setSurveyQuestionId(4L);
    textAnswer.setAnswerText("很好");
    when(answerMapper.selectList(any())).thenReturn(List.of(choice, textAnswer));
    SurveyAnswerOption chosen = new SurveyAnswerOption();
    chosen.setSurveyOptionId(30L);
    when(answerOptionMapper.selectList(any())).thenReturn(List.of(chosen), List.of());

    SurveyStatsVO stats;
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
      stp.when(() -> StpUtil.hasPermission("survey:instance:stats")).thenReturn(false);
      stp.when(() -> StpUtil.hasPermission("survey:instance:list")).thenReturn(false);
      stats = service.stats(1L);
    }
    assertThat(stats.getTotalSubmitted()).isEqualTo(1L);
    assertThat(stats.getQuestionStats()).hasSize(2);
    assertThat(stats.getQuestionStats().get(0).getOptionCounts().get(0).getCount()).isEqualTo(1L);
    assertThat(stats.getQuestionStats().get(1).getTextAnswers()).containsExactly("很好");
  }

  @Test
  @DisplayName("getFill：返回快照题目和当前用户提交状态")
  void getFill_returnsSnapshotQuestions() {
    when(instanceMapper.selectById(1L)).thenReturn(publishedInstance());
    Survey survey = new Survey();
    survey.setSurveyId(2L);
    survey.setSurveyName("培训调查");
    when(surveyMapper.selectById(2L)).thenReturn(survey);
    SurveyVersion version = new SurveyVersion();
    version.setContentSnapshotJson("""
                                     {"surveyName":"培训调查","questions":[{"surveyQuestionId":3,"questionType":"TEXT","questionText":"建议","sortOrder":1,"options":[]}]}
                                     """);
    when(versionMapper.selectById(3L)).thenReturn(version);
    when(participantMapper.selectOne(any())).thenReturn(null);

    SurveyFillVO fill;
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
      fill = service.getFill(1L);
    }
    assertThat(fill.getSubmitted()).isFalse();
    assertThat(fill.getQuestions()).hasSize(1);
    assertThat(fill.getQuestions().get(0).getQuestionText()).isEqualTo("建议");
  }

  private SurveyInstance publishedInstance() {
    SurveyInstance instance = new SurveyInstance();
    instance.setSurveyInstanceId(1L);
    instance.setOrganizationId(1L);
    instance.setSurveyId(2L);
    instance.setSurveyVersionId(3L);
    instance.setInstanceName("投放1");
    instance.setPurpose("TRAINING_SURVEY");
    instance.setResultVisibility("ADMIN_ONLY");
    instance.setIsAnonymousSnapshot(0);
    instance.setInstanceStatus("PUBLISHED");
    return instance;
  }
}
