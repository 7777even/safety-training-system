package com.sts.question.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.question.dto.*;
import com.sts.question.entity.*;
import com.sts.question.mapper.*;
import com.sts.question.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl
  extends BaseServiceImpl<QuestionMapper, Question>
  implements QuestionService {

  public static final Set<String> QUESTION_TYPES =
    Set.of("SINGLE", "MULTIPLE", "JUDGE", "FILL", "ESSAY");

  private final QuestionMapper questionMapper;
  private final QuestionBankMapper questionBankMapper;
  private final QuestionVersionMapper questionVersionMapper;
  private final QuestionOptionMapper questionOptionMapper;
  private final QuestionBlankAnswerMapper blankAnswerMapper;
  private final QuestionKeywordMapper keywordMapper;
  private final QuestionAssetMapper questionAssetMapper;
  private final QuestionKnowledgeMapper questionKnowledgeMapper;
  private final QuestionUserStateMapper userStateMapper;
  private final CommonImportBatchMapper importBatchMapper;
  private final CommonImportErrorMapper importErrorMapper;

  @Override
  public PageResult<QuestionVO> page(PageRequest<QuestionQuery> request) {
    QuestionQuery query = request.getQuery();
    LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper
        .eq(query.getQuestionBankId() != null, Question::getQuestionBankId, query.getQuestionBankId())
        .like(StringUtils.hasText(query.getQuestionCode()), Question::getQuestionCode, query.getQuestionCode())
        .eq(StringUtils.hasText(query.getArchiveStatus()), Question::getArchiveStatus, query.getArchiveStatus())
        .eq(query.getStatus() != null, Question::getStatus, query.getStatus());
      // 发布状态在版本表上，用子查询筛选，保证分页总数与条件一致
      if (StringUtils.hasText(query.getPublishedStatus())) {
        wrapper.exists("SELECT 1 FROM sts_question_version v WHERE v.question_id = sts_question_question.question_id"
                         + " AND v.published_status = {0}", query.getPublishedStatus());
      }
    }
    wrapper.orderByDesc(Question::getCreateTime);
    Page<Question> page = questionMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(q -> toVO(q, true, false)).toList(), page.getTotal());
  }

  @Override
  public QuestionVO get(Long questionId) {
    return toVO(requireQuestion(questionId), true, true);
  }

  @Override
  @Transactional
  public Long create(QuestionCreateRequest request) {
    assertType(request.getQuestionType());
    requireBank(request.getQuestionBankId());
    Question question = new Question();
    question.setQuestionBankId(request.getQuestionBankId());
    question.setQuestionCode(request.getQuestionCode().trim());
    question.setCurrentVersionNo(1);
    question.setArchiveStatus("ACTIVE");
    question.setStatus(1);
    try {
      questionMapper.insert(question);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 6032, "同机构下题目编码已存在");
    }
    insertVersion(question.getQuestionId(), 1, request.getQuestionType(), request.getStem(),
      request.getAnalysis(), request.getStandardAnswer(), request.getDifficulty(),
      request.getGradingMode(), request.getIsUnorderedAnswerAllowed(),
      request.getIsCaseIgnored(), request.getIsFullHalfWidthIgnored(),
      "DRAFT", null, null, request.getVersionNote(),
      request.getOptions(), request.getBlankAnswers(), request.getKeywords());
    replaceQuestionKnowledge(question.getQuestionId(), request.getKnowledgePointIds());
    return question.getQuestionId();
  }

  @Override
  @Transactional
  public void updateContent(Long questionId, QuestionUpdateRequest request) {
    assertType(request.getQuestionType());
    Question question = requireQuestion(questionId);
    question.setVersion(request.getVersion());
    QuestionVersion current = requireVersion(questionId, question.getCurrentVersionNo());
    if ("PUBLISHED".equals(current.getPublishedStatus())) {
      int nextNo = question.getCurrentVersionNo() + 1;
      insertVersion(questionId, nextNo, request.getQuestionType(), request.getStem(),
        request.getAnalysis(), request.getStandardAnswer(), request.getDifficulty(),
        request.getGradingMode(), request.getIsUnorderedAnswerAllowed(),
        request.getIsCaseIgnored(), request.getIsFullHalfWidthIgnored(),
        "DRAFT", null, null, request.getVersionNote(),
        request.getOptions(), request.getBlankAnswers(), request.getKeywords());
      question.setCurrentVersionNo(nextNo);
      questionMapper.updateById(question);
    } else {
      // draft: replace child rows but keep same version_no (draft still mutable until publish)
      clearVersionChildren(current.getQuestionVersionId());
      current.setQuestionType(request.getQuestionType());
      current.setStem(request.getStem());
      current.setAnalysis(request.getAnalysis());
      current.setStandardAnswer(request.getStandardAnswer());
      current.setDifficulty(request.getDifficulty());
      current.setGradingMode(request.getGradingMode());
      current.setIsUnorderedAnswerAllowed(nz(request.getIsUnorderedAnswerAllowed()));
      current.setIsCaseIgnored(nz(request.getIsCaseIgnored()));
      current.setIsFullHalfWidthIgnored(nz(request.getIsFullHalfWidthIgnored()));
      current.setVersionNote(request.getVersionNote());
      questionVersionMapper.updateById(current);
      insertChildren(current.getQuestionVersionId(), request.getOptions(),
        request.getBlankAnswers(), request.getKeywords());
      questionMapper.updateById(question);
    }
    if (request.getKnowledgePointIds() != null) {
      replaceQuestionKnowledge(questionId, request.getKnowledgePointIds());
    }
  }

  @Override
  @Transactional
  public void publish(Long questionId) {
    Question question = requireQuestion(questionId);
    QuestionVersion current = requireVersion(questionId, question.getCurrentVersionNo());
    if ("PUBLISHED".equals(current.getPublishedStatus())) {
      return;
    }
    validateForPublish(current);
    current.setPublishedStatus("PUBLISHED");
    current.setPublishedBy(StpUtil.getLoginIdAsLong());
    current.setPublishedTime(LocalDateTime.now());
    questionVersionMapper.updateById(current);
  }

  @Override
  @Transactional
  public void delete(Long questionId) {
    Question question = requireQuestion(questionId);
    this.removeById(question.getQuestionId());
  }

  @Override
  @Transactional
  public void replaceAssets(Long questionId, List<QuestionAssetRequest> assets) {
    Question question = requireQuestion(questionId);
    QuestionVersion current = requireVersion(questionId, question.getCurrentVersionNo());
    if ("PUBLISHED".equals(current.getPublishedStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 6035, "已发布版本不可修改素材，请先更新题目生成新版本");
    }
    questionAssetMapper.delete(new LambdaQueryWrapper<QuestionAsset>()
      .eq(QuestionAsset::getQuestionVersionId, current.getQuestionVersionId()));
    if (assets == null) {
      return;
    }
    for (QuestionAssetRequest req : assets) {
      QuestionAsset asset = new QuestionAsset();
      asset.setQuestionVersionId(current.getQuestionVersionId());
      asset.setAssetId(req.getAssetId());
      asset.setAssetRole(req.getAssetRole());
      asset.setOptionKey(req.getOptionKey() == null ? "" : req.getOptionKey());
      asset.setSortOrder(req.getSortOrder());
      questionAssetMapper.insert(asset);
    }
  }

  @Override
  @Transactional
  public QuestionImportResultVO importQuestions(Long questionBankId, QuestionImportRequest request) {
    QuestionBank bank = requireBank(questionBankId);
    CommonImportBatch batch = new CommonImportBatch();
    batch.setBusinessType("QUESTION");
    batch.setBusinessId(questionBankId);
    batch.setSourceFileId(request.getSourceFileId() == null ? 0L : request.getSourceFileId());
    batch.setTemplateType("EXCEL");
    batch.setImportStatus("PROCESSING");
    batch.setTotalRows(request.getRows().size());
    batch.setSuccessRows(0);
    batch.setFailureRows(0);
    batch.setStartedTime(LocalDateTime.now());
    importBatchMapper.insert(batch);

    int success = 0;
    int failure = 0;
    int rowNo = 0;
    for (QuestionImportRowRequest row : request.getRows()) {
      rowNo++;
      try {
        validateImportRow(row);
        QuestionCreateRequest create = new QuestionCreateRequest();
        create.setQuestionBankId(bank.getQuestionBankId());
        create.setQuestionCode(row.getQuestionCode());
        create.setQuestionType(row.getQuestionType());
        create.setStem(row.getStem());
        create.setAnalysis(row.getAnalysis());
        create.setStandardAnswer(row.getStandardAnswer());
        create.setDifficulty(row.getDifficulty());
        create.setGradingMode(StringUtils.hasText(row.getGradingMode()) ? row.getGradingMode() : defaultGrading(row.getQuestionType()));
        create.setOptions(row.getOptions());
        create.setBlankAnswers(row.getBlankAnswers());
        Long qid = create(create);
        publish(qid);
        success++;
      } catch (BusinessException ex) {
        failure++;
        CommonImportError err = new CommonImportError();
        err.setImportBatchId(batch.getImportBatchId());
        err.setRowNo(rowNo);
        err.setFieldName(null);
        err.setErrorCode(String.valueOf(ex.getCode()));
        err.setErrorMessage(ex.getMessage());
        err.setRawDataJson(null);
        importErrorMapper.insert(err);
      } catch (RuntimeException ex) {
        failure++;
        CommonImportError err = new CommonImportError();
        err.setImportBatchId(batch.getImportBatchId());
        err.setRowNo(rowNo);
        err.setErrorCode("IMPORT_ERROR");
        err.setErrorMessage(ex.getMessage() == null ? "导入失败" : ex.getMessage());
        importErrorMapper.insert(err);
      }
    }
    batch.setSuccessRows(success);
    batch.setFailureRows(failure);
    batch.setImportStatus(failure == 0 ? "SUCCEEDED" : (success == 0 ? "FAILED" : "PARTIAL_FAILED"));
    batch.setFinishedTime(LocalDateTime.now());
    importBatchMapper.updateById(batch);

    QuestionImportResultVO vo = new QuestionImportResultVO();
    vo.setImportBatchId(batch.getImportBatchId());
    vo.setImportStatus(batch.getImportStatus());
    vo.setTotalRows(batch.getTotalRows());
    vo.setSuccessRows(success);
    vo.setFailureRows(failure);
    return vo;
  }

  @Override
  @Transactional
  public QuestionUserStateVO upsertUserState(QuestionUserStateUpsertRequest request) {
    requireQuestion(request.getQuestionId());
    QuestionUserState state = userStateMapper.selectOne(new LambdaQueryWrapper<QuestionUserState>()
      .eq(QuestionUserState::getUserId, request.getUserId())
      .eq(QuestionUserState::getQuestionId, request.getQuestionId()));
    LocalDateTime now = LocalDateTime.now();
    if (state == null) {
      state = new QuestionUserState();
      state.setUserId(request.getUserId());
      state.setQuestionId(request.getQuestionId());
      state.setIsFavorite(request.getIsFavorite() == null ? 0 : request.getIsFavorite());
      state.setWrongCount(Boolean.TRUE.equals(request.getWrong()) ? 1 : 0);
      state.setCorrectCount(Boolean.TRUE.equals(request.getCorrect()) ? 1 : 0);
      state.setLastAnswerTime(now);
      if (Boolean.TRUE.equals(request.getWrong())) {
        state.setLastWrongTime(now);
      }
      state.setLastExamAttemptId(request.getLastExamAttemptId());
      userStateMapper.insert(state);
    } else {
      if (request.getIsFavorite() != null) {
        state.setIsFavorite(request.getIsFavorite());
      }
      state.setLastAnswerTime(now);
      if (Boolean.TRUE.equals(request.getWrong())) {
        state.setWrongCount(state.getWrongCount() + 1);
        state.setLastWrongTime(now);
      }
      if (Boolean.TRUE.equals(request.getCorrect())) {
        state.setCorrectCount(state.getCorrectCount() + 1);
      }
      if (request.getLastExamAttemptId() != null) {
        state.setLastExamAttemptId(request.getLastExamAttemptId());
      }
      userStateMapper.updateById(state);
    }
    return toUserStateVO(state);
  }

  private void validateImportRow(QuestionImportRowRequest row) {
    if (!StringUtils.hasText(row.getQuestionCode())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6041, "题目编码不能为空");
    }
    assertType(row.getQuestionType());
    if (!StringUtils.hasText(row.getStem())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6041, "题干不能为空");
    }
    if (!StringUtils.hasText(row.getDifficulty())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6041, "难度不能为空");
    }
  }

  private String defaultGrading(String type) {
    return "ESSAY".equals(type) ? "MANUAL" : "AUTO";
  }

  private void insertVersion(Long questionId, int versionNo, String type, String stem,
                             String analysis, String standardAnswer, String difficulty,
                             String gradingMode, Integer unordered, Integer caseIgnored,
                             Integer fullHalf, String publishedStatus, Long publishedBy,
                             LocalDateTime publishedTime, String versionNote,
                             List<QuestionOptionRequest> options,
                             List<QuestionBlankAnswerRequest> blanks,
                             List<QuestionKeywordRequest> keywords) {
    QuestionVersion version = new QuestionVersion();
    version.setQuestionId(questionId);
    version.setVersionNo(versionNo);
    version.setQuestionType(type);
    version.setStem(stem);
    version.setAnalysis(analysis);
    version.setStandardAnswer(standardAnswer);
    version.setDifficulty(difficulty);
    version.setGradingMode(gradingMode);
    version.setIsUnorderedAnswerAllowed(nz(unordered));
    version.setIsCaseIgnored(nz(caseIgnored));
    version.setIsFullHalfWidthIgnored(nz(fullHalf));
    version.setPublishedStatus(publishedStatus);
    version.setPublishedBy(publishedBy);
    version.setPublishedTime(publishedTime);
    version.setVersionNote(versionNote);
    questionVersionMapper.insert(version);
    insertChildren(version.getQuestionVersionId(), options, blanks, keywords);
  }

  private void insertChildren(Long versionId, List<QuestionOptionRequest> options,
                              List<QuestionBlankAnswerRequest> blanks,
                              List<QuestionKeywordRequest> keywords) {
    if (options != null) {
      for (QuestionOptionRequest opt : options) {
        QuestionOption entity = new QuestionOption();
        entity.setQuestionVersionId(versionId);
        entity.setOptionKey(opt.getOptionKey());
        entity.setOptionContent(opt.getOptionContent());
        entity.setIsCorrect(opt.getIsCorrect());
        entity.setSortOrder(opt.getSortOrder());
        questionOptionMapper.insert(entity);
      }
    }
    if (blanks != null) {
      for (QuestionBlankAnswerRequest blank : blanks) {
        QuestionBlankAnswer entity = new QuestionBlankAnswer();
        entity.setQuestionVersionId(versionId);
        entity.setBlankNo(blank.getBlankNo());
        entity.setAnswerGroupNo(blank.getAnswerGroupNo());
        entity.setAnswerText(blank.getAnswerText());
        entity.setSortOrder(blank.getSortOrder());
        blankAnswerMapper.insert(entity);
      }
    }
    if (keywords != null) {
      for (QuestionKeywordRequest kw : keywords) {
        QuestionKeyword entity = new QuestionKeyword();
        entity.setQuestionVersionId(versionId);
        entity.setKeyword(kw.getKeyword());
        entity.setWeight(kw.getWeight());
        entity.setSortOrder(kw.getSortOrder());
        keywordMapper.insert(entity);
      }
    }
  }

  private void clearVersionChildren(Long versionId) {
    questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
      .eq(QuestionOption::getQuestionVersionId, versionId));
    blankAnswerMapper.delete(new LambdaQueryWrapper<QuestionBlankAnswer>()
      .eq(QuestionBlankAnswer::getQuestionVersionId, versionId));
    keywordMapper.delete(new LambdaQueryWrapper<QuestionKeyword>()
      .eq(QuestionKeyword::getQuestionVersionId, versionId));
  }

  private void validateForPublish(QuestionVersion version) {
    if ("SINGLE".equals(version.getQuestionType()) || "MULTIPLE".equals(version.getQuestionType())
      || "JUDGE".equals(version.getQuestionType())) {
      Long correct = questionOptionMapper.selectCount(new LambdaQueryWrapper<QuestionOption>()
        .eq(QuestionOption::getQuestionVersionId, version.getQuestionVersionId())
        .eq(QuestionOption::getIsCorrect, 1));
      if (correct == null || correct == 0) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 6034, "客观题至少需要一个正确答案");
      }
    }
    if ("FILL".equals(version.getQuestionType())) {
      Long blanks = blankAnswerMapper.selectCount(new LambdaQueryWrapper<QuestionBlankAnswer>()
        .eq(QuestionBlankAnswer::getQuestionVersionId, version.getQuestionVersionId()));
      if (blanks == null || blanks == 0) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 6034, "填空题至少需要一个答案");
      }
    }
  }

  private void replaceQuestionKnowledge(Long questionId, List<Long> knowledgePointIds) {
    questionKnowledgeMapper.delete(new LambdaQueryWrapper<QuestionKnowledge>()
      .eq(QuestionKnowledge::getQuestionId, questionId));
    if (knowledgePointIds == null) {
      return;
    }
    for (Long kpId : knowledgePointIds) {
      QuestionKnowledge link = new QuestionKnowledge();
      link.setQuestionId(questionId);
      link.setKnowledgePointId(kpId);
      questionKnowledgeMapper.insert(link);
    }
  }

  private void assertType(String type) {
    if (!QUESTION_TYPES.contains(type)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 6033, "不支持的题目类型");
    }
  }

  private int nz(Integer v) {
    return v == null ? 0 : v;
  }

  private Question requireQuestion(Long id) {
    Question entity = questionMapper.selectById(id);
    if (entity == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6031, "题目不存在");
    }
    return entity;
  }

  private QuestionBank requireBank(Long id) {
    QuestionBank bank = questionBankMapper.selectById(id);
    if (bank == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6021, "题库不存在");
    }
    return bank;
  }

  private QuestionVersion requireVersion(Long questionId, Integer versionNo) {
    QuestionVersion version = questionVersionMapper.selectOne(new LambdaQueryWrapper<QuestionVersion>()
      .eq(QuestionVersion::getQuestionId, questionId)
      .eq(QuestionVersion::getVersionNo, versionNo));
    if (version == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6031, "题目版本不存在");
    }
    return version;
  }

  /**
   * 装配题目返回对象；列表只带当前版本摘要，详情才加载选项和素材等重字段。
   */
  private QuestionVO toVO(Question question, boolean withVersion, boolean withVersionChildren) {
    QuestionVO vo = new QuestionVO();
    vo.setQuestionId(question.getQuestionId());
    vo.setOrganizationId(question.getOrganizationId());
    vo.setQuestionBankId(question.getQuestionBankId());
    vo.setQuestionCode(question.getQuestionCode());
    vo.setCurrentVersionNo(question.getCurrentVersionNo());
    vo.setArchiveStatus(question.getArchiveStatus());
    vo.setStatus(question.getStatus());
    vo.setVersion(question.getVersion());
    vo.setCreateTime(question.getCreateTime());
    vo.setKnowledgePointIds(questionKnowledgeMapper.selectList(new LambdaQueryWrapper<QuestionKnowledge>()
        .eq(QuestionKnowledge::getQuestionId, question.getQuestionId()))
      .stream().map(QuestionKnowledge::getKnowledgePointId).toList());
    if (withVersion) {
      QuestionVersion version = requireVersion(question.getQuestionId(), question.getCurrentVersionNo());
      vo.setCurrentVersion(toVersionVO(version, withVersionChildren));
    }
    return vo;
  }

  private QuestionVersionVO toVersionVO(QuestionVersion version, boolean withChildren) {
    QuestionVersionVO vo = new QuestionVersionVO();
    vo.setQuestionVersionId(version.getQuestionVersionId());
    vo.setQuestionId(version.getQuestionId());
    vo.setVersionNo(version.getVersionNo());
    vo.setQuestionType(version.getQuestionType());
    vo.setStem(version.getStem());
    vo.setAnalysis(version.getAnalysis());
    vo.setStandardAnswer(version.getStandardAnswer());
    vo.setDifficulty(version.getDifficulty());
    vo.setGradingMode(version.getGradingMode());
    vo.setIsUnorderedAnswerAllowed(version.getIsUnorderedAnswerAllowed());
    vo.setIsCaseIgnored(version.getIsCaseIgnored());
    vo.setIsFullHalfWidthIgnored(version.getIsFullHalfWidthIgnored());
    vo.setPublishedStatus(version.getPublishedStatus());
    vo.setPublishedBy(version.getPublishedBy());
    vo.setPublishedTime(version.getPublishedTime());
    vo.setVersionNote(version.getVersionNote());
    if (!withChildren) {
      return vo;
    }
    vo.setOptions(questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
        .eq(QuestionOption::getQuestionVersionId, version.getQuestionVersionId())
        .orderByAsc(QuestionOption::getSortOrder))
      .stream().map(o -> {
        QuestionOptionVO ov = new QuestionOptionVO();
        ov.setQuestionOptionId(o.getQuestionOptionId());
        ov.setOptionKey(o.getOptionKey());
        ov.setOptionContent(o.getOptionContent());
        ov.setIsCorrect(o.getIsCorrect());
        ov.setSortOrder(o.getSortOrder());
        return ov;
      }).toList());
    vo.setAssets(questionAssetMapper.selectList(new LambdaQueryWrapper<QuestionAsset>()
                                                  .eq(QuestionAsset::getQuestionVersionId,
                                                      version.getQuestionVersionId())
                                                  .orderByAsc(QuestionAsset::getSortOrder))
                   .stream().map(asset -> {
        QuestionAssetVO av = new QuestionAssetVO();
        BeanUtils.copyProperties(asset, av);
        return av;
      }).toList());
    // 填空题标准答案与简答题判分关键词属于版本可编辑内容，详情回显必须一并返回，否则前端无法恢复已配置数据。
    vo.setBlankAnswers(blankAnswerMapper.selectList(new LambdaQueryWrapper<QuestionBlankAnswer>()
        .eq(QuestionBlankAnswer::getQuestionVersionId, version.getQuestionVersionId())
        .orderByAsc(QuestionBlankAnswer::getSortOrder))
      .stream().map(blank -> {
        QuestionBlankAnswerRequest blankItem = new QuestionBlankAnswerRequest();
        blankItem.setBlankNo(blank.getBlankNo());
        blankItem.setAnswerGroupNo(blank.getAnswerGroupNo());
        blankItem.setAnswerText(blank.getAnswerText());
        blankItem.setSortOrder(blank.getSortOrder());
        return blankItem;
      }).toList());
    vo.setKeywords(keywordMapper.selectList(new LambdaQueryWrapper<QuestionKeyword>()
        .eq(QuestionKeyword::getQuestionVersionId, version.getQuestionVersionId())
        .orderByAsc(QuestionKeyword::getSortOrder))
      .stream().map(keyword -> {
        QuestionKeywordRequest keywordItem = new QuestionKeywordRequest();
        keywordItem.setKeyword(keyword.getKeyword());
        keywordItem.setWeight(keyword.getWeight());
        keywordItem.setSortOrder(keyword.getSortOrder());
        return keywordItem;
      }).toList());
    return vo;
  }

  private QuestionUserStateVO toUserStateVO(QuestionUserState state) {
    QuestionUserStateVO vo = new QuestionUserStateVO();
    vo.setUserQuestionStateId(state.getUserQuestionStateId());
    vo.setOrganizationId(state.getOrganizationId());
    vo.setUserId(state.getUserId());
    vo.setQuestionId(state.getQuestionId());
    vo.setIsFavorite(state.getIsFavorite());
    vo.setWrongCount(state.getWrongCount());
    vo.setCorrectCount(state.getCorrectCount());
    vo.setLastAnswerTime(state.getLastAnswerTime());
    vo.setLastWrongTime(state.getLastWrongTime());
    vo.setLastExamAttemptId(state.getLastExamAttemptId());
    vo.setVersion(state.getVersion());
    return vo;
  }
}
