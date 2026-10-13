package com.sts.paper.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.exception.BusinessException;
import com.sts.paper.dto.PaperComposeRequest;
import com.sts.paper.dto.PaperCreateRequest;
import com.sts.paper.dto.PaperDrawRuleRequest;
import com.sts.paper.dto.PaperQuestionItemRequest;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperDrawRule;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperDrawRuleMapper;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.question.entity.Question;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.mapper.QuestionMapper;
import com.sts.paper.vo.PaperVersionVO;
import com.sts.question.mapper.QuestionVersionMapper;
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

class PaperServiceImplTest {

  private PaperMapper paperMapper;
  private PaperVersionMapper paperVersionMapper;
  private PaperVersionQuestionMapper paperVersionQuestionMapper;
  private PaperDrawRuleMapper paperDrawRuleMapper;
  private QuestionMapper questionMapper;
  private QuestionVersionMapper questionVersionMapper;
  private PaperServiceImpl service;

  @BeforeEach
  void setUp() {
    paperMapper = mock(PaperMapper.class);
    paperVersionMapper = mock(PaperVersionMapper.class);
    paperVersionQuestionMapper = mock(PaperVersionQuestionMapper.class);
    paperDrawRuleMapper = mock(PaperDrawRuleMapper.class);
    questionMapper = mock(QuestionMapper.class);
    questionVersionMapper = mock(QuestionVersionMapper.class);
    service = new PaperServiceImpl(paperMapper, paperVersionMapper, paperVersionQuestionMapper,
      paperDrawRuleMapper, questionMapper, questionVersionMapper);
  }

  @Test
  @DisplayName("create：创建 FIXED 试卷")
  void create_ok() {
    when(paperMapper.insert(any(Paper.class))).thenAnswer(inv -> {
      Paper p = inv.getArgument(0);
      p.setPaperId(1L);
      return 1;
    });
    PaperCreateRequest req = new PaperCreateRequest();
    req.setPaperCode("P1");
    req.setPaperName("试卷1");
    req.setPaperType("FIXED");
    req.setStatus(1);
    assertThat(service.create(req)).isEqualTo(1L);
  }

  @Test
  @DisplayName("publish：固化题目快照并计算总分")
  void publish_fixedSnapshot() {
    Paper paper = new Paper();
    paper.setPaperId(1L);
    paper.setPaperType("FIXED");
    paper.setCurrentVersionNo(0);
    when(paperMapper.selectById(1L)).thenReturn(paper);
    when(paperVersionMapper.insert(any(PaperVersion.class))).thenAnswer(inv -> {
      PaperVersion v = inv.getArgument(0);
      v.setPaperVersionId(11L);
      return 1;
    });
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of());

