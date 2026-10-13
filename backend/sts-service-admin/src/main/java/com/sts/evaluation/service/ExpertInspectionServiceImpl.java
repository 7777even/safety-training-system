package com.sts.evaluation.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.exception.BusinessException;
import com.sts.evaluation.dto.EvaluationRecordItemRequest;
import com.sts.evaluation.dto.ExpertInspectionSubmitRequest;
import com.sts.evaluation.entity.*;
import com.sts.evaluation.mapper.*;
import com.sts.evaluation.vo.EvaluationRecordVO;
import com.sts.evaluation.vo.ExpertInspectionSummaryVO;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanEvaluationForm;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanEvaluationFormMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 专家检查与综合成绩分离：只写一份已提交的课程现场检查记录。 */
@Service
@RequiredArgsConstructor
public class ExpertInspectionServiceImpl implements ExpertInspectionService {
  private static final String TARGET_TYPE = "TRAINING_PLAN_CONTENT";
  private final TrainingPlanMapper trainingPlanMapper;
  private final TrainingPlanContentMapper trainingPlanContentMapper;
  private final TrainingPlanEvaluationFormMapper trainingPlanEvaluationFormMapper;
  private final EvaluationFormVersionMapper evaluationFormVersionMapper;
  private final EvaluationFormMapper evaluationFormMapper;
  private final EvaluationItemMapper evaluationItemMapper;
  private final EvaluationRecordMapper evaluationRecordMapper;
  private final EvaluationRecordItemMapper evaluationRecordItemMapper;
  private final EvaluationRecordService evaluationRecordService;
  private final SysUserMapper sysUserMapper;

  /** 提交前在同一事务内完成计划、绑定、答案和唯一性校验。 */
  @Override
  @Transactional
  public EvaluationRecordVO submit(ExpertInspectionSubmitRequest request) {
    StpUtil.checkPermission("evaluation:record:submit");
    TrainingPlan plan = requireRunningPlan(request.getTrainingPlanId());
    TrainingPlanContent content = requireCourseContent(plan.getTrainingPlanId(), request.getPlanContentId());
    EvaluationFormVersion version = requireBoundExpertVersion(plan, request.getFormVersionId());
    List<EvaluationItem> formItems = evaluationItemMapper.selectList(new LambdaQueryWrapper<EvaluationItem>()
      .eq(EvaluationItem::getFormVersionId, version.getFormVersionId()).orderByAsc(EvaluationItem::getSortOrder));
    Map<Long, EvaluationRecordItemRequest> answers = validateAnswers(formItems, request.getItems());
    EvaluationRecord record = new EvaluationRecord();
    record.setFormVersionId(version.getFormVersionId());
    record.setTargetType(TARGET_TYPE);
    record.setTargetId(content.getPlanContentId());
    record.setEvaluatorUserId(StpUtil.getLoginIdAsLong());
    record.setRecordStatus("SUBMITTED");
    record.setSubmittedTime(LocalDateTime.now());
    record.setSummary(request.getSummary());
    record.setTotalScore(formItems.stream().filter(item -> "SCORE".equals(item.getItemType()))
      .map(item -> answers.get(item.getEvaluationItemId()).getScore()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    try {
      evaluationRecordMapper.insert(record);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8703, "该课程已提交此专家查课表");
    }
    for (EvaluationItem item : formItems) {
      EvaluationRecordItemRequest answer = answers.get(item.getEvaluationItemId());
      EvaluationRecordItem recordItem = new EvaluationRecordItem();
      recordItem.setEvaluationRecordId(record.getEvaluationRecordId());
      recordItem.setEvaluationItemId(item.getEvaluationItemId());
      recordItem.setScore(answer.getScore());
      recordItem.setTextValue(answer.getTextValue());
      evaluationRecordItemMapper.insert(recordItem);
    }
    return evaluationRecordService.get(record.getEvaluationRecordId());
  }

  /** 只返回课程内容以及当前专家检查绑定版本的提交摘要。 */
  @Override
  public List<ExpertInspectionSummaryVO> listByPlan(Long trainingPlanId) {
    TrainingPlan plan = trainingPlanMapper.selectById(trainingPlanId);
    if (plan == null) throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    TrainingPlanEvaluationForm binding = trainingPlanEvaluationFormMapper.selectOne(new LambdaQueryWrapper<TrainingPlanEvaluationForm>()
      .eq(TrainingPlanEvaluationForm::getTrainingPlanId, trainingPlanId).eq(TrainingPlanEvaluationForm::getUsageType, "EXPERT_INSPECTION")
      .eq(TrainingPlanEvaluationForm::getStatus, 1));
    if (binding == null) return List.of();
    EvaluationFormVersion version = evaluationFormVersionMapper.selectById(binding.getFormVersionId());
    Map<Long, EvaluationRecord> records = new HashMap<>();
    for (EvaluationRecord record : evaluationRecordMapper.selectList(new LambdaQueryWrapper<EvaluationRecord>()
      .eq(EvaluationRecord::getFormVersionId, binding.getFormVersionId()).eq(EvaluationRecord::getTargetType, TARGET_TYPE)
      .eq(EvaluationRecord::getRecordStatus, "SUBMITTED"))) records.put(record.getTargetId(), record);
    return trainingPlanContentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>()
      .eq(TrainingPlanContent::getContentType, "COURSE").inSql(TrainingPlanContent::getPlanStageId,
        "select plan_stage_id from sts_training_plan_stage where training_plan_id = " + trainingPlanId + " and deleted = 0")
      .orderByAsc(TrainingPlanContent::getSortOrder)).stream().map(content -> toSummary(content, binding, version, records.get(content.getPlanContentId()))).toList();
  }

