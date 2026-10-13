package com.sts.survey.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.survey.dto.*;
import com.sts.survey.entity.*;
import com.sts.survey.mapper.*;
import com.sts.survey.util.SnapshotHashUtil;
import com.sts.survey.vo.SurveyOptionVO;
import com.sts.survey.vo.SurveyPreviewVO;
import com.sts.survey.vo.SurveyQuestionVO;
import com.sts.survey.vo.SurveyVO;
import com.sts.survey.vo.SurveyVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SurveyServiceImpl extends BaseServiceImpl<SurveyMapper, Survey> implements SurveyService {

  private static final Set<String> SURVEY_TYPES = Set.of("TRAINING", "ORGANIZATION_EVALUATION");
  private static final Set<String> QUESTION_TYPES = Set.of("SINGLE", "MULTIPLE", "TEXT");
  private static final Set<String> PREVIEW_CLIENTS = Set.of("PC", "MOBILE");

  private final SurveyMapper surveyMapper;
  private final SurveyVersionMapper surveyVersionMapper;
  private final SurveyQuestionMapper surveyQuestionMapper;
  private final SurveyOptionMapper surveyOptionMapper;
  private final SurveySubItemMapper surveySubItemMapper;
  private final SurveyInstanceMapper surveyInstanceMapper;
  private final ObjectMapper objectMapper;

  @Override
  public PageResult<SurveyVO> page(PageRequest<SurveyQuery> request) {
    SurveyQuery query = request.getQuery();
    LambdaQueryWrapper<Survey> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(StringUtils.hasText(query.getSurveyCode()), Survey::getSurveyCode, query.getSurveyCode())
        .like(StringUtils.hasText(query.getSurveyName()), Survey::getSurveyName, query.getSurveyName())
        .eq(StringUtils.hasText(query.getContentStatus()), Survey::getContentStatus, query.getContentStatus())
        .eq(query.getStatus() != null, Survey::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(Survey::getCreateTime);
    Page<Survey> page = surveyMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toListVO).toList(), page.getTotal());
  }

  @Override
  public SurveyVO get(Long surveyId) {
    return toDetailVO(requireSurvey(surveyId));
  }

  @Override
  @Transactional
  public Long create(SurveyCreateRequest request) {
    String surveyType = requireSurveyType(request.getSurveyType());
    Survey survey = new Survey();
    survey.setSurveyCode(request.getSurveyCode().trim());
    survey.setSurveyName(request.getSurveyName().trim());
    survey.setSurveyType(surveyType);
    survey.setDescription(request.getDescription());
    survey.setIsAnonymous(0);
    survey.setContentStatus("DRAFT");
    survey.setStatus(request.getStatus());
    try {
      surveyMapper.insert(survey);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 9002, "同机构下问卷编码已存在");
    }
    return survey.getSurveyId();
  }

  @Override
  @Transactional
  public void update(Long surveyId, SurveyUpdateRequest request) {
    Survey survey = requireUnlaunched(surveyId);
    survey.setVersion(request.getVersion());
    survey.setSurveyName(request.getSurveyName().trim());
    survey.setDescription(request.getDescription());
    survey.setStatus(request.getStatus());
    surveyMapper.updateById(survey);
  }

  @Override
  @Transactional
  public void delete(Long surveyId) {
    requireSurvey(surveyId);
    if (hasInstances(surveyId)) {
      throw new BusinessException(HttpStatus.CONFLICT, 9013, "已投放问卷不可删除");
    }
    this.removeById(surveyId);
  }

  @Override
  @Transactional
  public void replaceQuestions(Long surveyId, SurveyQuestionsReplaceRequest request) {
    Survey survey = requireUnlaunched(surveyId);
    for (SurveyQuestionRequest question : request.getQuestions()) {
      validateQuestion(question);
    }
    survey.setVersion(request.getVersion());
    surveyMapper.updateById(survey);
    replaceQuestionRows(surveyId, request.getQuestions());
  }

  @Override
  @Transactional
  public Long copy(Long surveyId, SurveyCopyRequest request) {
    Survey source = requireSurvey(surveyId);
    Survey copied = new Survey();
    copied.setSurveyCode(request.getSurveyCode().trim());
    copied.setSurveyName(request.getSurveyName().trim());
    copied.setSurveyType(source.getSurveyType());
    copied.setDescription(source.getDescription());
    copied.setClosingText(source.getClosingText());
    copied.setCoverFileId(source.getCoverFileId());
    copied.setIsAnonymous(0);
    copied.setContentStatus("DRAFT");
    copied.setStatus(1);
    try {
      surveyMapper.insert(copied);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 9002, "同机构下问卷编码已存在");
    }
    List<SurveyQuestionRequest> questions = toQuestionRequests(loadQuestions(surveyId)).stream()
      .filter(question -> QUESTION_TYPES.contains(question.getQuestionType()))
      .toList();
    if (!questions.isEmpty()) {
      replaceQuestionRows(copied.getSurveyId(), questions);
    }
    return copied.getSurveyId();
  }

  @Override
  public SurveyPreviewVO preview(Long surveyId, String client) {
    if (!PREVIEW_CLIENTS.contains(client)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9014, "预览端仅支持 PC 或 MOBILE");
    }
    Survey survey = requireSurvey(surveyId);
    SurveyPreviewVO preview = new SurveyPreviewVO();
    preview.setClient(client);
    preview.setSurveyId(survey.getSurveyId());
    preview.setSurveyName(survey.getSurveyName());
    preview.setSurveyType(survey.getSurveyType());
    preview.setQuestions(loadQuestions(surveyId));
    return preview;
  }

  @Override
  @Transactional
  public SurveyVersionVO publish(Long surveyId, SurveyPublishRequest request) {
    Survey survey = requireSurvey(surveyId);
    List<SurveyQuestion> questions = surveyQuestionMapper.selectList(
      new LambdaQueryWrapper<SurveyQuestion>().eq(SurveyQuestion::getSurveyId, surveyId).orderByAsc(SurveyQuestion::getSortOrder));
    if (questions.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9005, "问卷题目不能为空");
    }

    int nextNo = survey.getCurrentVersionId() == null ? 1 : countVersions(surveyId) + 1;
    Map<String, Object> snapshot = buildSnapshot(survey, questions);
    String json;
    try {
      json = objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 9010, "快照序列化失败");
    }
    String hash = SnapshotHashUtil.sha256Hex(json);

    SurveyVersion version = new SurveyVersion();
    version.setSurveyId(surveyId);
    version.setVersionNo(nextNo);
    version.setContentSnapshotJson(json);
    version.setSnapshotHash(hash);
    version.setVersionNote(request != null ? request.getVersionNote() : null);
    version.setPublishedBy(StpUtil.getLoginIdAsLong());
    version.setPublishedTime(LocalDateTime.now());
    surveyVersionMapper.insert(version);

    survey.setCurrentVersionId(version.getSurveyVersionId());
    survey.setContentStatus("PUBLISHED");
    surveyMapper.updateById(survey);

    SurveyVersionVO vo = new SurveyVersionVO();
    vo.setSurveyVersionId(version.getSurveyVersionId());
    vo.setSurveyId(surveyId);
    vo.setVersionNo(nextNo);
    vo.setSnapshotHash(hash);
    vo.setVersionNote(version.getVersionNote());
    vo.setPublishedTime(version.getPublishedTime());
    return vo;
  }

  private void replaceQuestionRows(Long surveyId, List<SurveyQuestionRequest> questions) {
    List<SurveyQuestion> existing = surveyQuestionMapper.selectList(
      new LambdaQueryWrapper<SurveyQuestion>().eq(SurveyQuestion::getSurveyId, surveyId));
    for (SurveyQuestion q : existing) {
      surveyOptionMapper.delete(new LambdaQueryWrapper<SurveyOption>().eq(SurveyOption::getSurveyQuestionId,
                                                                          q.getSurveyQuestionId()));
      surveySubItemMapper.delete(new LambdaQueryWrapper<SurveySubItem>().eq(SurveySubItem::getSurveyQuestionId,
                                                                            q.getSurveyQuestionId()));
    }
    surveyQuestionMapper.delete(new LambdaQueryWrapper<SurveyQuestion>().eq(SurveyQuestion::getSurveyId, surveyId));

    for (SurveyQuestionRequest qReq : questions) {
      SurveyQuestion question = new SurveyQuestion();
      question.setSurveyId(surveyId);
      question.setQuestionType(qReq.getQuestionType());
      question.setQuestionText(qReq.getQuestionText());
      question.setIsRequired(qReq.getIsRequired());
      question.setIsOtherAllowed(0);
      question.setIsDescriptionAllowed(0);
      question.setSortOrder(qReq.getSortOrder());
      question.setStatus(qReq.getStatus());
      surveyQuestionMapper.insert(question);
      if (!"TEXT".equals(qReq.getQuestionType()) && qReq.getOptions() != null) {
        for (SurveyOptionRequest oReq : qReq.getOptions()) {
          SurveyOption option = new SurveyOption();
          option.setSurveyQuestionId(question.getSurveyQuestionId());
          option.setOptionKey(oReq.getOptionKey());
          option.setOptionContent(oReq.getOptionContent());
          option.setSortOrder(oReq.getSortOrder());
          option.setStatus(oReq.getStatus());
          surveyOptionMapper.insert(option);
        }
      }
    }
  }

  private void validateQuestion(SurveyQuestionRequest question) {
    if (!QUESTION_TYPES.contains(question.getQuestionType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9011, "题目仅支持单选、多选和文本");
    }
    if ("TEXT".equals(question.getQuestionType())) {
      return;
    }
    if (question.getOptions() == null || question.getOptions().size() < 2) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9018, "单选和多选题至少需要两个选项");
    }
  }

  private String requireSurveyType(String surveyType) {
    if (!SURVEY_TYPES.contains(surveyType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9012, "问卷用途仅支持线下培训调查或培训机构评价");
    }
    return surveyType;
  }

  private Survey requireUnlaunched(Long surveyId) {
    Survey survey = requireSurvey(surveyId);
    if (hasInstances(surveyId) || "PUBLISHED".equals(survey.getContentStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 9003, "已投放问卷不可修改");
    }
    return survey;
  }

  private boolean hasInstances(Long surveyId) {
    return surveyInstanceMapper.selectCount(
      new LambdaQueryWrapper<SurveyInstance>().eq(SurveyInstance::getSurveyId, surveyId)) > 0;
  }

  private int countVersions(Long surveyId) {
    return surveyVersionMapper.selectCount(new LambdaQueryWrapper<SurveyVersion>().eq(SurveyVersion::getSurveyId, surveyId)).intValue();
  }

  private Map<String, Object> buildSnapshot(Survey survey, List<SurveyQuestion> questions) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("surveyId", survey.getSurveyId());
    root.put("surveyCode", survey.getSurveyCode());
    root.put("surveyName", survey.getSurveyName());
    root.put("surveyType", survey.getSurveyType());
    List<Map<String, Object>> qList = new ArrayList<>();
    for (SurveyQuestion q : questions) {
      Map<String, Object> qMap = new LinkedHashMap<>();
      qMap.put("surveyQuestionId", q.getSurveyQuestionId());
      qMap.put("questionType", q.getQuestionType());
      qMap.put("questionText", q.getQuestionText());
      qMap.put("isRequired", q.getIsRequired());
      qMap.put("sortOrder", q.getSortOrder());
      List<SurveyOption> options = surveyOptionMapper.selectList(
        new LambdaQueryWrapper<SurveyOption>().eq(SurveyOption::getSurveyQuestionId, q.getSurveyQuestionId()).orderByAsc(SurveyOption::getSortOrder));
      qMap.put("options", options.stream().map(o -> Map.of(
        "surveyOptionId", o.getSurveyOptionId(),
        "optionKey", o.getOptionKey(),
        "optionContent", o.getOptionContent(),
        "sortOrder", o.getSortOrder() == null ? 0 : o.getSortOrder()
      )).toList());
      qList.add(qMap);
    }
    root.put("questions", qList);
    return root;
  }

  private List<SurveyQuestionVO> loadQuestions(Long surveyId) {
    List<SurveyQuestion> questions = surveyQuestionMapper.selectList(
      new LambdaQueryWrapper<SurveyQuestion>().eq(SurveyQuestion::getSurveyId,
                                                  surveyId).orderByAsc(SurveyQuestion::getSortOrder));
    List<SurveyQuestionVO> result = new ArrayList<>();
    for (SurveyQuestion question : questions) {
      result.add(toQuestionVO(question));
    }
    return result;
  }

  private SurveyQuestionVO toQuestionVO(SurveyQuestion question) {
    SurveyQuestionVO vo = new SurveyQuestionVO();
    vo.setSurveyQuestionId(question.getSurveyQuestionId());
    vo.setQuestionType(question.getQuestionType());
    vo.setQuestionText(question.getQuestionText());
    vo.setIsRequired(question.getIsRequired());
    vo.setSortOrder(question.getSortOrder());
    List<SurveyOption> options = surveyOptionMapper.selectList(
      new LambdaQueryWrapper<SurveyOption>().eq(SurveyOption::getSurveyQuestionId, question.getSurveyQuestionId())
        .orderByAsc(SurveyOption::getSortOrder));
    vo.setOptions(options.stream().map(this::toOptionVO).toList());
    return vo;
  }

  private SurveyOptionVO toOptionVO(SurveyOption option) {
    SurveyOptionVO vo = new SurveyOptionVO();
    vo.setSurveyOptionId(option.getSurveyOptionId());
    vo.setOptionKey(option.getOptionKey());
    vo.setOptionContent(option.getOptionContent());
    vo.setSortOrder(option.getSortOrder());
    return vo;
  }

  private List<SurveyQuestionRequest> toQuestionRequests(List<SurveyQuestionVO> questions) {
    List<SurveyQuestionRequest> requests = new ArrayList<>();
    for (SurveyQuestionVO question : questions) {
      SurveyQuestionRequest request = new SurveyQuestionRequest();
      request.setQuestionType(question.getQuestionType());
      request.setQuestionText(question.getQuestionText());
      request.setIsRequired(question.getIsRequired() != null ? question.getIsRequired() : 1);
      request.setSortOrder(question.getSortOrder());
      request.setStatus(1);
      if (question.getOptions() != null) {
        request.setOptions(question.getOptions().stream().map(option -> {
          SurveyOptionRequest optionRequest = new SurveyOptionRequest();
          optionRequest.setOptionKey(option.getOptionKey());
          optionRequest.setOptionContent(option.getOptionContent());
          optionRequest.setSortOrder(option.getSortOrder() != null ? option.getSortOrder() : 0);
          optionRequest.setStatus(1);
          return optionRequest;
        }).toList());
      }
      requests.add(request);
    }
    return requests;
  }

  private Survey requireSurvey(Long surveyId) {
    Survey survey = surveyMapper.selectById(surveyId);
    if (survey == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9001, "问卷不存在");
    }
    return survey;
  }

  private SurveyVO toListVO(Survey survey) {
    SurveyVO vo = new SurveyVO();
    BeanUtils.copyProperties(survey, vo);
    return vo;
  }

  private SurveyVO toDetailVO(Survey survey) {
    SurveyVO vo = toListVO(survey);
    vo.setQuestions(loadQuestions(survey.getSurveyId()));
    return vo;
  }
}