    Question q = new Question();
    q.setQuestionId(5L);
    q.setQuestionBankId(9L);
    when(questionMapper.selectById(5L)).thenReturn(q);
    QuestionVersion qv = new QuestionVersion();
    qv.setQuestionVersionId(50L);
    qv.setQuestionId(5L);
    qv.setQuestionType("SINGLE");
    qv.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectOne(any())).thenReturn(qv);

    PaperComposeRequest compose = new PaperComposeRequest();
    compose.setDurationSeconds(3600);
    compose.setPassRatio(new BigDecimal("0.6000"));
    compose.setQuestionOrderMode("IMPORT");
    compose.setMultiSelectScoring("ALL_OR_NOTHING");
    PaperQuestionItemRequest item = new PaperQuestionItemRequest();
    item.setQuestionId(5L);
    item.setScore(new BigDecimal("10.00"));
    item.setSortOrder(1);
    compose.setQuestions(List.of(item));

    PaperVersionVO vo;
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(77L);
      vo = service.publish(1L, compose);
    }
    assertThat(vo.getTotalScore()).isEqualByComparingTo("10.00");
    assertThat(vo.getPassScore()).isEqualByComparingTo("6.00");
    assertThat(vo.getPublishedStatus()).isEqualTo("PUBLISHED");
    verify(paperVersionQuestionMapper).insert(any(com.sts.paper.entity.PaperVersionQuestion.class));
    ArgumentCaptor<PaperVersion> captor = ArgumentCaptor.forClass(PaperVersion.class);
    verify(paperVersionMapper).insert(captor.capture());
    assertThat(captor.getValue().getPublishedBy()).isEqualTo(77L);
  }

  @Test
  @DisplayName("listVersions：按版本号倒序返回摘要，草稿版本可被发现")
  void listVersions_returnsSummariesDesc() {
    Paper paper = new Paper();
    paper.setPaperId(2L);
    paper.setPaperType("RANDOM");
    paper.setCurrentVersionNo(2);
    when(paperMapper.selectById(2L)).thenReturn(paper);
    PaperVersion published = new PaperVersion();
    published.setPaperVersionId(11L);
    published.setPaperId(2L);
    published.setVersionNo(1);
    published.setPublishedStatus("PUBLISHED");
    PaperVersion draft = new PaperVersion();
    draft.setPaperVersionId(12L);
    draft.setPaperId(2L);
    draft.setVersionNo(2);
    draft.setPublishedStatus("DRAFT");
    when(paperVersionMapper.selectList(any())).thenReturn(List.of(draft, published));

    var versions = service.listVersions(2L);

    assertThat(versions).extracting(v -> v.getPaperVersionId()).containsExactly(12L, 11L);
    assertThat(versions.getFirst().getPublishedStatus()).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("getVersion：版本详情装配抽题规则与随机种子")
  void getVersion_assemblesDrawRulesAndSeed() {
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(12L);
    version.setPaperId(2L);
    version.setVersionNo(1);
    version.setPublishedStatus("DRAFT");
    version.setDrawSettingMode("UNIFIED");
    version.setRandomSeed(20260731L);
    when(paperVersionMapper.selectById(12L)).thenReturn(version);
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of());
    PaperDrawRule rule = new PaperDrawRule();
    rule.setDrawRuleId(70L);
    rule.setPaperVersionId(12L);
    rule.setQuestionBankId(9L);
    rule.setQuestionType("SINGLE");
    rule.setDifficulty("ALL");
    rule.setDrawCount(5);
    rule.setScorePerQuestion(new BigDecimal("2.00"));
    rule.setSortOrder(1);
    when(paperDrawRuleMapper.selectList(any())).thenReturn(List.of(rule));

    PaperVersionVO vo = service.getVersion(12L);

    assertThat(vo.getRandomSeed()).isEqualTo(20260731L);
    assertThat(vo.getDrawRules()).hasSize(1);
    assertThat(vo.getDrawRules().getFirst().getDrawRuleId()).isEqualTo(70L);
    assertThat(vo.getDrawRules().getFirst().getDrawCount()).isEqualTo(5);
    assertThat(vo.getDrawRules().getFirst().getScorePerQuestion()).isEqualByComparingTo("2.00");
  }

  @Test
  @DisplayName("compose：固定卷不记录随机种子")
  void compose_fixedPaper_leavesRandomSeedNull() {
    PaperVersion saved = composeFixedPaper(null);
    assertThat(saved.getRandomSeed()).isNull();
  }

  @Test
  @DisplayName("compose：随机卷落库调用方指定的种子")
  void compose_randomPaper_persistsGivenSeed() {
    PaperVersion saved = composeRandomPaper(20260731L);
    assertThat(saved.getRandomSeed()).isEqualTo(20260731L);
  }

  @Test
  @DisplayName("compose：未指定种子时落库实际使用的种子，保证可重现")
  void compose_randomPaperWithoutSeed_persistsEffectiveSeed() {
    PaperVersion saved = composeRandomPaper(null);
    // 未指定时以 paperId 作为实际种子，必须落库而不是留 null
    assertThat(saved.getRandomSeed()).isEqualTo(2L);
  }

  private PaperVersion composeFixedPaper(Long seed) {
    Paper paper = new Paper();
    paper.setPaperId(1L);
    paper.setPaperType("FIXED");
    paper.setCurrentVersionNo(0);
    when(paperMapper.selectById(1L)).thenReturn(paper);
    when(paperVersionMapper.insert(any(PaperVersion.class))).thenAnswer(inv -> {
      PaperVersion v = inv.getArgument(0);
      v.setPaperVersionId(11L);
      return 1;
    });
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of());
    Question q = new Question();
    q.setQuestionId(5L);
    q.setQuestionBankId(9L);
    when(questionMapper.selectById(5L)).thenReturn(q);
    QuestionVersion qv = new QuestionVersion();
    qv.setQuestionVersionId(50L);
    qv.setQuestionId(5L);
    qv.setQuestionType("SINGLE");
    qv.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectOne(any())).thenReturn(qv);

    PaperComposeRequest compose = new PaperComposeRequest();
    compose.setDurationSeconds(3600);
    compose.setPassRatio(new BigDecimal("0.6000"));
    compose.setQuestionOrderMode("IMPORT");
    compose.setMultiSelectScoring("ALL_OR_NOTHING");
    compose.setRandomSeed(seed);
    PaperQuestionItemRequest item = new PaperQuestionItemRequest();
    item.setQuestionId(5L);
    item.setScore(new BigDecimal("10.00"));
    item.setSortOrder(1);
    compose.setQuestions(List.of(item));

    service.compose(1L, compose);
    ArgumentCaptor<PaperVersion> captor = ArgumentCaptor.forClass(PaperVersion.class);
    verify(paperVersionMapper).insert(captor.capture());
    return captor.getValue();
  }

  private PaperVersion composeRandomPaper(Long seed) {
    Paper paper = new Paper();
    paper.setPaperId(2L);
    paper.setPaperType("RANDOM");
    paper.setCurrentVersionNo(0);
    when(paperMapper.selectById(2L)).thenReturn(paper);
    when(paperVersionMapper.insert(any(PaperVersion.class))).thenAnswer(inv -> {
      PaperVersion v = inv.getArgument(0);
      v.setPaperVersionId(12L);
      return 1;
    });
    when(paperVersionQuestionMapper.selectList(any())).thenReturn(List.of());
    Question q = new Question();
    q.setQuestionId(6L);
    q.setQuestionBankId(9L);
    when(questionMapper.selectList(any())).thenReturn(List.of(q));
    QuestionVersion qv = new QuestionVersion();
    qv.setQuestionVersionId(60L);
    qv.setQuestionId(6L);
    qv.setQuestionType("SINGLE");
    qv.setDifficulty("EASY");
    qv.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectOne(any())).thenReturn(qv);

    PaperComposeRequest compose = new PaperComposeRequest();
    compose.setDurationSeconds(3600);
    compose.setPassRatio(new BigDecimal("0.6000"));
    compose.setQuestionOrderMode("RANDOM");
    compose.setMultiSelectScoring("ALL_OR_NOTHING");
    compose.setRandomSeed(seed);
    PaperDrawRuleRequest rule = new PaperDrawRuleRequest();
    rule.setQuestionBankId(9L);
    rule.setQuestionType("SINGLE");
    rule.setDifficulty("ALL");
    rule.setDrawCount(1);
    rule.setScorePerQuestion(new BigDecimal("10.00"));
    rule.setSortOrder(1);
    compose.setDrawRules(List.of(rule));

    service.compose(2L, compose);
    ArgumentCaptor<PaperVersion> captor = ArgumentCaptor.forClass(PaperVersion.class);
    verify(paperVersionMapper).insert(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("publish：随机卷无规则失败")
  void publish_randomWithoutRules_throws() {
    Paper paper = new Paper();
    paper.setPaperId(2L);
    paper.setPaperType("RANDOM");
    paper.setCurrentVersionNo(0);
    when(paperMapper.selectById(2L)).thenReturn(paper);
    PaperComposeRequest compose = new PaperComposeRequest();
    compose.setDurationSeconds(60);
    compose.setPassRatio(new BigDecimal("0.6"));
    compose.setQuestionOrderMode("RANDOM");
    compose.setMultiSelectScoring("ALL_OR_NOTHING");
    assertThatThrownBy(() -> service.publish(2L, compose))
      .isInstanceOf(BusinessException.class);
  }
}
