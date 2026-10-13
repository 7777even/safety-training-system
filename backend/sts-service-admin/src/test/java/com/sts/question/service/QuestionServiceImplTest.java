package com.sts.question.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.question.dto.*;
import com.sts.question.entity.Question;
import com.sts.question.entity.QuestionAsset;
import com.sts.question.entity.QuestionBank;
import com.sts.question.entity.QuestionOption;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionServiceImplTest {

  private QuestionMapper questionMapper;
  private QuestionBankMapper questionBankMapper;
  private QuestionVersionMapper questionVersionMapper;
  private QuestionOptionMapper questionOptionMapper;
  private QuestionBlankAnswerMapper blankAnswerMapper;
  private QuestionKeywordMapper keywordMapper;
  private QuestionAssetMapper questionAssetMapper;
  private QuestionKnowledgeMapper questionKnowledgeMapper;
  private QuestionUserStateMapper userStateMapper;
  private CommonImportBatchMapper importBatchMapper;
  private CommonImportErrorMapper importErrorMapper;
  private QuestionServiceImpl service;

  @BeforeEach
  void setUp() {
    questionMapper = mock(QuestionMapper.class);
    questionBankMapper = mock(QuestionBankMapper.class);
    questionVersionMapper = mock(QuestionVersionMapper.class);
    questionOptionMapper = mock(QuestionOptionMapper.class);
    blankAnswerMapper = mock(QuestionBlankAnswerMapper.class);
    keywordMapper = mock(QuestionKeywordMapper.class);
    questionAssetMapper = mock(QuestionAssetMapper.class);
    questionKnowledgeMapper = mock(QuestionKnowledgeMapper.class);
    userStateMapper = mock(QuestionUserStateMapper.class);
    importBatchMapper = mock(CommonImportBatchMapper.class);
    importErrorMapper = mock(CommonImportErrorMapper.class);
    service = new QuestionServiceImpl(
      questionMapper, questionBankMapper, questionVersionMapper, questionOptionMapper,
      blankAnswerMapper, keywordMapper, questionAssetMapper, questionKnowledgeMapper,
      userStateMapper, importBatchMapper, importErrorMapper);

    QuestionBank bank = new QuestionBank();
    bank.setQuestionBankId(10L);
    when(questionBankMapper.selectById(10L)).thenReturn(bank);
  }

  @Test
  @DisplayName("page：题目列表返回当前版本题干和题型")
  void page_returnsCurrentVersionSummary() {
    Question question = new Question();
    question.setQuestionId(100L);
    question.setQuestionBankId(10L);
    question.setQuestionCode("Q001");
    question.setCurrentVersionNo(2);
    IPage<Question> page = new Page<>(1, 10);
    page.setRecords(List.of(question));
    page.setTotal(1);
    when(questionMapper.selectPage(any(), any())).thenReturn((Page<Question>) page);
    QuestionVersion current = new QuestionVersion();
    current.setQuestionVersionId(200L);
    current.setQuestionId(100L);
    current.setVersionNo(2);
    current.setQuestionType("SINGLE");
    current.setStem("进入生产区域前应佩戴什么？");
    current.setDifficulty("EASY");
    current.setGradingMode("AUTO");
    current.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectOne(any())).thenReturn(current);
    when(questionKnowledgeMapper.selectList(any())).thenReturn(List.of());

    PageRequest<QuestionQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(10);
    var result = service.page(request);

    assertThat(result.list()).hasSize(1);
    assertThat(result.list().getFirst().getCurrentVersion()).isNotNull();
    assertThat(result.list().getFirst().getCurrentVersion().getStem()).isEqualTo("进入生产区域前应佩戴什么？");
    assertThat(result.list().getFirst().getCurrentVersion().getQuestionType()).isEqualTo("SINGLE");
    verify(questionOptionMapper, never()).selectList(any());
    verify(questionAssetMapper, never()).selectList(any());
  }

  @Test
  @DisplayName("create：写入题目与 DRAFT 版本")
  void create_insertsDraftVersion() {
    when(questionMapper.insert(any(Question.class))).thenAnswer(inv -> {
      Question q = inv.getArgument(0);
      q.setQuestionId(100L);
      return 1;
    });
    when(questionVersionMapper.insert(any(QuestionVersion.class))).thenAnswer(inv -> {
      QuestionVersion v = inv.getArgument(0);
      v.setQuestionVersionId(200L);
      return 1;
    });

    QuestionCreateRequest req = new QuestionCreateRequest();
    req.setQuestionBankId(10L);
    req.setQuestionCode("Q001");
    req.setQuestionType("SINGLE");
    req.setStem("1+1=?");
    req.setDifficulty("EASY");
    req.setGradingMode("AUTO");
    QuestionOptionRequest opt = new QuestionOptionRequest();
    opt.setOptionKey("A");
    opt.setOptionContent("2");
    opt.setIsCorrect(1);
    opt.setSortOrder(1);
    req.setOptions(List.of(opt));

    Long id = service.create(req);
    assertThat(id).isEqualTo(100L);
    ArgumentCaptor<QuestionVersion> cap = ArgumentCaptor.forClass(QuestionVersion.class);
    verify(questionVersionMapper).insert(cap.capture());
    assertThat(cap.getValue().getVersionNo()).isEqualTo(1);
    assertThat(cap.getValue().getPublishedStatus()).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("updateContent：已发布版本再改内容时递增 version_no")
  void updateContent_published_createsNewVersion() {
    Question question = new Question();
    question.setQuestionId(100L);
    question.setCurrentVersionNo(1);
    question.setVersion(0);
    when(questionMapper.selectById(100L)).thenReturn(question);

    QuestionVersion published = new QuestionVersion();
    published.setQuestionVersionId(200L);
    published.setQuestionId(100L);
    published.setVersionNo(1);
    published.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectOne(any())).thenReturn(published);
    when(questionVersionMapper.insert(any(QuestionVersion.class))).thenAnswer(inv -> {
      QuestionVersion v = inv.getArgument(0);
      v.setQuestionVersionId(201L);
      return 1;
    });

    QuestionUpdateRequest req = new QuestionUpdateRequest();
    req.setQuestionType("SINGLE");
    req.setStem("new stem");
    req.setDifficulty("EASY");
    req.setGradingMode("AUTO");
    req.setVersion(0);
    QuestionOptionRequest opt = new QuestionOptionRequest();
    opt.setOptionKey("A");
    opt.setOptionContent("2");
    opt.setIsCorrect(1);
    opt.setSortOrder(1);
    req.setOptions(List.of(opt));

    service.updateContent(100L, req);
    ArgumentCaptor<QuestionVersion> cap = ArgumentCaptor.forClass(QuestionVersion.class);
    verify(questionVersionMapper).insert(cap.capture());
    assertThat(cap.getValue().getVersionNo()).isEqualTo(2);
    assertThat(cap.getValue().getPublishedStatus()).isEqualTo("DRAFT");
    verify(questionVersionMapper, never()).updateById(published);
  }

  @Test
  @DisplayName("publish：客观题无正确答案时失败")
  void publish_noCorrectOption_throws() {
    Question question = new Question();
    question.setQuestionId(100L);
    question.setCurrentVersionNo(1);
    when(questionMapper.selectById(100L)).thenReturn(question);
    QuestionVersion draft = new QuestionVersion();
    draft.setQuestionVersionId(200L);
    draft.setQuestionId(100L);
    draft.setVersionNo(1);
    draft.setQuestionType("SINGLE");
    draft.setPublishedStatus("DRAFT");
    when(questionVersionMapper.selectOne(any())).thenReturn(draft);
    when(questionOptionMapper.selectCount(any())).thenReturn(0L);

    assertThatThrownBy(() -> service.publish(100L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(be.getCode()).isEqualTo(6034);
      });
  }

  @Test
  @DisplayName("get：当前版本回显关联素材")
  void get_returnsVersionAssets() {
    Question question = new Question();
    question.setQuestionId(100L);
    question.setQuestionBankId(10L);
    question.setCurrentVersionNo(1);
    when(questionMapper.selectById(100L)).thenReturn(question);
    QuestionVersion version = new QuestionVersion();
    version.setQuestionVersionId(200L);
    version.setQuestionId(100L);
    version.setVersionNo(1);
    version.setQuestionType("SINGLE");
    version.setPublishedStatus("PUBLISHED");
    when(questionVersionMapper.selectOne(any())).thenReturn(version);
    when(questionOptionMapper.selectList(any())).thenReturn(List.of());
    when(blankAnswerMapper.selectList(any())).thenReturn(List.of());
    when(keywordMapper.selectList(any())).thenReturn(List.of());
    when(questionKnowledgeMapper.selectList(any())).thenReturn(List.of());
    QuestionAsset asset = new QuestionAsset();
    asset.setQuestionAssetId(300L);
    asset.setQuestionVersionId(200L);
    asset.setAssetId(777L);
    asset.setAssetRole("STEM");
    asset.setOptionKey("A");
    asset.setSortOrder(1);
    when(questionAssetMapper.selectList(any())).thenReturn(List.of(asset));

    var vo = service.get(100L);

    assertThat(vo.getCurrentVersion().getAssets()).hasSize(1);
    assertThat(vo.getCurrentVersion().getAssets().getFirst().getAssetId()).isEqualTo(777L);
    assertThat(vo.getCurrentVersion().getAssets().getFirst().getAssetRole()).isEqualTo("STEM");
    assertThat(vo.getCurrentVersion().getAssets().getFirst().getOptionKey()).isEqualTo("A");
  }

  @Test
  @DisplayName("publish：发布人取自登录态，不写死 0")
  void publish_derivesPublishedByFromLoginState() {
    Question question = new Question();
    question.setQuestionId(100L);
    question.setCurrentVersionNo(1);
    when(questionMapper.selectById(100L)).thenReturn(question);
    QuestionVersion draft = new QuestionVersion();
    draft.setQuestionVersionId(200L);
    draft.setQuestionId(100L);
    draft.setVersionNo(1);
    draft.setQuestionType("SINGLE");
    draft.setPublishedStatus("DRAFT");
    when(questionVersionMapper.selectOne(any())).thenReturn(draft);
    when(questionOptionMapper.selectCount(any())).thenReturn(1L);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(77L);
      service.publish(100L);
    }

    assertThat(draft.getPublishedBy()).isEqualTo(77L);
  }

  @Test
  @DisplayName("upsertUserState：答错累加 wrongCount")
  void upsertUserState_wrongIncrements() {
    Question q = new Question();
    q.setQuestionId(9L);
    when(questionMapper.selectById(9L)).thenReturn(q);
    when(userStateMapper.selectOne(any())).thenReturn(null);
    when(userStateMapper.insert(any(com.sts.question.entity.QuestionUserState.class))).thenAnswer(inv -> {
      com.sts.question.entity.QuestionUserState s = inv.getArgument(0);
      s.setUserQuestionStateId(1L);
      s.setVersion(0);
      return 1;
    });

    QuestionUserStateUpsertRequest req = new QuestionUserStateUpsertRequest();
    req.setUserId(2L);
    req.setQuestionId(9L);
    req.setWrong(true);
    var vo = service.upsertUserState(req);
    assertThat(vo.getWrongCount()).isEqualTo(1);
  }
}