  /** 对齐专家检查冻结项目，防止请求携带错项、漏项或跨类型字段。 */
  private Map<Long, EvaluationRecordItemRequest> validateAnswers(List<EvaluationItem> items, List<EvaluationRecordItemRequest> requests) {
    Map<Long, EvaluationRecordItemRequest> answers = new HashMap<>();
    for (EvaluationRecordItemRequest answer : requests) {
      if (answer.getEvaluationItemId() == null || answers.put(answer.getEvaluationItemId(), answer) != null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "专家查课项目答案重复或无效");
      }
    }
    BigDecimal maxTotal = BigDecimal.ZERO;
    for (EvaluationItem item : items) {
      EvaluationRecordItemRequest answer = answers.remove(item.getEvaluationItemId());
      if (answer == null) {
        if (Integer.valueOf(1).equals(item.getIsRequired())) throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "请填写所有必填检查项目");
        continue;
      }
      if ("SCORE".equals(item.getItemType())) {
        if (answer.getTextValue() != null || answer.getOptionValue() != null
          || (Integer.valueOf(1).equals(item.getIsRequired()) && answer.getScore() == null)
          || (answer.getScore() != null && (answer.getScore().compareTo(BigDecimal.ZERO) < 0 || answer.getScore().compareTo(item.getMaxScore()) > 0))) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "分数项必须填写满分范围内的分数");
        }
        maxTotal = maxTotal.add(item.getMaxScore());
      } else if ("TEXT".equals(item.getItemType())) {
        if (answer.getScore() != null || answer.getOptionValue() != null || (Integer.valueOf(1).equals(item.getIsRequired()) && !StringUtils.hasText(answer.getTextValue()))) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "文本意见项只能填写意见内容");
        }
      } else {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "专家检查表只支持分数项和文本意见项");
      }
    }
    if (!answers.isEmpty() || maxTotal.compareTo(BigDecimal.valueOf(100)) != 0) throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "专家检查项目配置或答案不符合100分规则");
    return requests.stream().collect(java.util.stream.Collectors.toMap(EvaluationRecordItemRequest::getEvaluationItemId, item -> item));
  }

  private TrainingPlan requireRunningPlan(Long trainingPlanId) { TrainingPlan plan = trainingPlanMapper.selectById(trainingPlanId); if (plan == null) throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在"); if (!"RUNNING".equals(plan.getPublishStatus())) throw new BusinessException(HttpStatus.CONFLICT, 8704, "仅执行中的培训计划可提交专家查课"); return plan; }
  private TrainingPlanContent requireCourseContent(Long planId, Long contentId) { TrainingPlanContent content = trainingPlanContentMapper.selectById(contentId); if (content == null || !"COURSE".equals(content.getContentType()) || !belongsToPlan(content, planId)) throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "查课对象必须是当前计划内的课程内容"); return content; }
  private boolean belongsToPlan(TrainingPlanContent content, Long planId) { return trainingPlanContentMapper.selectCount(new LambdaQueryWrapper<TrainingPlanContent>().eq(TrainingPlanContent::getPlanContentId, content.getPlanContentId()).inSql(TrainingPlanContent::getPlanStageId, "select plan_stage_id from sts_training_plan_stage where training_plan_id = " + planId + " and deleted = 0")) > 0; }
  private EvaluationFormVersion requireBoundExpertVersion(TrainingPlan plan, Long versionId) { EvaluationFormVersion version = evaluationFormVersionMapper.selectById(versionId); EvaluationForm form = version == null ? null : evaluationFormMapper.selectById(version.getEvaluationFormId()); TrainingPlanEvaluationForm binding = trainingPlanEvaluationFormMapper.selectOne(new LambdaQueryWrapper<TrainingPlanEvaluationForm>().eq(TrainingPlanEvaluationForm::getTrainingPlanId, plan.getTrainingPlanId()).eq(TrainingPlanEvaluationForm::getFormVersionId, versionId).eq(TrainingPlanEvaluationForm::getUsageType, "EXPERT_INSPECTION").eq(TrainingPlanEvaluationForm::getStatus, 1)); if (version == null || !"PUBLISHED".equals(version.getPublishedStatus()) || form == null || !"EXPERT_INSPECTION".equals(form.getFormType()) || !Integer.valueOf(1).equals(form.getStatus()) || binding == null) throw new BusinessException(HttpStatus.BAD_REQUEST, 8704, "专家检查表未发布、未启用或未绑定当前计划"); return version; }
  private ExpertInspectionSummaryVO toSummary(TrainingPlanContent content, TrainingPlanEvaluationForm binding, EvaluationFormVersion version, EvaluationRecord record) { ExpertInspectionSummaryVO vo = new ExpertInspectionSummaryVO(); vo.setPlanContentId(content.getPlanContentId()); vo.setContentName(content.getContentName()); vo.setFormVersionId(binding.getFormVersionId()); vo.setVersionNo(version == null ? null : version.getVersionNo()); if (record != null) { vo.setEvaluationRecordId(record.getEvaluationRecordId()); vo.setRecordStatus(record.getRecordStatus()); vo.setEvaluatorUserId(record.getEvaluatorUserId()); SysUser user = record.getEvaluatorUserId() == null ? null : sysUserMapper.selectById(record.getEvaluatorUserId()); vo.setEvaluatorName(user == null ? null : user.getUserName()); vo.setSubmittedTime(record.getSubmittedTime()); vo.setTotalScore(record.getTotalScore()); vo.setSummary(record.getSummary()); } return vo; }
}
