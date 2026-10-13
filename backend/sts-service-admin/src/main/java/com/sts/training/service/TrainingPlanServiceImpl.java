package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import com.sts.regulation.service.PlanHoursWarningRefreshService;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingPlanServiceImpl
  extends BaseServiceImpl<TrainingPlanMapper, TrainingPlan>
  implements TrainingPlanService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("COURSE", "EXAM", "QUESTION_BANK", "OFFLINE");

  private final TrainingPlanMapper trainingPlanMapper;
  private final TrainingPlanStageMapper trainingPlanStageMapper;
  private final TrainingPlanContentMapper trainingPlanContentMapper;
  private final TrainingTemplateStageMapper trainingTemplateStageMapper;
  private final TrainingTemplateContentMapper trainingTemplateContentMapper;
  private final TrainingPlanEvaluationFormMapper trainingPlanEvaluationFormMapper;
  private final TrainingParticipantMapper trainingParticipantMapper;
  private final TrainingStandardRuleMapper trainingStandardRuleMapper;
  private final EvaluationFormVersionMapper evaluationFormVersionMapper;
  private final EvaluationFormMapper evaluationFormMapper;
  private final PersonSelectionService personSelectionService;
  private final TrainingResourceAccessHelper resourceAccessHelper;
  private final ObjectMapper objectMapper;
  private final TrainingPlanPublishValidator publishValidator;
  private final TrainingContentConfigCodec configCodec;
  private final TrainingExecutionService trainingExecutionService;
  private final TrainingPlanLifecycleService lifecycleService;
  private final PlanHoursWarningRefreshService planHoursWarningRefreshService;
  /** 当前版本审批门禁。 */ private final TrainingPlanApprovalService approvalService;

  @Override
  public PageResult<TrainingPlanVO> page(PageRequest<TrainingPlanQuery> request) {
    TrainingPlanQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingPlan> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(StringUtils.hasText(query.getPlanCode()), TrainingPlan::getPlanCode, query.getPlanCode())
        .like(StringUtils.hasText(query.getPlanName()), TrainingPlan::getPlanName, query.getPlanName())
        .eq(query.getTrainingTypeId() != null, TrainingPlan::getTrainingTypeId, query.getTrainingTypeId())
        .eq(StringUtils.hasText(query.getTrainingNature()), TrainingPlan::getTrainingNature, query.getTrainingNature())
        .eq(StringUtils.hasText(query.getPublishStatus()), TrainingPlan::getPublishStatus, query.getPublishStatus())
        .eq(query.getStatus() != null, TrainingPlan::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(TrainingPlan::getCreateTime);
    Page<TrainingPlan> page = trainingPlanMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingPlanVO get(Long trainingPlanId) {
    TrainingPlanVO vo = toVO(requirePlan(trainingPlanId));
    vo.setStages(listStages(trainingPlanId));
    vo.setEvaluationForms(listEvaluationForms(trainingPlanId));
    return vo;
  }

  @Override
  @Transactional
  public Long create(TrainingPlanCreateRequest request) {
    validateResourcesForPlan(request.getTrainingTypeId(),
      request.getTrainingStandardId(), request.getSourceTemplateId());
    TrainingTemplate sourceTemplate = null;
    if (request.getSourceTemplateId() != null) {
      sourceTemplate = requirePublishedTemplateForCopy(request.getSourceTemplateId(),
                                                       request.getTrainingTypeId());
    }
    TrainingPlan plan = new TrainingPlan();
    plan.setPlanCode(request.getPlanCode().trim());
    plan.setPlanName(request.getPlanName().trim());
    plan.setTrainingTypeId(request.getTrainingTypeId());
    plan.setTrainingNature(request.getTrainingNature());
    plan.setTrainingStandardId(request.getTrainingStandardId());
    plan.setSourceTemplateId(request.getSourceTemplateId());
    applySourceOwner(plan);
    plan.setManagerUserId(request.getManagerUserId());
    plan.setStartTime(request.getStartTime());
    plan.setEndTime(request.getEndTime());
    plan.setLocation(request.getLocation());
    plan.setDescription(request.getDescription());
    plan.setPublishStatus("DRAFT");
    plan.setStatus(request.getStatus());
    try {
      trainingPlanMapper.insert(plan);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8102, "同机构下计划编码已存在");
    }
    if (sourceTemplate != null) {
      copyTemplateSyllabus(plan.getTrainingPlanId(), sourceTemplate.getTrainingTemplateId());
    }
    planHoursWarningRefreshService.refresh(plan.getTrainingPlanId());
    return plan.getTrainingPlanId();
  }

  @Override
  @Transactional
  public void update(Long trainingPlanId, TrainingPlanUpdateRequest request) {
    TrainingPlan plan = requireDraft(trainingPlanId);
    validateResourcesForPlan(request.getTrainingTypeId(),
      request.getTrainingStandardId(), request.getSourceTemplateId());
    plan.setVersion(request.getVersion());
    plan.setPlanName(request.getPlanName().trim());
    plan.setTrainingTypeId(request.getTrainingTypeId());
    plan.setTrainingNature(request.getTrainingNature());
    plan.setTrainingStandardId(request.getTrainingStandardId());
    plan.setSourceTemplateId(request.getSourceTemplateId());
    applySourceOwner(plan);
    plan.setManagerUserId(request.getManagerUserId());
    plan.setStartTime(request.getStartTime());
    plan.setEndTime(request.getEndTime());
    plan.setLocation(request.getLocation());
    plan.setDescription(request.getDescription());
    plan.setStatus(request.getStatus());
    if (trainingPlanMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
    planHoursWarningRefreshService.refresh(trainingPlanId);
  }

  @Override
  @Transactional
  public void delete(Long trainingPlanId) {
    requireDraft(trainingPlanId);
    deleteStagesAndContents(trainingPlanId);
    softDeleteEvaluationForms(trainingPlanId);
    this.removeById(trainingPlanId);
    planHoursWarningRefreshService.refresh(trainingPlanId);
  }

  @Override
  @Transactional
  public void applyTemplate(Long trainingPlanId, TrainingPlanApplyTemplateRequest request) {
    TrainingPlan plan = approvalService.lockForEdit(trainingPlanId);
    if (!"DRAFT".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8158, "仅草稿计划可从模板复制课表");
    }
    requirePublishedTemplateForCopy(request.getSourceTemplateId(), plan.getTrainingTypeId());
    plan.setVersion(request.getVersion());
    plan.setSourceTemplateId(request.getSourceTemplateId());
    applySourceOwner(plan);
    if (trainingPlanMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
    deleteStagesAndContents(trainingPlanId);
    copyTemplateSyllabus(trainingPlanId, request.getSourceTemplateId());
    planHoursWarningRefreshService.refresh(trainingPlanId);
  }

  @Override
  @Transactional
  public void replaceStages(Long trainingPlanId, TrainingPlanStagesReplaceRequest request) {
    TrainingPlan plan = requireDraft(trainingPlanId);
    if (request.getStages() != null) {
      for (TrainingPlanStageRequest stageReq : request.getStages()) {
        if (stageReq.getContents() == null) {
          continue;
        }
        for (TrainingPlanContentRequest contentReq : stageReq.getContents()) {
          validateContentType(contentReq.getContentType());
          TrainingArrangedHours.requireForSave(contentReq.getContentType(), contentReq.getRequiredHours());
        }
      }
    }
    plan.setVersion(request.getVersion());
    if (trainingPlanMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
    deleteStagesAndContents(trainingPlanId);
    if (request.getStages() != null) {
      for (TrainingPlanStageRequest stageReq : request.getStages()) {
        TrainingPlanStage stage = new TrainingPlanStage();
        stage.setTrainingPlanId(trainingPlanId);
        stage.setSourceTemplateStageId(stageReq.getSourceTemplateStageId());
        stage.setStageName(stageReq.getStageName());
        stage.setStageType(stageReq.getStageType());
        stage.setStartTime(stageReq.getStartTime());
        stage.setEndTime(stageReq.getEndTime());
        stage.setLocation(stageReq.getLocation());
        stage.setRequiredHours(stageReq.getRequiredHours());
        stage.setSortOrder(stageReq.getSortOrder());
        stage.setStageStatus(stageReq.getStageStatus() == null ? "PENDING" : stageReq.getStageStatus());
        trainingPlanStageMapper.insert(stage);
        if (stageReq.getContents() != null) {
          for (TrainingPlanContentRequest contentReq : stageReq.getContents()) {
            TrainingPlanContent content = new TrainingPlanContent();
            content.setPlanStageId(stage.getPlanStageId());
            content.setContentType(contentReq.getContentType());
            content.setResourceId(contentReq.getResourceId());
            content.setResourceVersionId(contentReq.getResourceVersionId());
            content.setAssessmentMode(contentReq.getAssessmentMode());
            content.setExecutionConfigJson(configCodec.toJson(contentReq.getExecutionConfig()));
            content.setContentName(contentReq.getContentName());
            content.setIsRequired(contentReq.getIsRequired());
            content.setIsScored(contentReq.getIsScored() == null ? 0 : contentReq.getIsScored());
            content.setScoreWeight(contentReq.getScoreWeight());
            content.setScoreAggregationMode(contentReq.getScoreAggregationMode());
            content.setRequiredHours(TrainingArrangedHours.requireForSave(
              contentReq.getContentType(), contentReq.getRequiredHours()));
            content.setSortOrder(contentReq.getSortOrder());
            content.setContentStatus(contentReq.getContentStatus() == null ? "PENDING" : contentReq.getContentStatus());
            trainingPlanContentMapper.insert(content);
          }
        }
      }
    }
    planHoursWarningRefreshService.refresh(trainingPlanId);
  }

  @Override
  @Transactional
  public void bindEvaluationForms(Long trainingPlanId, TrainingPlanEvaluationFormBindRequest request) {
    TrainingPlan plan = requireDraft(trainingPlanId);
    List<TrainingPlanEvaluationFormItemRequest> forms =
      request.getForms() == null ? List.of() : request.getForms();
    assertUniqueUsageTypes(forms);
    for (TrainingPlanEvaluationFormItemRequest item : forms) {
      EvaluationFormVersion version = evaluationFormVersionMapper.selectById(item.getFormVersionId());
      if (version == null || !"PUBLISHED".equals(version.getPublishedStatus())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8110, "评价表单版本未发布或不存在");
      }
      EvaluationForm form = evaluationFormMapper.selectById(version.getEvaluationFormId());
      if (form == null || !Objects.equals(plan.getOrganizationId(), form.getOrganizationId())
        || !Objects.equals(1, form.getStatus())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8134, "评价表单不属于当前机构或不可用");
      }
      if (("COMPOSITE".equals(item.getUsageType()) && !"COMPOSITE".equals(form.getFormType()))
        || ("EXPERT_INSPECTION".equals(item.getUsageType()) && !"EXPERT_INSPECTION".equals(form.getFormType()))) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8135, "评价表单类型与绑定用途不匹配");
      }
    }
    plan.setVersion(request.getVersion());
    if (trainingPlanMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
    softDeleteEvaluationForms(trainingPlanId);
    try {
      for (TrainingPlanEvaluationFormItemRequest item : forms) {
        TrainingPlanEvaluationForm bind = new TrainingPlanEvaluationForm();
        bind.setTrainingPlanId(trainingPlanId);
        bind.setFormVersionId(item.getFormVersionId());
        bind.setUsageType(item.getUsageType());
        bind.setIsRequired(item.getIsRequired());
        bind.setStatus(item.getStatus());
        trainingPlanEvaluationFormMapper.insert(bind);
      }
      publishValidator.validateCompositeBindingsOrThrow(plan);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8133, "同一评价用途只能绑定一个表单");
    }
  }

  /**
   * 同一请求内每个评价用途只能出现一次。
   */
  private void assertUniqueUsageTypes(List<TrainingPlanEvaluationFormItemRequest> forms) {
    Set<String> usageTypes = new HashSet<>();
    for (TrainingPlanEvaluationFormItemRequest item : forms) {
      if (!usageTypes.add(item.getUsageType())) {
        throw new BusinessException(HttpStatus.CONFLICT, 8133, "同一评价用途只能绑定一个表单");
      }
    }
  }

  @Override
  @Transactional
  public void publish(Long trainingPlanId, Integer version) {
    TrainingPlan plan = approvalService.lock(trainingPlanId);
    if (!"DRAFT".equals(plan.getPublishStatus())) throw new BusinessException(HttpStatus.CONFLICT,8103,"仅草稿计划可发布");
    if (!Objects.equals(version, plan.getVersion())) throw new BusinessException(HttpStatus.CONFLICT,8104,"培训计划版本冲突");
    approvalService.requireApproved(plan);
    plan.setVersion(version);
    freezeAndPublish(plan);
  }

  /** 发布与已批准完整变更共用同一资源校验/冻结逻辑，只有发布入口检查首次审批。 */
  private void freezeAndPublish(TrainingPlan plan) {
    Long trainingPlanId = plan.getTrainingPlanId();
    publishValidator.freezeOrThrow(plan);
    TrainingType type = resourceAccessHelper.requireTypeForUse(plan.getTrainingTypeId());
    TrainingStandard standard = resourceAccessHelper.requireStandardForUse(plan.getTrainingStandardId());
    TrainingTemplate template = resourceAccessHelper.requireTemplateForUse(plan.getSourceTemplateId());

    Map<String, Object> typeSnapshot = snapshotType(plan, type);
    Map<String, Object> standardSnapshot = snapshotStandard(standard);
    Map<String, Object> templateSnapshot = snapshotTemplate(template);

    try {
      plan.setTrainingTypeSnapshotJson(objectMapper.writeValueAsString(typeSnapshot));
      plan.setTrainingStandardSnapshotJson(objectMapper.writeValueAsString(standardSnapshot));
      plan.setTemplateSnapshotJson(objectMapper.writeValueAsString(templateSnapshot));
      String combined = plan.getTrainingTypeSnapshotJson() + plan.getTrainingStandardSnapshotJson() + plan.getTemplateSnapshotJson();
      plan.setSourceSnapshotHash(sha256Hex(combined));
    } catch (JsonProcessingException ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "快照序列化失败");
    }
    plan.setPublishStatus("PUBLISHED");
    if (plan.getPublishedTime() == null) plan.setPublishedTime(LocalDateTime.now());
    if (trainingPlanMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
    planHoursWarningRefreshService.refresh(trainingPlanId);
  }

  /** 完整变更的分类条件在提交时展开，批准时使用固定名单而不重算组。 */
  public void freezeChangeParticipants(TrainingPlanFullChangeRequest request) {
    request.setParticipantUserIds(personSelectionService.resolve(request.getParticipantSelection()));
  }

  /** 仅供已授权的变更审批服务在同一事务中调用，不暴露为HTTP编辑入口。 */
  @Transactional
  public void applyApprovedChange(TrainingPlan locked, TrainingPlanChangeRequest request) {
    Long id = locked.getTrainingPlanId();
    if ("BASIC".equals(request.getChangeType())) {
      var basic = request.getBasic();
      locked.setPlanName(basic.getPlanName().trim()); locked.setManagerUserId(basic.getManagerUserId());
      locked.setLocation(basic.getLocation()); locked.setDescription(basic.getDescription());
      if (trainingPlanMapper.updateById(locked) == 0) throw new BusinessException(HttpStatus.CONFLICT,8104,"培训计划版本冲突");
      return;
    }
    var full = request.getFull();
    if (full.getParticipantUserIds() == null || full.getParticipantUserIds().isEmpty())
      throw new BusinessException(HttpStatus.CONFLICT,8350,"完整变更没有提交时冻结的参训名单");
    // 生效事务内临时转为草稿以复用既有校验；任何失败都会恢复原计划和全部编排。
    locked.setPublishStatus("DRAFT");
    if (trainingPlanMapper.updateById(locked) == 0) throw new BusinessException(HttpStatus.CONFLICT,8104,"培训计划版本冲突");
    full.getPlan().setVersion(requirePlan(id).getVersion()); update(id,full.getPlan());
    var stages = new TrainingPlanStagesReplaceRequest(); stages.setStages(full.getStages()); stages.setVersion(requirePlan(id).getVersion()); replaceStages(id,stages);
    var forms = new TrainingPlanEvaluationFormBindRequest(); forms.setForms(full.getForms()); forms.setVersion(requirePlan(id).getVersion()); bindEvaluationForms(id,forms);
    trainingParticipantMapper.update(null,Wrappers.<TrainingParticipant>lambdaUpdate().setSql("deleted = training_participant_id").eq(TrainingParticipant::getTrainingPlanId,id).eq(TrainingParticipant::getDeleted,0L));
    var selection = new PersonSelectionRequest(); selection.setSelectionType("USER"); selection.setSubjectIds(full.getParticipantUserIds());
    var bind = new BindTrainingSelectionRequest(); bind.setSelection(selection); bindSelection(id,bind);
    freezeAndPublish(requirePlan(id));
  }

  @Override
  public TrainingPublishPrecheckVO precheck(Long trainingPlanId) {
    planHoursWarningRefreshService.refresh(trainingPlanId);
    TrainingPlan plan = requirePlan(trainingPlanId);
    TrainingPublishPrecheckVO vo = new TrainingPublishPrecheckVO();
    vo.setTrainingPlanId(trainingPlanId);
    vo.setIssues(publishValidator.collectIssues(plan));
    if ("DRAFT".equals(plan.getPublishStatus())) {
      try { approvalService.requireApproved(plan); }
      catch (BusinessException ex) {
        if (ex.getCode() != 8332) throw ex;
        var issue = new TrainingPublishIssueVO(); issue.setCode(8332); issue.setMessage(ex.getMessage()); vo.getIssues().add(issue);
      }
    }
    vo.setReady(vo.getIssues().isEmpty() && "DRAFT".equals(plan.getPublishStatus()));
    if (!"DRAFT".equals(plan.getPublishStatus())) {
      TrainingPublishIssueVO statusIssue = new TrainingPublishIssueVO();
      statusIssue.setCode(8103);
      statusIssue.setMessage("仅草稿计划可发布");
      vo.getIssues().add(statusIssue);
      vo.setReady(false);
    }
    return vo;
  }

  @Override
  public List<TrainingParticipantVO> listParticipants(Long trainingPlanId) {
    requirePlan(trainingPlanId);
    return trainingParticipantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId)
        .orderByAsc(TrainingParticipant::getTrainingParticipantId))
      .stream().map(this::toParticipantVO).toList();
  }

  @Override
  @Transactional
  public TrainingParticipantVO assignParticipant(Long trainingPlanId, TrainingParticipantAssignRequest request) {
    TrainingPlan plan = requireDraftForParticipants(trainingPlanId);
    PersonSelectionRequest selection = new PersonSelectionRequest();
    selection.setSelectionType("USER");
    selection.setSubjectIds(List.of(request.getUserId()));
    Long userId = personSelectionService.resolve(selection).get(0);
    TrainingParticipant existing = trainingParticipantMapper.selectOne(new LambdaQueryWrapper<TrainingParticipant>()
      .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId)
                                                                         .eq(TrainingParticipant::getUserId, userId));
    if (existing != null) {
      return toParticipantVO(existing);
    }
    TrainingParticipant participant = new TrainingParticipant();
    participant.setOrganizationId(plan.getOrganizationId());
    participant.setTrainingPlanId(trainingPlanId);
    participant.setUserId(userId);
    participant.setParticipantOrgId(plan.getOrganizationId());
    participant.setJoinSource("ASSIGN");
    participant.setJoinedTime(LocalDateTime.now());
    participant.setParticipantStatus("PENDING");
    participant.setOnlineHours(BigDecimal.ZERO);
    participant.setOfflineHours(BigDecimal.ZERO);
    try {
      trainingParticipantMapper.insert(participant);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8107, "参训人员已存在");
    }
    bumpParticipantVersion(plan);
    return toParticipantVO(participant);
  }

  @Override
  @Transactional
  public List<TrainingParticipantVO> bindSelection(Long trainingPlanId, BindTrainingSelectionRequest request) {
    TrainingPlan plan = requireDraftForParticipants(trainingPlanId);
    List<Long> userIds = personSelectionService.resolve(request.getSelection());
    List<TrainingParticipantVO> result = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (Long userId : userIds) {
      TrainingParticipant existing = trainingParticipantMapper.selectOne(new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId)
                                                                           .eq(TrainingParticipant::getUserId, userId));
      if (existing != null) {
        result.add(toParticipantVO(existing));
        continue;
      }
      TrainingParticipant participant = new TrainingParticipant();
      participant.setOrganizationId(plan.getOrganizationId());
      participant.setTrainingPlanId(trainingPlanId);
      participant.setUserId(userId);
      participant.setParticipantOrgId(plan.getOrganizationId());
      participant.setJoinSource("ASSIGN");
      participant.setJoinedTime(now);
      participant.setParticipantStatus("PENDING");
      participant.setOnlineHours(BigDecimal.ZERO);
      participant.setOfflineHours(BigDecimal.ZERO);
      trainingParticipantMapper.insert(participant);
      result.add(toParticipantVO(participant));
    }
    bumpParticipantVersion(plan);
    return result;
  }

  /** 名单属于审批内容，修改后必须重新审批。 */
  private void bumpParticipantVersion(TrainingPlan plan) {
    if (trainingPlanMapper.updateById(plan) == 0) throw new BusinessException(HttpStatus.CONFLICT,8104,"培训计划版本冲突");
  }

  @Override
  public TrainingGenerateExecutionsResultVO generateExecutions(Long trainingPlanId) {
    return trainingExecutionService.generateExecutions(trainingPlanId);
  }

  @Override
  @Transactional
  public void start(Long trainingPlanId, Integer version) {
    lifecycleService.start(trainingPlanId, version);
  }

  @Override
  @Transactional
  public void end(Long trainingPlanId, Integer version) {
    lifecycleService.end(trainingPlanId, version);
  }

  @Override
  @Transactional
  public void cancel(Long trainingPlanId, Integer version) {
    lifecycleService.cancel(trainingPlanId, version);
  }

  TrainingPlan requirePlan(Long id) {
    TrainingPlan plan = trainingPlanMapper.selectById(id);
    if (plan == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    }
    return plan;
  }

  private TrainingPlan requireDraft(Long id) {
    TrainingPlan plan = approvalService.lockForEdit(id);
    if (!"DRAFT".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8103, "仅草稿计划可编辑或删除");
    }
    return plan;
  }

  private TrainingPlan requireDraftForParticipants(Long id) {
    TrainingPlan plan = approvalService.lockForEdit(id);
    if (!"DRAFT".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8109, "已发布计划不可修改参训名单");
    }
    return plan;
  }

  private void validateResourcesForPlan(Long typeId, Long standardId, Long templateId) {
    resourceAccessHelper.requireTypeForUse(typeId);
    resourceAccessHelper.requireStandardForUse(standardId);
    resourceAccessHelper.requireTemplateForUse(templateId);
  }

  private void applySourceOwner(TrainingPlan plan) {
    if (plan.getSourceTemplateId() != null) {
      TrainingTemplate template = resourceAccessHelper.requireTemplateForUse(plan.getSourceTemplateId());
      plan.setSourceOwnerOrganizationId(template.getOrganizationId());
    } else {
      plan.setSourceOwnerOrganizationId(null);
    }
  }

  /**
   * 复制课表前校验模板已发布且与计划培训类型一致。
   */
  private TrainingTemplate requirePublishedTemplateForCopy(Long templateId, Long trainingTypeId) {
    TrainingTemplate template = resourceAccessHelper.requireTemplateForUse(templateId);
    if (template == null || !"PUBLISHED".equals(template.getPublishStatus())
      || !Objects.equals(trainingTypeId, template.getTrainingTypeId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8157, "来源模板未发布、不属于当前机构或与计划培训类型不一致");
    }
    return template;
  }

  /**
   * 将模板阶段与内容复制为计划课表，不复制模板绑定的培训标准。
   */
  private void copyTemplateSyllabus(Long trainingPlanId, Long sourceTemplateId) {
    List<TrainingTemplateStage> stages = trainingTemplateStageMapper.selectList(
      new LambdaQueryWrapper<TrainingTemplateStage>()
        .eq(TrainingTemplateStage::getTrainingTemplateId, sourceTemplateId)
        .orderByAsc(TrainingTemplateStage::getSortOrder));
    for (TrainingTemplateStage templateStage : stages) {
      TrainingPlanStage stage = new TrainingPlanStage();
      stage.setTrainingPlanId(trainingPlanId);
      stage.setSourceTemplateStageId(templateStage.getTemplateStageId());
      stage.setStageName(templateStage.getStageName());
      stage.setStageType(templateStage.getStageType());
      stage.setRequiredHours(templateStage.getRequiredHours());
      stage.setSortOrder(templateStage.getSortOrder());
      stage.setStageStatus("PENDING");
      trainingPlanStageMapper.insert(stage);
      List<TrainingTemplateContent> contents = trainingTemplateContentMapper.selectList(
        new LambdaQueryWrapper<TrainingTemplateContent>()
          .eq(TrainingTemplateContent::getTemplateStageId, templateStage.getTemplateStageId())
          .orderByAsc(TrainingTemplateContent::getSortOrder));
      for (TrainingTemplateContent templateContent : contents) {
        TrainingPlanContent content = new TrainingPlanContent();
        content.setPlanStageId(stage.getPlanStageId());
        content.setContentType(templateContent.getContentType());
        content.setResourceId(templateContent.getResourceId());
        content.setResourceVersionId(templateContent.getResourceVersionId());
        content.setAssessmentMode(templateContent.getAssessmentMode());
        content.setExecutionConfigJson(templateContent.getExecutionConfigJson());
        content.setContentName(templateContent.getContentName());
        content.setIsRequired(templateContent.getIsRequired());
        content.setIsScored(0);
        content.setRequiredHours(TrainingArrangedHours.forCopy(
          templateContent.getContentType(), templateContent.getRequiredHours()));
        content.setSortOrder(templateContent.getSortOrder());
        content.setContentStatus("PENDING");
        trainingPlanContentMapper.insert(content);
      }
    }
  }

  private void validateContentType(String contentType) {
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8112, "不支持的内容类型");
    }
  }

  private void deleteStagesAndContents(Long trainingPlanId) {
    List<TrainingPlanStage> stages = trainingPlanStageMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanStage>().eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId));
    for (TrainingPlanStage stage : stages) {
      trainingPlanContentMapper.update(null, Wrappers.<TrainingPlanContent>lambdaUpdate()
        .setSql("deleted = plan_content_id")
        .eq(TrainingPlanContent::getPlanStageId, stage.getPlanStageId())
        .eq(TrainingPlanContent::getDeleted, 0L));
    }
    trainingPlanStageMapper.update(null, Wrappers.<TrainingPlanStage>lambdaUpdate()
      .setSql("deleted = plan_stage_id")
      .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId)
      .eq(TrainingPlanStage::getDeleted, 0L));
  }

  private void softDeleteEvaluationForms(Long trainingPlanId) {
    trainingPlanEvaluationFormMapper.update(null, Wrappers.<TrainingPlanEvaluationForm>lambdaUpdate()
      .setSql("deleted = plan_evaluation_form_id")
      .eq(TrainingPlanEvaluationForm::getTrainingPlanId, trainingPlanId)
      .eq(TrainingPlanEvaluationForm::getDeleted, 0L));
  }

  private List<TrainingPlanStageVO> listStages(Long trainingPlanId) {
    return trainingPlanStageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>()
        .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId)
        .orderByAsc(TrainingPlanStage::getSortOrder))
      .stream().map(stage -> {
        TrainingPlanStageVO vo = new TrainingPlanStageVO();
        BeanUtils.copyProperties(stage, vo);
        vo.setContents(trainingPlanContentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>()
            .eq(TrainingPlanContent::getPlanStageId, stage.getPlanStageId())
            .orderByAsc(TrainingPlanContent::getSortOrder))
          .stream().map(this::toContentVO).toList());
        return vo;
      }).toList();
  }

  private List<TrainingPlanEvaluationFormVO> listEvaluationForms(Long trainingPlanId) {
    return trainingPlanEvaluationFormMapper.selectList(new LambdaQueryWrapper<TrainingPlanEvaluationForm>()
                                                         .eq(TrainingPlanEvaluationForm::getTrainingPlanId,
                                                             trainingPlanId))
      .stream()
      .map(binding -> toEvaluationFormVO(trainingPlanId, binding))
      .sorted(Comparator.comparing(TrainingPlanEvaluationFormVO::getUsageType)
                .thenComparing(TrainingPlanEvaluationFormVO::getPlanEvaluationFormId))
      .toList();
  }

  private TrainingPlanEvaluationFormVO toEvaluationFormVO(
    Long trainingPlanId, TrainingPlanEvaluationForm binding) {
    EvaluationFormVersion version = evaluationFormVersionMapper.selectById(binding.getFormVersionId());
    if (version == null) {
      throw incompleteEvaluationFormBinding(trainingPlanId, binding.getPlanEvaluationFormId());
    }
    EvaluationForm form = evaluationFormMapper.selectById(version.getEvaluationFormId());
    if (form == null) {
      throw incompleteEvaluationFormBinding(trainingPlanId, binding.getPlanEvaluationFormId());
    }
    TrainingPlanEvaluationFormVO vo = new TrainingPlanEvaluationFormVO();
    BeanUtils.copyProperties(binding, vo);
    vo.setEvaluationFormId(form.getEvaluationFormId());
    vo.setFormName(form.getFormName());
    vo.setVersionNo(version.getVersionNo());
    vo.setPublishedStatus(version.getPublishedStatus());
    return vo;
  }

  private BusinessException incompleteEvaluationFormBinding(Long trainingPlanId, Long bindingId) {
    log.error("培训计划评价表单绑定数据不完整: trainingPlanId={}, planEvaluationFormId={}",
              trainingPlanId, bindingId);
    return new BusinessException(
      HttpStatus.INTERNAL_SERVER_ERROR, 8132, "培训计划评价表单绑定数据不完整");
  }

  private Map<String, Object> snapshotType(TrainingPlan plan, TrainingType type) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("schemaVersion", 2);
    map.put("trainingTypeId", type.getTrainingTypeId());
    map.put("typeCode", type.getTypeCode());
    map.put("typeName", type.getTypeName());
    map.put("initialHours", type.getInitialHours());
    map.put("reviewHours", type.getReviewHours());
    map.put("trainingNature", plan.getTrainingNature());
    map.put("applicableRequiredHours", plan.getApplicableRequiredHours());
    map.put("onlineMaxRatio", type.getOnlineMaxRatio());
    return map;
  }

  private Map<String, Object> snapshotStandard(TrainingStandard standard) {
    if (standard == null) {
      return Map.of();
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("trainingStandardId", standard.getTrainingStandardId());
    map.put("standardCode", standard.getStandardCode());
    map.put("standardName", standard.getStandardName());
    map.put("standardVersion", standard.getStandardVersion());
    List<Map<String, Object>> rules = trainingStandardRuleMapper.selectList(
        new LambdaQueryWrapper<TrainingStandardRule>().eq(TrainingStandardRule::getTrainingStandardId, standard.getTrainingStandardId()))
      .stream().map(r -> {
        Map<String, Object> rm = new LinkedHashMap<>();
        rm.put("ruleType", r.getRuleType());
        rm.put("requiredValue", r.getRequiredValue());
        rm.put("unit", r.getUnit());
        rm.put("resourceType", r.getResourceType());
        rm.put("resourceId", r.getResourceId());
        return rm;
      }).toList();
    map.put("rules", rules);
    return map;
  }

  private Map<String, Object> snapshotTemplate(TrainingTemplate template) {
    if (template == null) {
      return Map.of();
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("trainingTemplateId", template.getTrainingTemplateId());
    map.put("templateCode", template.getTemplateCode());
    map.put("templateName", template.getTemplateName());
    return map;
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

  private TrainingPlanVO toVO(TrainingPlan plan) {
    TrainingPlanVO vo = new TrainingPlanVO();
    BeanUtils.copyProperties(plan, vo);
    return vo;
  }

  private TrainingPlanContentVO toContentVO(TrainingPlanContent content) {
    TrainingPlanContentVO vo = new TrainingPlanContentVO();
    BeanUtils.copyProperties(content, vo);
    vo.setExecutionConfig(configCodec.fromJson(content.getExecutionConfigJson()));
    return vo;
  }

  private TrainingParticipantVO toParticipantVO(TrainingParticipant participant) {
    TrainingParticipantVO vo = new TrainingParticipantVO();
    BeanUtils.copyProperties(participant, vo);
    return vo;
  }
}
