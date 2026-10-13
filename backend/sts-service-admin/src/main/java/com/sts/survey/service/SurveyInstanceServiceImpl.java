package com.sts.survey.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.survey.config.SurveyProperties;
import com.sts.survey.dto.SurveyAnswerRequest;
import com.sts.survey.dto.SurveyInstanceCreateRequest;
import com.sts.survey.dto.SurveyInstanceQuery;
import com.sts.survey.dto.SurveySubmitRequest;
import com.sts.survey.entity.*;
import com.sts.survey.mapper.*;
import com.sts.survey.vo.SurveyFillVO;
import com.sts.survey.vo.SurveyInstanceVO;
import com.sts.survey.vo.SurveyOptionCountVO;
import com.sts.survey.vo.SurveyOptionVO;
import com.sts.survey.vo.SurveyQuestionStatsVO;
import com.sts.survey.vo.SurveyQuestionVO;
import com.sts.survey.vo.SurveyStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SurveyInstanceServiceImpl implements SurveyInstanceService {

  private static final Set<String> PURPOSES = Set.of("TRAINING_SURVEY", "ORG_EVALUATION");
  private static final Set<String> VISIBILITIES = Set.of("ADMIN_ONLY", "SUBMITTER_AFTER_SUBMIT");

  private final SurveyMapper surveyMapper;
  private final SurveyInstanceMapper surveyInstanceMapper;
  private final SurveyParticipantMapper surveyParticipantMapper;
  private final SurveyResponseMapper surveyResponseMapper;
  private final SurveyAnswerMapper surveyAnswerMapper;
  private final SurveyAnswerOptionMapper surveyAnswerOptionMapper;
  private final SurveyQuestionMapper surveyQuestionMapper;
  private final SurveyOptionMapper surveyOptionMapper;
  private final SurveyVersionMapper surveyVersionMapper;
  private final SurveyService surveyService;
  private final SurveyProperties surveyProperties;
  private final ObjectMapper objectMapper;

  @Override
  public PageResult<SurveyInstanceVO> page(PageRequest<SurveyInstanceQuery> request) {
    SurveyInstanceQuery query = request.getQuery();
    LambdaQueryWrapper<SurveyInstance> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getSurveyId() != null, SurveyInstance::getSurveyId, query.getSurveyId())
        .eq(query.getInstanceStatus() != null, SurveyInstance::getInstanceStatus, query.getInstanceStatus());
    }
    wrapper.orderByDesc(SurveyInstance::getCreateTime);
    Page<SurveyInstance> page = surveyInstanceMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public SurveyInstanceVO get(Long surveyInstanceId) {
    return toVO(requireInstance(surveyInstanceId));
  }

  @Override
  @Transactional
  public SurveyInstanceVO create(SurveyInstanceCreateRequest request) {
    String purpose = requirePurpose(request.getPurpose());
    String visibility = requireVisibility(request.getResultVisibility());
    Survey survey = surveyMapper.selectById(request.getSurveyId());
    if (survey == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9001, "问卷不存在");
    }
    if (survey.getCurrentVersionId() == null) {
      surveyService.publish(request.getSurveyId(), null);
      survey = surveyMapper.selectById(request.getSurveyId());
    }
    if (survey == null || survey.getCurrentVersionId() == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 9006, "问卷尚未形成投放快照，无法创建投放实例");
    }
    SurveyInstance instance = new SurveyInstance();
    instance.setInstanceCode(request.getInstanceCode().trim());
    instance.setInstanceName(request.getInstanceName().trim());
    instance.setSurveyId(request.getSurveyId());
    instance.setSurveyVersionId(survey.getCurrentVersionId());
    instance.setPurpose(purpose);
    instance.setResultVisibility(visibility);
    instance.setSourceType("ORG_EVALUATION".equals(purpose) ? "ORGANIZATION" : "TRAINING");
    instance.setSourceId(0L);
    instance.setPublishMode("IMMEDIATE");
    instance.setStartTime(request.getStartTime());
    instance.setEndTime(request.getEndTime());
    instance.setIsAnonymousSnapshot(0);
    instance.setInstanceStatus("PUBLISHED");
    instance.setPublishedTime(LocalDateTime.now());
    instance.setStatus(1);
    try {
      surveyInstanceMapper.insert(instance);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 9007, "同机构下实例编码已存在");
    }
    return toVO(instance);
  }

  @Override
  @Transactional
  public void submit(Long surveyInstanceId, SurveySubmitRequest request) {
    SurveyInstance instance = requireInstance(surveyInstanceId);
    Long userId = StpUtil.getLoginIdAsLong();
    SurveyParticipant participant = requireOrCreateParticipant(instance, userId);
    if ("SUBMITTED".equals(participant.getParticipantStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 9008, "该用户已提交答卷，不可重复提交");
    }

    SurveyResponse existing = surveyResponseMapper.selectOne(
      new LambdaQueryWrapper<SurveyResponse>().eq(SurveyResponse::getSurveyParticipantId, participant.getSurveyParticipantId()));
    if (existing != null && "SUBMITTED".equals(existing.getResponseStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 9008, "该用户已提交答卷，不可重复提交");
    }

    SurveyResponse response = existing != null ? existing : new SurveyResponse();
    response.setOrganizationId(instance.getOrganizationId());
    response.setSurveyInstanceId(surveyInstanceId);
    response.setSurveyId(instance.getSurveyId());
    response.setSurveyVersionId(instance.getSurveyVersionId());
    response.setSurveyParticipantId(participant.getSurveyParticipantId());
    response.setUserId(userId);
    response.setIsAnonymous(0);
    response.setResponseStatus("SUBMITTED");
    response.setStartedTime(LocalDateTime.now());
    response.setSubmittedTime(LocalDateTime.now());
    response.setDeviceType(request.getDeviceType());
    if (existing == null) {
      surveyResponseMapper.insert(response);
    } else {
      surveyResponseMapper.updateById(response);
    }

    for (SurveyAnswerRequest aReq : request.getAnswers()) {
      SurveyAnswer answer = new SurveyAnswer();
      answer.setSurveyResponseId(response.getSurveyResponseId());
      answer.setSurveyQuestionId(aReq.getSurveyQuestionId());
      answer.setSubItemNo(0);
      answer.setAnswerText(aReq.getAnswerText());
      surveyAnswerMapper.insert(answer);
      if (aReq.getOptionIds() != null) {
        int sort = 0;
        for (Long optionId : aReq.getOptionIds()) {
          SurveyAnswerOption ao = new SurveyAnswerOption();
          ao.setSurveyAnswerId(answer.getSurveyAnswerId());
          ao.setSurveyOptionId(optionId);
          ao.setSortOrder(sort++);
          surveyAnswerOptionMapper.insert(ao);
        }
      }
    }

    participant.setParticipantStatus("SUBMITTED");
    participant.setSubmittedTime(LocalDateTime.now());
    surveyParticipantMapper.updateById(participant);
  }

  @Override
  public SurveyFillVO getFill(Long surveyInstanceId) {
    SurveyInstance instance = requireInstance(surveyInstanceId);
    Survey survey = surveyMapper.selectById(instance.getSurveyId());
    Long userId = StpUtil.getLoginIdAsLong();
    SurveyParticipant participant = findParticipant(surveyInstanceId, userId);
    SurveyFillVO fill = new SurveyFillVO();
    fill.setSurveyInstanceId(surveyInstanceId);
    fill.setSurveyId(instance.getSurveyId());
    fill.setInstanceName(instance.getInstanceName());
    fill.setSurveyName(survey == null ? instance.getInstanceName() : survey.getSurveyName());
    fill.setPurpose(instance.getPurpose());
    fill.setResultVisibility(instance.getResultVisibility());
    fill.setSubmitted(participant != null && "SUBMITTED".equals(participant.getParticipantStatus()));
    fill.setQuestions(loadFillQuestions(instance));
    return fill;
  }

  @Override
  public SurveyStatsVO stats(Long surveyInstanceId) {
    SurveyInstance instance = requireInstance(surveyInstanceId);
    assertCanReadStats(instance);
    long submitted = surveyResponseMapper.selectCount(
      new LambdaQueryWrapper<SurveyResponse>()
        .eq(SurveyResponse::getSurveyInstanceId, surveyInstanceId)
        .eq(SurveyResponse::getResponseStatus, "SUBMITTED"));

    List<SurveyResponse> responses = surveyResponseMapper.selectList(
      new LambdaQueryWrapper<SurveyResponse>()
        .eq(SurveyResponse::getSurveyInstanceId, surveyInstanceId)
        .eq(SurveyResponse::getResponseStatus, "SUBMITTED"));

    Map<Long, Map<Long, Long>> optionCountsByQuestion = new HashMap<>();
    Map<Long, List<String>> textsByQuestion = new HashMap<>();
    for (SurveyResponse resp : responses) {
      List<SurveyAnswer> answers = surveyAnswerMapper.selectList(
        new LambdaQueryWrapper<SurveyAnswer>().eq(SurveyAnswer::getSurveyResponseId, resp.getSurveyResponseId()));
      for (SurveyAnswer answer : answers) {
        List<SurveyAnswerOption> options = surveyAnswerOptionMapper.selectList(
          new LambdaQueryWrapper<SurveyAnswerOption>().eq(SurveyAnswerOption::getSurveyAnswerId, answer.getSurveyAnswerId()));
        for (SurveyAnswerOption ao : options) {
          optionCountsByQuestion
            .computeIfAbsent(answer.getSurveyQuestionId(), k -> new HashMap<>())
            .merge(ao.getSurveyOptionId(), 1L, Long::sum);
        }
        if (StringUtils.hasText(answer.getAnswerText())) {
          textsByQuestion.computeIfAbsent(answer.getSurveyQuestionId(), k -> new ArrayList<>())
            .add(answer.getAnswerText());
        }
      }
    }

    List<SurveyQuestion> questions = surveyQuestionMapper.selectList(
      new LambdaQueryWrapper<SurveyQuestion>().eq(SurveyQuestion::getSurveyId, instance.getSurveyId())
        .orderByAsc(SurveyQuestion::getSortOrder));
    List<SurveyQuestionStatsVO> questionStats = new ArrayList<>();
    for (SurveyQuestion question : questions) {
      SurveyQuestionStatsVO qs = new SurveyQuestionStatsVO();
      qs.setSurveyQuestionId(question.getSurveyQuestionId());
      qs.setQuestionType(question.getQuestionType());
      qs.setQuestionText(question.getQuestionText());
      if ("TEXT".equals(question.getQuestionType())) {
        qs.setTextAnswers(textsByQuestion.getOrDefault(question.getSurveyQuestionId(), List.of()));
        qs.setOptionCounts(List.of());
      } else {
        List<SurveyOption> options = surveyOptionMapper.selectList(
          new LambdaQueryWrapper<SurveyOption>().eq(SurveyOption::getSurveyQuestionId, question.getSurveyQuestionId())
            .orderByAsc(SurveyOption::getSortOrder));
        Map<Long, Long> counts = optionCountsByQuestion.getOrDefault(question.getSurveyQuestionId(), Map.of());
        List<SurveyOptionCountVO> optionCounts = new ArrayList<>();
        for (SurveyOption option : options) {
          SurveyOptionCountVO countVO = new SurveyOptionCountVO();
          countVO.setSurveyOptionId(option.getSurveyOptionId());
          countVO.setOptionContent(option.getOptionContent());
          countVO.setCount(counts.getOrDefault(option.getSurveyOptionId(), 0L));
          optionCounts.add(countVO);
        }
        qs.setOptionCounts(optionCounts);
        qs.setTextAnswers(List.of());
      }
      questionStats.add(qs);
    }

    SurveyStatsVO stats = new SurveyStatsVO();
    stats.setSurveyInstanceId(surveyInstanceId);
    stats.setTotalSubmitted(submitted);
    stats.setQuestionStats(questionStats);
    return stats;
  }

  private void assertCanReadStats(SurveyInstance instance) {
    if (StpUtil.hasPermission("survey:instance:stats") || StpUtil.hasPermission("survey:instance:list")) {
      return;
    }
    if ("SUBMITTER_AFTER_SUBMIT".equals(instance.getResultVisibility())) {
      SurveyParticipant participant = findParticipant(instance.getSurveyInstanceId(), StpUtil.getLoginIdAsLong());
      if (participant != null && "SUBMITTED".equals(participant.getParticipantStatus())) {
        return;
      }
    }
    throw new BusinessException(HttpStatus.FORBIDDEN, 9017, "无权查看该投放结果统计");
  }

  private SurveyParticipant requireOrCreateParticipant(SurveyInstance instance, Long userId) {
    SurveyParticipant existing = findParticipant(instance.getSurveyInstanceId(), userId);
    if (existing != null) {
      return existing;
    }
    SurveyParticipant participant = new SurveyParticipant();
    participant.setOrganizationId(instance.getOrganizationId());
    participant.setSurveyInstanceId(instance.getSurveyInstanceId());
    participant.setSurveyId(instance.getSurveyId());
    participant.setSurveyVersionId(instance.getSurveyVersionId());
    participant.setUserId(userId);
    participant.setParticipantStatus("PENDING");
    try {
      surveyParticipantMapper.insert(participant);
      return participant;
    } catch (DuplicateKeyException ex) {
      SurveyParticipant raced = findParticipant(instance.getSurveyInstanceId(), userId);
      if (raced == null) {
        throw new BusinessException(HttpStatus.CONFLICT, 9008, "该用户已提交答卷，不可重复提交");
      }
      return raced;
    }
  }

  private SurveyParticipant findParticipant(Long surveyInstanceId, Long userId) {
    return surveyParticipantMapper.selectOne(
      new LambdaQueryWrapper<SurveyParticipant>()
        .eq(SurveyParticipant::getSurveyInstanceId, surveyInstanceId)
        .eq(SurveyParticipant::getUserId, userId));
  }

  private List<SurveyQuestionVO> loadFillQuestions(SurveyInstance instance) {
    SurveyVersion version = surveyVersionMapper.selectById(instance.getSurveyVersionId());
    if (version != null && StringUtils.hasText(version.getContentSnapshotJson())) {
      return questionsFromSnapshot(version.getContentSnapshotJson());
    }
    List<SurveyQuestion> questions = surveyQuestionMapper.selectList(
      new LambdaQueryWrapper<SurveyQuestion>().eq(SurveyQuestion::getSurveyId, instance.getSurveyId())
        .orderByAsc(SurveyQuestion::getSortOrder));
    List<SurveyQuestionVO> result = new ArrayList<>();
    for (SurveyQuestion question : questions) {
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
      result.add(vo);
    }
    return result;
  }

  private List<SurveyQuestionVO> questionsFromSnapshot(String json) {
    try {
      Map<String, Object> root = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
      });
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> qList = (List<Map<String, Object>>) root.get("questions");
      if (qList == null) {
        return List.of();
      }
      List<SurveyQuestionVO> result = new ArrayList<>();
      for (Map<String, Object> qMap : qList) {
        SurveyQuestionVO vo = new SurveyQuestionVO();
        vo.setSurveyQuestionId(asLong(qMap.get("surveyQuestionId")));
        vo.setQuestionType((String) qMap.get("questionType"));
        vo.setQuestionText((String) qMap.get("questionText"));
        vo.setIsRequired(asInteger(qMap.get("isRequired")));
        vo.setSortOrder(asInteger(qMap.get("sortOrder")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) qMap.get("options");
        List<SurveyOptionVO> optionVOs = new ArrayList<>();
        if (options != null) {
          for (Map<String, Object> option : options) {
            SurveyOptionVO optionVO = new SurveyOptionVO();
            optionVO.setSurveyOptionId(asLong(option.get("surveyOptionId")));
            optionVO.setOptionKey((String) option.get("optionKey"));
            optionVO.setOptionContent((String) option.get("optionContent"));
            optionVO.setSortOrder(asInteger(option.get("sortOrder")));
            optionVOs.add(optionVO);
          }
        }
        vo.setOptions(optionVOs);
        result.add(vo);
      }
      return result;
    } catch (JsonProcessingException | ClassCastException ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 9010, "快照序列化失败");
    }
  }

  private SurveyOptionVO toOptionVO(SurveyOption option) {
    SurveyOptionVO vo = new SurveyOptionVO();
    vo.setSurveyOptionId(option.getSurveyOptionId());
    vo.setOptionKey(option.getOptionKey());
    vo.setOptionContent(option.getOptionContent());
    vo.setSortOrder(option.getSortOrder());
    return vo;
  }

  private Long asLong(Object value) {
    return value instanceof Number number ? number.longValue() : null;
  }

  private Integer asInteger(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private String requirePurpose(String purpose) {
    if (!PURPOSES.contains(purpose)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9015, "投放用途仅支持线下培训调查或培训机构评价");
    }
    return purpose;
  }

  private String requireVisibility(String visibility) {
    if (!VISIBILITIES.contains(visibility)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9016, "结果可见方式仅支持仅管理员或提交后填写人可见");
    }
    return visibility;
  }

  private SurveyInstance requireInstance(Long id) {
    SurveyInstance instance = surveyInstanceMapper.selectById(id);
    if (instance == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9009, "问卷投放实例不存在");
    }
    return instance;
  }

  private SurveyInstanceVO toVO(SurveyInstance instance) {
    SurveyInstanceVO vo = new SurveyInstanceVO();
    BeanUtils.copyProperties(instance, vo);
    vo.setFillUrl(buildFillUrl(instance.getSurveyInstanceId()));
    vo.setQrContent(vo.getFillUrl());
    return vo;
  }

  private String buildFillUrl(Long surveyInstanceId) {
    String prefix = surveyProperties.getPublicFillUrlPrefix();
    if (!StringUtils.hasText(prefix)) {
      prefix = "/survey/fill";
    }
    if (prefix.endsWith("/")) {
      prefix = prefix.substring(0, prefix.length() - 1);
    }
    return prefix + "/" + surveyInstanceId;
  }
}
