package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.evaluation.mapper.EvaluationScoreRuleMapper;
import com.sts.evaluation.entity.EvaluationScoreRule;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamPolicy;
import com.sts.exam.entity.ExamReviewer;
import com.sts.exam.mapper.ExamMapper;
import com.sts.exam.mapper.ExamPolicyMapper;
import com.sts.exam.mapper.ExamReviewerMapper;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.AssessmentReviewerService;
import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.question.service.QuestionCandidatePoolService;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.TrainingPublishIssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 培训计划发布预检与资源版本冻结。
 */
@Component
@RequiredArgsConstructor
public class TrainingPlanPublishValidator {

  static final int CODE_STRUCTURE = 8113;
  static final int CODE_COURSE = 8114;
  static final int CODE_EXAM = 8115;
  static final int CODE_QUESTION_BANK = 8116;
  static final int CODE_OFFLINE = 8117;
  static final int CODE_BANK_CONFIG = 8118;
  static final int CODE_HOURS = 8121;
  static final int CODE_ASSESSMENT_MODE = 8123;
  static final int CODE_EXECUTION_CONFIG = 8124;
  static final int CODE_STANDARD = 8125;
  static final int CODE_TEMPLATE = 8126;
  static final int CODE_EVALUATION_ANOMALY = 8127;
  static final int CODE_EVAL_FORM = 8128;
  static final int CODE_TRAINING_NATURE = 8129;
  static final int CODE_REQUIRED_RESOURCE = 8156;
  static final int CODE_CANDIDATE_SHORTAGE = 8166;

  private static final Set<String> PRACTICE_TYPES = Set.of("SINGLE", "MULTIPLE", "JUDGE", "FILL", "ESSAY");
  private static final Set<String> ASSESSMENT_MODES = Set.of("QUIZ", "FORMAL_EXAM");
  private static final Set<String> COMPLETION_RULES = Set.of("ALL_REQUIRED_ITEMS", "ALL_ITEMS");
  private static final Set<String> TRAINING_NATURES = Set.of("INITIAL", "REVIEW");

  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingPlanEvaluationFormMapper evaluationFormBindMapper;
  private final TrainingStandardRuleMapper standardRuleMapper;
  private final TrainingResourceAccessHelper resourceAccessHelper;
  private final EvaluationFormVersionMapper evaluationFormVersionMapper;
  private final EvaluationFormMapper evaluationFormMapper;
  private final EvaluationScoreRuleMapper evaluationScoreRuleMapper;
  private final CourseMapper courseMapper;
  private final CourseVersionMapper courseVersionMapper;
  private final ExamMapper examMapper;
  private final ExamPolicyMapper examPolicyMapper;
  private final QuestionBankMapper questionBankMapper;
  private final QuestionCandidatePoolService candidatePoolService;
  private final TrainingQuestionPoolSnapshotService questionPoolSnapshotService;
  private final AssessmentReviewerService assessmentReviewerService;
  private final AssessmentExecutionService assessmentExecutionService;
  private final ExamReviewerMapper examReviewerMapper;
  private final SysUserMapper sysUserMapper;
  private final TrainingContentConfigCodec configCodec;
  private final ObjectMapper objectMapper;

  public List<TrainingPublishIssueVO> collectIssues(TrainingPlan plan) {
    List<TrainingPublishIssueVO> issues = new ArrayList<>();
    TrainingType type = resourceAccessHelper.requireTypeForUse(plan.getTrainingTypeId());
    TrainingStandard standard = resourceAccessHelper.requireStandardForUse(plan.getTrainingStandardId());
    TrainingTemplate template = resourceAccessHelper.requireTemplateForUse(plan.getSourceTemplateId());
    if (standard != null && !"PUBLISHED".equals(standard.getPublishStatus())) {
      issues.add(issue(CODE_STANDARD, "培训标准未发布或不可用", null));
    }
    if (template != null && !"PUBLISHED".equals(template.getPublishStatus())) {
      issues.add(issue(CODE_TEMPLATE, "来源培训模板未发布或不可用", null));
    }

    List<TrainingPlanStage> stages = listStages(plan.getTrainingPlanId());
    List<TrainingPlanContent> contents = listContents(stages);
    long participantCount = participantMapper.selectCount(new LambdaQueryWrapper<TrainingParticipant>()
                                                            .eq(TrainingParticipant::getTrainingPlanId,
                                                                plan.getTrainingPlanId()));
    boolean hasRequired = contents.stream().anyMatch(c -> Objects.equals(1, c.getIsRequired()));
    if (stages.isEmpty() || !hasRequired || participantCount <= 0) {
      issues.add(issue(CODE_STRUCTURE, "发布前必须包含至少一个阶段、一个必修内容和一个具体参训人", null));
    }

    validateEvaluationForms(plan, issues);
    Map<Long, TrainingPlanStage> stagesById = new HashMap<>();
    stages.forEach(stage -> stagesById.put(stage.getPlanStageId(), stage));
    for (TrainingPlanContent content : contents) {
      validateContent(plan, content, stagesById.get(content.getPlanStageId()), issues);
    }
    validateHours(plan, type, standard, contents, issues);
    validateRequiredResources(standard, contents, issues);
    return issues;
  }

  public void freezeOrThrow(TrainingPlan plan) {
    List<TrainingPublishIssueVO> issues = collectIssues(plan);
    if (!issues.isEmpty()) {
      TrainingPublishIssueVO first = issues.get(0);
      throw new BusinessException(HttpStatus.BAD_REQUEST, first.getCode(), first.getMessage());
    }
    TrainingType type = resourceAccessHelper.requireTypeForUse(plan.getTrainingTypeId());
    TrainingStandard standard = resourceAccessHelper.requireStandardForUse(plan.getTrainingStandardId());
    plan.setApplicableRequiredHours(resolveApplicableRequiredHours(plan, type, standard));
    List<TrainingPlanStage> stages = listStages(plan.getTrainingPlanId());
    for (TrainingPlanContent content : listContents(stages)) {
      if (TrainingArrangedHours.isAssessment(content.getContentType())) {
        content.setRequiredHours(BigDecimal.ZERO);
      }
      freezeContent(plan, content);
      contentMapper.updateById(content);
    }
  }

  private void validateEvaluationForms(TrainingPlan plan, List<TrainingPublishIssueVO> issues) {
    List<TrainingPlanEvaluationForm> binds = evaluationFormBindMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanEvaluationForm>()
        .eq(TrainingPlanEvaluationForm::getTrainingPlanId, plan.getTrainingPlanId()));
    for (TrainingPlanEvaluationForm bind : binds) {
      EvaluationFormVersion version = evaluationFormVersionMapper.selectById(bind.getFormVersionId());
      if (version == null || !"PUBLISHED".equals(version.getPublishedStatus())) {
        issues.add(issue(CODE_EVAL_FORM, "评价表单版本未发布或不存在", null));
        continue;
      }
      EvaluationForm form = evaluationFormMapper.selectById(version.getEvaluationFormId());
      if (form == null || !Objects.equals(plan.getOrganizationId(), form.getOrganizationId())
        || !Objects.equals(1, form.getStatus())) {
        issues.add(issue(CODE_EVAL_FORM, "评价表单不属于当前机构或不可用", null));
        continue;
      }
      if ("COMPOSITE".equals(form.getFormType())) {
        String message = compositeConfigurationIssue(plan, version.getFormVersionId());
        if (message != null) {
          issues.add(issue(CODE_EVAL_FORM, message, null));
        }
      }
    }
  }

  /** 草稿绑定后的服务端最终校验，异常触发事务回滚，不能仅依赖 PC 保存前校验。 */
  public void validateCompositeBindingsOrThrow(TrainingPlan plan) {
    List<TrainingPlanEvaluationForm> binds = evaluationFormBindMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanEvaluationForm>()
        .eq(TrainingPlanEvaluationForm::getTrainingPlanId, plan.getTrainingPlanId()));
    for (TrainingPlanEvaluationForm bind : binds) {
      EvaluationFormVersion version = evaluationFormVersionMapper.selectById(bind.getFormVersionId());
      if (version == null) continue;
      EvaluationForm form = evaluationFormMapper.selectById(version.getEvaluationFormId());
      if (form != null && "COMPOSITE".equals(form.getFormType())) {
        String message = compositeConfigurationIssue(plan, version.getFormVersionId());
        if (message != null) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, CODE_EVAL_FORM, message);
        }
      }
    }
  }

  /** 检查评分表所需类别与计划计分内容的两层权重配置是否闭合。 */
  private String compositeConfigurationIssue(TrainingPlan plan, Long formVersionId) {
    List<TrainingPlanStage> stages = listStages(plan.getTrainingPlanId());
    List<TrainingPlanContent> contents = listContents(stages);
    List<EvaluationScoreRule> rules = evaluationScoreRuleMapper.selectList(
      new LambdaQueryWrapper<EvaluationScoreRule>().eq(EvaluationScoreRule::getFormVersionId, formVersionId));
    if (rules.isEmpty()) return "综合评价表单必须配置计分规则";
    Set<String> sourceTypes = new HashSet<>();
    BigDecimal ruleWeightTotal = BigDecimal.ZERO;
    for (EvaluationScoreRule rule : rules) {
      if (!"WEIGHT".equals(rule.getCalculationType()) || rule.getWeight() == null
        || rule.getWeight().compareTo(BigDecimal.ZERO) <= 0
        || !Set.of("COURSE", "EXAM", "OFFLINE").contains(rule.getSourceType())
        || !sourceTypes.add(rule.getSourceType())) {
        return "综合评价表单规则不合规";
      }
      ruleWeightTotal = ruleWeightTotal.add(rule.getWeight());
      List<TrainingPlanContent> scored = contents.stream()
        .filter(content -> Objects.equals(1, content.getIsScored())
          && Objects.equals(rule.getSourceType(), content.getContentType()))
        .toList();
      if (scored.isEmpty()) return "综合评价表单来源未配置计分内容：" + rule.getSourceType();
      String mode = scored.get(0).getScoreAggregationMode();
      if (!("AVERAGE".equals(mode) || "WEIGHTED_AVERAGE".equals(mode))
        || scored.stream().anyMatch(content -> !Objects.equals(mode, content.getScoreAggregationMode()))) {
        return rule.getSourceType() + "计分内容必须统一配置算术平均或加权平均";
      }
      if ("AVERAGE".equals(mode) && scored.stream().anyMatch(content -> content.getScoreWeight() != null)) {
        return rule.getSourceType() + "算术平均不允许配置内容权重";
      }
      if ("WEIGHTED_AVERAGE".equals(mode)) {
        BigDecimal total = scored.stream().map(TrainingPlanContent::getScoreWeight).filter(Objects::nonNull)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scored.stream().anyMatch(content -> content.getScoreWeight() == null
          || content.getScoreWeight().compareTo(BigDecimal.ZERO) <= 0) || total.compareTo(BigDecimal.ONE) != 0) {
          return rule.getSourceType() + "计分内容权重必须大于0且合计为1";
        }
      }
    }
    return ruleWeightTotal.compareTo(BigDecimal.ONE) == 0 ? null : "综合评价表单规则权重必须合计为1";
  }

  private void validateContent(TrainingPlan plan, TrainingPlanContent content, TrainingPlanStage stage,
                               List<TrainingPublishIssueVO> issues) {
    String type = content.getContentType();
    if ("COURSE".equals(type)) {
      validateCourse(plan, content, issues);
    } else if ("EXAM".equals(type)) {
      validateExam(plan, content, issues);
    } else if ("QUESTION_BANK".equals(type)) {
      validateQuestionBank(plan, content, stage, issues);
    } else if ("OFFLINE".equals(type)) {
      validateOffline(content, issues);
    }
  }

  private void validateCourse(TrainingPlan plan, TrainingPlanContent content,
                              List<TrainingPublishIssueVO> issues) {
    Course course = content.getResourceId() == null ? null : courseMapper.selectById(content.getResourceId());
    if (course == null || !Objects.equals(plan.getOrganizationId(), course.getOrganizationId())
      || !"PUBLISHED".equals(course.getPublishStatus()) || !Objects.equals(1, course.getStatus())
      || course.getCurrentVersionId() == null) {
      issues.add(issue(CODE_COURSE, "课程不属于当前机构、未发布或没有可冻结版本", content));
      return;
    }
    // 已指定的版本必须属于该课程，避免矩阵课程升级后悄悄替换原课件。
    if (content.getResourceVersionId() != null) {
      CourseVersion selected = courseVersionMapper.selectById(content.getResourceVersionId());
      if (selected == null || !Objects.equals(selected.getCourseId(), course.getCourseId())) {
        issues.add(issue(CODE_COURSE, "指定课程发布版本不存在或不属于该课程", content));
        return;
      }
    }
    TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
    if (config == null || config.getCompletionRule() == null
      || !COMPLETION_RULES.contains(config.getCompletionRule())) {
      issues.add(issue(CODE_EXECUTION_CONFIG, "课程内容必须配置 completionRule", content));
    }
  }

  private void validateExam(TrainingPlan plan, TrainingPlanContent content,
                            List<TrainingPublishIssueVO> issues) {
    if (content.getAssessmentMode() == null || !ASSESSMENT_MODES.contains(content.getAssessmentMode())) {
      issues.add(issue(CODE_ASSESSMENT_MODE, "考试内容必须指定 assessmentMode=QUIZ 或 FORMAL_EXAM", content));
    }
    Exam exam = content.getResourceId() == null ? null : examMapper.selectById(content.getResourceId());
    if (exam == null || !Objects.equals(plan.getOrganizationId(), exam.getOrganizationId())
      || !"PUBLISHED".equals(exam.getPublishStatus()) || !Objects.equals(1, exam.getStatus())
      || exam.getPaperVersionId() == null) {
      issues.add(issue(CODE_EXAM, "考试不属于当前机构、未发布或没有可冻结试卷版本", content));
      return;
    }
    if (assessmentReviewerService.paperRequiresManualReview(exam.getPaperVersionId())
      && listValidSourceReviewers(plan.getOrganizationId(), exam.getExamId()).isEmpty()) {
      issues.add(issue(CODE_EXAM, "含非自动判分题目的考试必须配置本机构有效批阅人", content));
    }
  }

  private void validateQuestionBank(TrainingPlan plan, TrainingPlanContent content, TrainingPlanStage stage,
                                    List<TrainingPublishIssueVO> issues) {
    QuestionBank bank = content.getResourceId() == null ? null : questionBankMapper.selectById(content.getResourceId());
    if (bank == null || !Objects.equals(plan.getOrganizationId(), bank.getOrganizationId())
      || !Objects.equals(1, bank.getStatus())
      || (StringUtils.hasText(bank.getArchiveStatus()) && !"ACTIVE".equals(bank.getArchiveStatus()))) {
      issues.add(issue(CODE_QUESTION_BANK, "题库不属于当前机构、未启用或已归档", content));
      return;
    }
    TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
    if (!isQuestionBankConfigComplete(config)) {
      issues.add(issue(CODE_BANK_CONFIG, "题库练习必须配置时长、次数和有效抽题规则", content));
      return;
    }
    List<QuestionDrawEngine.Candidate> candidates = candidatePoolService.loadCandidates(bank.getQuestionBankId());
    if (candidates == null || candidates.isEmpty()) {
      issues.add(issue(CODE_QUESTION_BANK, "题库没有可用于练习的题目", content));
      return;
    }
    List<QuestionDrawEngine.Rule> rules = new ArrayList<>();
    for (int position = 0; position < config.getDrawRules().size(); position++) {
      TrainingQuestionBankDrawRuleRequest rule = config.getDrawRules().get(position);
      int ruleIndex = position + 1;
      rules.add(new QuestionDrawEngine.Rule(ruleIndex, rule.getQuestionType(), rule.getDifficulty(),
                                            rule.getKnowledgePointId(), rule.getDrawCount()));
      boolean hasUnsupported = candidates.stream()
        .anyMatch(candidate -> matches(candidate, rule)
          && candidate.evaluationMode() == QuestionDrawEngine.EvaluationMode.UNSUPPORTED);
      if (hasUnsupported) {
        TrainingPublishIssueVO anomaly = questionRuleIssue(
          CODE_EVALUATION_ANOMALY, "第 " + ruleIndex + " 条规则命中了判定配置不完整的题目", content, stage, bank,
          ruleIndex, rule, null, null);
        anomaly.setSuggestion("请修复该规则候选题的评分模式或答案资产后重新预检");
        issues.add(anomaly);
      }
    }
    QuestionDrawEngine.Validation validation = QuestionDrawEngine.validate(candidates, rules);
    for (QuestionDrawEngine.RuleAvailability availability : validation.rules()) {
      if (availability.sufficient()) {
        continue;
      }
      int position = availability.ruleIndex() - 1;
      TrainingQuestionBankDrawRuleRequest rule = config.getDrawRules().get(position);
      String message = "第 " + availability.ruleIndex() + " 条规则，需要 "
        + availability.requiredCount() + " 题，当前可用 " + availability.availableCount() + " 题";
      TrainingPublishIssueVO shortage = questionRuleIssue(
        CODE_CANDIDATE_SHORTAGE, message, content, stage, bank, availability.ruleIndex(), rule,
        availability.requiredCount(), availability.availableCount());
      shortage.setSuggestion("请增加符合筛选条件的已发布题目，或调整抽题规则与抽取数量");
      issues.add(shortage);
    }
  }

  private boolean isQuestionBankConfigComplete(TrainingContentExecutionConfig config) {
    if (config == null || config.getDurationSeconds() == null || config.getDurationSeconds() <= 0
      || config.getMaxAttempts() == null || config.getMaxAttempts() <= 0
      || CollectionUtils.isEmpty(config.getDrawRules())) {
      return false;
    }
    for (TrainingQuestionBankDrawRuleRequest rule : config.getDrawRules()) {
      if (rule == null || rule.getQuestionType() == null || !PRACTICE_TYPES.contains(rule.getQuestionType())
        || rule.getDrawCount() == null || rule.getDrawCount() <= 0) {
        return false;
      }
    }
    return true;
  }

  private void validateOffline(TrainingPlanContent content, List<TrainingPublishIssueVO> issues) {
    TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
    if (config == null || config.getTeacherUserId() == null
      || !StringUtils.hasText(config.getLocation())
      || config.getStartTime() == null || config.getEndTime() == null
      || config.getPassScore() == null) {
      issues.add(issue(CODE_OFFLINE, "线下内容必须配置教师、地点、时间和及格线", content));
    }
  }

  private void validateHours(TrainingPlan plan, TrainingType type, TrainingStandard standard,
                             List<TrainingPlanContent> contents, List<TrainingPublishIssueVO> issues) {
    BigDecimal typeRequiredHours = typeRequiredHours(plan, type);
    if (typeRequiredHours == null) {
      issues.add(issue(CODE_TRAINING_NATURE, trainingNatureIssueMessage(plan, type), null));
      return;
    }
    List<TrainingStandardRule> rules = standard == null ? List.of() : standardRuleMapper.selectList(
      new LambdaQueryWrapper<TrainingStandardRule>()
        .eq(TrainingStandardRule::getTrainingStandardId, standard.getTrainingStandardId()));
    BigDecimal totalHours = BigDecimal.ZERO;
    BigDecimal onlineHours = BigDecimal.ZERO;
    // 发布只合计课程与线下安排学时；考试/题库不计。不读取学员获得学时。
    for (TrainingPlanContent content : contents) {
      BigDecimal hours = TrainingArrangedHours.contributingHours(content.getContentType(), content.getRequiredHours());
      totalHours = totalHours.add(hours);
      if (TrainingArrangedHours.countsAsOnline(content.getContentType())) {
        onlineHours = onlineHours.add(hours);
      }
    }
    BigDecimal applicableRequiredHours = typeRequiredHours;
    for (TrainingStandardRule rule : rules) {
      if (!Objects.equals(1, rule.getStatus()) || rule.getRequiredValue() == null) {
        continue;
      }
      if ("TOTAL_HOURS".equals(rule.getRuleType())
        && rule.getRequiredValue().compareTo(applicableRequiredHours) > 0) {
        applicableRequiredHours = rule.getRequiredValue();
      }
      if ("ONLINE_RATIO".equals(rule.getRuleType())) {
        if (totalHours.compareTo(BigDecimal.ZERO) <= 0
          || onlineHours.divide(totalHours, 6, RoundingMode.HALF_UP).compareTo(rule.getRequiredValue()) > 0) {
          issues.add(issue(CODE_HOURS, "计划内容线上占比超过培训标准", null));
        }
      }
    }
    if (totalHours.compareTo(applicableRequiredHours) < 0) {
      issues.add(issue(CODE_HOURS,
                       trainingNatureLabel(plan.getTrainingNature()) + "计划要求学时 " + display(totalHours)
                         + "，低于适用最低学时 " + display(applicableRequiredHours), null));
    }
    if (type.getOnlineMaxRatio() != null && totalHours.compareTo(BigDecimal.ZERO) > 0
      && onlineHours.divide(totalHours, 6, RoundingMode.HALF_UP).compareTo(type.getOnlineMaxRatio()) > 0) {
      issues.add(issue(CODE_HOURS, "计划内容线上占比超过培训类型上限", null));
    }
  }

  /**
   * 绑定已发布标准时，启用的指定课程/考试/线下规则必须能在计划内容中找到对应项。
   */
  private void validateRequiredResources(TrainingStandard standard, List<TrainingPlanContent> contents,
                                         List<TrainingPublishIssueVO> issues) {
    if (standard == null) {
      return;
    }
    List<TrainingStandardRule> rules = standardRuleMapper.selectList(
      new LambdaQueryWrapper<TrainingStandardRule>()
        .eq(TrainingStandardRule::getTrainingStandardId, standard.getTrainingStandardId()));
    Set<String> resourceRuleTypes = Set.of("COURSE", "EXAM", "OFFLINE");
    for (TrainingStandardRule rule : rules) {
      if (!Objects.equals(1, rule.getStatus()) || !resourceRuleTypes.contains(rule.getRuleType())) {
        continue;
      }
      boolean matched = contents.stream().anyMatch(content ->
                                                     Objects.equals(rule.getRuleType(), content.getContentType())
                                                       && Objects.equals(rule.getResourceId(),
                                                                         content.getResourceId()));
      if (!matched) {
        issues.add(issue(CODE_REQUIRED_RESOURCE, "计划缺少培训标准指定的课程、考试或线下内容", null));
      }
    }
  }

  private BigDecimal resolveApplicableRequiredHours(TrainingPlan plan, TrainingType type,
                                                    TrainingStandard standard) {
    BigDecimal required = typeRequiredHours(plan, type);
    if (required == null) {
      return null;
    }
    if (standard == null) {
      return required;
    }
    List<TrainingStandardRule> rules = standardRuleMapper.selectList(
      new LambdaQueryWrapper<TrainingStandardRule>()
        .eq(TrainingStandardRule::getTrainingStandardId, standard.getTrainingStandardId()));
    for (TrainingStandardRule rule : rules) {
      if (Objects.equals(1, rule.getStatus()) && "TOTAL_HOURS".equals(rule.getRuleType())
        && rule.getRequiredValue() != null && rule.getRequiredValue().compareTo(required) > 0) {
        required = rule.getRequiredValue();
      }
    }
    return required;
  }

  private BigDecimal typeRequiredHours(TrainingPlan plan, TrainingType type) {
    if (plan == null || type == null || !TRAINING_NATURES.contains(plan.getTrainingNature())) {
      return null;
    }
    BigDecimal required = "INITIAL".equals(plan.getTrainingNature())
      ? type.getInitialHours() : type.getReviewHours();
    return required != null && required.compareTo(BigDecimal.ZERO) > 0 ? required : null;
  }

  private String trainingNatureIssueMessage(TrainingPlan plan, TrainingType type) {
    if (plan == null || !TRAINING_NATURES.contains(plan.getTrainingNature())) {
      return "培训计划必须明确选择初培或复审，历史未标明的计划不能发布";
    }
    return trainingNatureLabel(plan.getTrainingNature()) + "要求学时未配置或不是正数";
  }

  private String trainingNatureLabel(String nature) {
    return "INITIAL".equals(nature) ? "初培" : "REVIEW".equals(nature) ? "复审" : "历史未标明";
  }

  private String display(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private void freezeContent(TrainingPlan plan, TrainingPlanContent content) {
    if ("COURSE".equals(content.getContentType())) {
      freezeCourse(content);
    } else if ("EXAM".equals(content.getContentType())) {
      freezeExam(content);
    } else if ("QUESTION_BANK".equals(content.getContentType())) {
      freezeQuestionBank(plan, content);
    } else if ("OFFLINE".equals(content.getContentType())) {
      freezeOffline(content);
    }
  }

  private void freezeCourse(TrainingPlanContent content) {
    Course course = courseMapper.selectById(content.getResourceId());
    // 显式版本优先，未指定时沿用既有当前版本冻结行为。
    Long versionId = content.getResourceVersionId() == null ? course.getCurrentVersionId() : content.getResourceVersionId();
    CourseVersion version = courseVersionMapper.selectById(versionId);
    content.setResourceVersionId(versionId);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("contentType", "COURSE");
    snapshot.put("resourceId", course.getCourseId());
    snapshot.put("resourceVersionId", versionId);
    snapshot.put("courseCode", course.getCourseCode());
    snapshot.put("courseName", course.getCourseName());
    snapshot.put("courseVersionNo", version == null ? null : version.getVersionNo());
    snapshot.put("completionRule", configCodec.fromJson(content.getExecutionConfigJson()).getCompletionRule());
    writeSnapshot(content, snapshot);
  }

  private void freezeExam(TrainingPlanContent content) {
    Exam exam = examMapper.selectById(content.getResourceId());
    ExamPolicy policy = examPolicyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
                                                     .eq(ExamPolicy::getExamId, exam.getExamId()));
    content.setResourceVersionId(exam.getPaperVersionId());
    TrainingExamResourceSnapshot snapshot = new TrainingExamResourceSnapshot();
    snapshot.setSchemaVersion(2);
    snapshot.setSourceExamId(exam.getExamId());
    snapshot.setExamCode(exam.getExamCode());
    snapshot.setExamName(exam.getExamName());
    snapshot.setPaperVersionId(exam.getPaperVersionId());
    snapshot.setAssessmentMode(content.getAssessmentMode());
    snapshot.setDurationSeconds(exam.getDurationSeconds());
    snapshot.setPassScore(exam.getPassScore());
    snapshot.setPassRatio(exam.getPassRatio());
    snapshot.setQuestionOrderMode(exam.getQuestionOrderMode());
    snapshot.setMultiSelectScoring(exam.getMultiSelectScoring());
    snapshot.setCredit(exam.getCredit());
    snapshot.setExamCategoryId(exam.getExamCategoryId());
    snapshot.setCoverFileId(exam.getCoverFileId());
    snapshot.setResponsibleUserId(exam.getResponsibleUserId());
    snapshot.setIsNoticeEnabled(exam.getIsNoticeEnabled() == null ? 0 : exam.getIsNoticeEnabled());
    snapshot.setNoticeContent(exam.getNoticeContent());
    List<TrainingExamReviewerSnapshot> reviewers = listValidSourceReviewers(exam.getOrganizationId(), exam.getExamId());
    boolean requiresManual = assessmentReviewerService.paperRequiresManualReview(exam.getPaperVersionId());
    snapshot.setRequiresManualReview(requiresManual);
    snapshot.setPolicy(toPolicySnapshot(policy));
    snapshot.setReviewers(reviewers);
    snapshot.normalizeNumericFields();
    writeSnapshot(content, snapshot);
  }

  private TrainingExamPolicySnapshot toPolicySnapshot(ExamPolicy policy) {
    TrainingExamPolicySnapshot snap = new TrainingExamPolicySnapshot();
    if (policy == null) {
      return snap;
    }
    snap.setIsAnticheatEnabled(policy.getIsAnticheatEnabled());
    snap.setIsWatermarkEnabled(policy.getIsWatermarkEnabled());
    snap.setIsPcAllowed(policy.getIsPcAllowed());
    snap.setIsAppAllowed(policy.getIsAppAllowed());
    snap.setIsH5Allowed(policy.getIsH5Allowed());
    snap.setSwitchLimit(policy.getSwitchLimit());
    snap.setIsSplitScreenForceSubmit(policy.getIsSplitScreenForceSubmit());
    snap.setIsCopyRefreshDevtoolDisabled(policy.getIsCopyRefreshDevtoolDisabled());
    snap.setInactivitySeconds(policy.getInactivitySeconds());
    snap.setInactivityCountdownSeconds(policy.getInactivityCountdownSeconds());
    snap.setMinimumAnswerSeconds(policy.getMinimumAnswerSeconds());
    snap.setIsRetakeEnabled(policy.getIsRetakeEnabled());
    snap.setRetakeScope(policy.getRetakeScope());
    snap.setRetakeStartTime(policy.getRetakeStartTime() == null ? null : policy.getRetakeStartTime().toString());
    snap.setRetakeEndTime(policy.getRetakeEndTime() == null ? null : policy.getRetakeEndTime().toString());
    snap.setRetakeMaxCount(policy.getRetakeMaxCount());
    snap.setRetakeScoreMode(policy.getRetakeScoreMode());
    snap.setIsRetakeRecordVisible(policy.getIsRetakeRecordVisible());
    snap.setPassPoint(policy.getPassPoint());
    snap.setFailPoint(policy.getFailPoint());
    snap.setAbsentPoint(policy.getAbsentPoint());
    snap.setLateForbidSeconds(policy.getLateForbidSeconds());
    snap.setAnswerDetailMode(policy.getAnswerDetailMode());
    snap.setIsAnalysisVisible(policy.getIsAnalysisVisible());
    snap.setIsScoreVisible(policy.getIsScoreVisible());
    snap.setIsRankingEnabled(policy.getIsRankingEnabled());
    return snap;
  }

  private List<TrainingExamReviewerSnapshot> listValidSourceReviewers(Long organizationId, Long examId) {
    AssessmentExecution execution = assessmentExecutionService.findStandaloneByExamId(examId);
    if (execution == null) {
      return List.of();
    }
    List<ExamReviewer> rows = examReviewerMapper.selectList(new LambdaQueryWrapper<ExamReviewer>()
                                                              .eq(ExamReviewer::getAssessmentExecutionId,
                                                                  execution.getAssessmentExecutionId()));
    List<TrainingExamReviewerSnapshot> result = new ArrayList<>();
    for (ExamReviewer row : rows) {
      if (row == null || row.getReviewerUserId() == null) {
        continue;
      }
      SysUser user = sysUserMapper.selectById(row.getReviewerUserId());
      if (user == null || !Objects.equals(organizationId, user.getOrganizationId())
        || !Objects.equals(1, user.getStatus())) {
        continue;
      }
      TrainingExamReviewerSnapshot snap = new TrainingExamReviewerSnapshot();
      snap.setReviewerUserId(row.getReviewerUserId());
      snap.setAssignmentSource(row.getAssignmentSource() == null ? "USER" : row.getAssignmentSource());
      result.add(snap);
    }
    result.sort(Comparator.comparing(TrainingExamReviewerSnapshot::getReviewerUserId));
    return result;
  }

  private void freezeQuestionBank(TrainingPlan plan, TrainingPlanContent content) {
    QuestionBank bank = questionBankMapper.selectById(content.getResourceId());
    content.setResourceVersionId(null);
    TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
    List<QuestionDrawEngine.Candidate> candidates = candidatePoolService.loadCandidates(bank.getQuestionBankId());
    List<QuestionDrawEngine.Rule> rules = new ArrayList<>();
    for (int index = 0; index < config.getDrawRules().size(); index++) {
      TrainingQuestionBankDrawRuleRequest rule = config.getDrawRules().get(index);
      rules.add(new QuestionDrawEngine.Rule(index + 1, rule.getQuestionType(), rule.getDifficulty(),
                                            rule.getKnowledgePointId(), rule.getDrawCount()));
      if (candidates.stream().anyMatch(candidate -> matches(candidate, rule)
        && candidate.evaluationMode() == QuestionDrawEngine.EvaluationMode.UNSUPPORTED)) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, CODE_EVALUATION_ANOMALY,
                                    "第 " + (index + 1) + " 条规则命中了判定配置不完整的题目");
      }
    }
    QuestionDrawEngine.Validation validation = QuestionDrawEngine.validate(candidates, rules);
    validation.rules().stream().filter(item -> !item.sufficient()).findFirst().ifPresent(item -> {
      throw new BusinessException(HttpStatus.BAD_REQUEST, CODE_CANDIDATE_SHORTAGE,
                                  "第 " + item.ruleIndex() + " 条规则，需要 " + item.requiredCount()
                                    + " 题，当前可用 " + item.availableCount() + " 题");
    });
    questionPoolSnapshotService.freeze(plan, content, bank, config, candidates);
  }

  private void freezeOffline(TrainingPlanContent content) {
    content.setResourceVersionId(null);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("contentType", "OFFLINE");
    snapshot.put("executionConfig", configCodec.fromJson(content.getExecutionConfigJson()));
    writeSnapshot(content, snapshot);
  }

  private void writeSnapshot(TrainingPlanContent content, Object snapshot) {
    try {
      String json = objectMapper.writeValueAsString(snapshot);
      content.setResourceSnapshotJson(json);
      content.setResourceSnapshotHash(sha256Hex(json));
    } catch (JsonProcessingException ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "快照序列化失败");
    }
  }

  private List<TrainingPlanStage> listStages(Long trainingPlanId) {
    return stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>()
                                    .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId)
                                    .orderByAsc(TrainingPlanStage::getSortOrder));
  }

  private List<TrainingPlanContent> listContents(List<TrainingPlanStage> stages) {
    List<TrainingPlanContent> contents = new ArrayList<>();
    for (TrainingPlanStage stage : stages) {
      contents.addAll(contentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>()
                                                 .eq(TrainingPlanContent::getPlanStageId, stage.getPlanStageId())
                                                 .orderByAsc(TrainingPlanContent::getSortOrder)));
    }
    return contents;
  }

  private TrainingPublishIssueVO issue(int code, String message, TrainingPlanContent content) {
    TrainingPublishIssueVO vo = new TrainingPublishIssueVO();
    vo.setCode(code);
    vo.setMessage(message);
    if (content != null) {
      vo.setPlanContentId(content.getPlanContentId());
      vo.setContentType(content.getContentType());
      vo.setStageId(content.getPlanStageId());
      vo.setContentName(content.getContentName());
      vo.setResourceId(content.getResourceId());
    }
    return vo;
  }

  private TrainingPublishIssueVO questionRuleIssue(int code, String message, TrainingPlanContent content,
                                                   TrainingPlanStage stage, QuestionBank bank, int ruleIndex,
                                                   TrainingQuestionBankDrawRuleRequest rule,
                                                   Integer requiredCount, Integer availableCount) {
    TrainingPublishIssueVO issue = issue(code, message, content);
    issue.setStageName(stage == null ? null : stage.getStageName());
    issue.setResourceName(bank.getBankName());
    issue.setRuleIndex(ruleIndex);
    issue.setQuestionType(rule.getQuestionType());
    issue.setDifficulty(rule.getDifficulty());
    issue.setKnowledgePointId(rule.getKnowledgePointId());
    issue.setRequiredCount(requiredCount);
    issue.setAvailableCount(availableCount);
    return issue;
  }

  private boolean matches(QuestionDrawEngine.Candidate candidate,
                          TrainingQuestionBankDrawRuleRequest rule) {
    if (!Objects.equals(candidate.questionType(), rule.getQuestionType())) {
      return false;
    }
    if (StringUtils.hasText(rule.getDifficulty()) && !"ALL".equals(rule.getDifficulty())
      && !Objects.equals(candidate.difficulty(), rule.getDifficulty())) {
      return false;
    }
    return rule.getKnowledgePointId() == null || rule.getKnowledgePointId() == 0L
      || candidate.knowledgePointIds().contains(rule.getKnowledgePointId());
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "快照哈希计算失败");
    }
  }
}
