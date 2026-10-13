package com.sts.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.admin.storage.StoredDownload;
import com.sts.archive.dto.ArchiveCreateFromPlanRequest;
import com.sts.archive.dto.ArchiveEnterpriseQuery;
import com.sts.archive.entity.ArchiveAsset;
import com.sts.archive.entity.ArchiveParticipant;
import com.sts.archive.entity.ArchiveTraining;
import com.sts.archive.entity.ArchiveVersion;
import com.sts.archive.mapper.ArchiveAssetMapper;
import com.sts.archive.mapper.ArchiveParticipantMapper;
import com.sts.archive.mapper.ArchiveTrainingMapper;
import com.sts.archive.mapper.ArchiveVersionMapper;
import com.sts.archive.vo.ArchivePersonVO;
import com.sts.archive.vo.ArchiveTrainingVO;
import com.sts.archive.vo.ArchiveViewVO;
import com.sts.admin.tenant.TenantContext;
import com.sts.certificate.entity.CertificateCertificate;
import com.sts.certificate.mapper.CertificateCertificateMapper;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.evaluation.service.EvaluationFormService;
import com.sts.evaluation.service.EvaluationRecordService;
import com.sts.evaluation.service.ExpertInspectionService;
import com.sts.evaluation.vo.EvaluationFormVersionDetailVO;
import com.sts.evaluation.vo.EvaluationItemVO;
import com.sts.evaluation.vo.EvaluationRecordItemVO;
import com.sts.evaluation.vo.EvaluationRecordVO;
import com.sts.evaluation.vo.ExpertInspectionSummaryVO;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.survey.util.SnapshotHashUtil;
import com.sts.training.entity.TrainingAttachment;
import com.sts.training.entity.TrainingOfflineScore;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl extends BaseServiceImpl<ArchiveTrainingMapper, ArchiveTraining> implements ArchiveService {

  /** 影像记录与附件同源分流：IMAGE/VIDEO 记为影像记录，其余类型记为附件。 */
  private static final Set<String> IMAGE_ATTACHMENT_TYPES = Set.of("IMAGE", "VIDEO");
  /** 签到情况取值：计划无线下内容 / 已录入线下成绩 / 尚未录入线下成绩。 */
  private static final String ATTENDANCE_NOT_APPLICABLE = "NOT_APPLICABLE";
  private static final String ATTENDANCE_SIGNED = "SIGNED";
  private static final String ATTENDANCE_NOT_RECORDED = "NOT_RECORDED";

  private final ArchiveTrainingMapper archiveTrainingMapper;
  private final ArchiveVersionMapper archiveVersionMapper;
  private final ArchiveParticipantMapper archiveParticipantMapper;
  private final TrainingPlanMapper trainingPlanMapper;
  private final TrainingParticipantMapper trainingParticipantMapper;
  private final TrainingRewardPenaltyMapper rewardPenaltyMapper;
  private final TrainingAttachmentMapper attachmentMapper;
  private final TrainingPlanContentMapper planContentMapper;
  private final TrainingPlanStageMapper planStageMapper;
  private final TrainingOfflineScoreMapper offlineScoreMapper;
  private final CertificateCertificateMapper certificateMapper;
  private final TrainingTypeMapper trainingTypeMapper;
  private final OrganizationMapper organizationMapper;
  private final SysUserMapper userMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final ArchiveAssetMapper archiveAssetMapper;
  private final ObjectMapper objectMapper;
  private final GeneratedFileStore generatedFileStore;
  private final ArchivePdfRenderer archivePdfRenderer;
  private final ExpertInspectionService expertInspectionService;
  private final EvaluationRecordService evaluationRecordService;
  private final EvaluationFormService evaluationFormService;

  @Override
  @Transactional
  public ArchiveTrainingVO createFromPlan(ArchiveCreateFromPlanRequest request) {
    ArchiveViewVO view = buildView(requireEndedPlan(request.getTrainingPlanId()), true, true, true);
    if (!view.getMissingItems().isEmpty()) {
      throw missingFacts(view.getMissingItems());
    }
    TrainingPlan plan = trainingPlanMapper.selectById(request.getTrainingPlanId());
    ArchiveTraining archive = archiveTrainingMapper.selectOne(
      new LambdaQueryWrapper<ArchiveTraining>().eq(ArchiveTraining::getTrainingPlanId, request.getTrainingPlanId()));
    if (archive == null) {
      archive = new ArchiveTraining();
      archive.setArchiveCode(request.getArchiveCode().trim());
      archive.setTrainingPlanId(request.getTrainingPlanId());
      archive.setCurrentVersionNo(0);
      archive.setArchiveStatus("DRAFT");
      archive.setStatus(1);
      try {
        archiveTrainingMapper.insert(archive);
      } catch (DuplicateKeyException ex) {
        throw new BusinessException(HttpStatus.CONFLICT, 9203, "档案编码或计划已存在");
      }
    }

    int nextVersion = (archive.getCurrentVersionNo() == null ? 0 : archive.getCurrentVersionNo()) + 1;

    // 快照输入必须稳定：参训人按主键升序取，否则哈希会随数据库返回顺序抖动。
    List<TrainingParticipant> participants = trainingParticipantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, plan.getTrainingPlanId())
        .orderByAsc(TrainingParticipant::getTrainingParticipantId));
    Map<Long, List<Map<String, Object>>> rewardFacts = groupBy(facts(view.getRewardPenalties()),
                                                              "trainingParticipantId");
    Map<Long, List<Map<String, Object>>> certificateFacts = groupBy(facts(view.getCertificates()), "holderUserId");
    Map<Long, List<Map<String, Object>>> memberFacts = groupBy(facts(view.getParticipants()),
                                                              "trainingParticipantId");

    String planJson = toJson(view.getPlan());
    String orgJson = toJson(view.getOrganization());
    String summaryJson = toJson(Map.of("participantCount", view.getParticipantCount()));

    List<ArchiveParticipant> participantRows = new ArrayList<>();
    for (TrainingParticipant p : participants) {
      ArchiveParticipant ap = new ArchiveParticipant();
      ap.setTrainingParticipantId(p.getTrainingParticipantId());
      ap.setUserId(p.getUserId());
      ap.setParticipantOrgId(p.getParticipantOrgId() != null ? p.getParticipantOrgId() : plan.getOrganizationId());
      ap.setMemberSnapshotJson(toJson(memberSnapshot(memberFacts, p.getUserId(), p.getTrainingParticipantId())));
      ap.setLearningSnapshotJson(toJson(snapshot("courseScore", p.getCourseScore(), "examScore", p.getExamScore(),
                                                "offlineScore", p.getOfflineScore())));
      ap.setExamSnapshotJson(toJson(snapshot("examScore", p.getExamScore())));
      ap.setEvaluationSnapshotJson(toJson(snapshot("compositeScore", p.getCompositeScore())));
      // 写实：不再写 "{}"，奖惩与证书按参训人各自的真实事实落库。
      ap.setRewardPenaltySnapshotJson(
        toJson(rewardFacts.getOrDefault(p.getTrainingParticipantId(), List.of())));
      ap.setCertificateSnapshotJson(
        toJson(certificateFacts.getOrDefault(p.getUserId(), List.of())));
      ap.setFinalScore(p.getCompositeScore());
      ap.setResultStatus(p.getResultStatus() != null ? p.getResultStatus() : "INCOMPLETE");
      participantRows.add(ap);
    }
    String hash = SnapshotHashUtil.sha256Hex(
      planJson + orgJson + summaryJson
        + participantRows.stream().map(ArchiveServiceImpl::participantHashInput)
        .collect(Collectors.joining()));

    ArchiveVersion version = new ArchiveVersion();
    version.setTrainingArchiveId(archive.getTrainingArchiveId());
    version.setVersionNo(nextVersion);
    version.setPlanSnapshotJson(planJson);
    version.setOrganizationSnapshotJson(orgJson);
    version.setSummarySnapshotJson(summaryJson);
    version.setSnapshotHash(hash);
    version.setGeneratedTime(LocalDateTime.now());
    version.setGeneratedBy(0L);
    version.setVersionReason(request.getVersionReason());
    archiveVersionMapper.insert(version);

    for (ArchiveParticipant row : participantRows) {
      row.setArchiveVersionId(version.getArchiveVersionId());
      archiveParticipantMapper.insert(row);
    }
    registerAssets(version.getArchiveVersionId(), plan.getTrainingPlanId());

    Long fileInfoId = generatedFileStore.store(
      archivePdfRenderer.render(view),
      "archive-" + plan.getPlanCode() + ".pdf",
      "archive",
      "application/pdf",
      "pdf");
    version.setFileInfoId(fileInfoId);
    archiveVersionMapper.updateById(version);
    view.setFileInfoId(fileInfoId);

    archive.setCurrentVersionNo(nextVersion);
    archive.setArchiveStatus("SEALED");
    archive.setSealedBy(0L);
    archive.setSealedTime(LocalDateTime.now());
    archiveTrainingMapper.updateById(archive);

    ArchiveTrainingVO vo = new ArchiveTrainingVO();
    BeanUtils.copyProperties(archive, vo);
    return vo;
  }

  @Override
  public ArchiveViewVO getByPlan(Long trainingPlanId, boolean includeCertificateFacts) {
    return buildView(requirePlan(trainingPlanId), false, includeCertificateFacts, true);
  }

  @Override
  public List<ArchiveViewVO> listEnterprise(ArchiveEnterpriseQuery query, boolean includeCertificateFacts) {
    ArchiveEnterpriseQuery filter = query == null ? new ArchiveEnterpriseQuery() : query;
    Organization current = currentOrganization();
    if (current == null) {
      return List.of();
    }
    if (StringUtils.hasText(filter.getOrganizationName())
      && (current.getOrgName() == null || !current.getOrgName().contains(filter.getOrganizationName().trim()))) {
      return List.of();
    }
    LambdaQueryWrapper<TrainingPlan> wrapper = new LambdaQueryWrapper<TrainingPlan>()
      .eq(TrainingPlan::getPublishStatus, "ENDED")
      .eq(TrainingPlan::getOrganizationId, current.getOrganizationId())
      .like(StringUtils.hasText(filter.getPlanName()), TrainingPlan::getPlanName, filter.getPlanName())
      .eq(StringUtils.hasText(filter.getPlanCode()), TrainingPlan::getPlanCode, filter.getPlanCode())
      .eq(filter.getTrainingTypeId() != null, TrainingPlan::getTrainingTypeId, filter.getTrainingTypeId())
      .ge(filter.getStartTimeFrom() != null, TrainingPlan::getStartTime, filter.getStartTimeFrom())
      .le(filter.getStartTimeTo() != null, TrainingPlan::getStartTime, filter.getStartTimeTo())
      .ge(filter.getEndTimeFrom() != null, TrainingPlan::getEndTime, filter.getEndTimeFrom())
      .le(filter.getEndTimeTo() != null, TrainingPlan::getEndTime, filter.getEndTimeTo())
      .orderByDesc(TrainingPlan::getEndedTime);
    return trainingPlanMapper.selectList(wrapper).stream()
      .map(plan -> buildView(plan, false, includeCertificateFacts, false))
      .toList();
  }

  @Override
  public ArchivePersonVO getPerson(Long userId, boolean includeCertificateFacts) {
    SysUser user = userMapper.selectById(userId);
    if (user == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9205, "人员不存在");
    }
    List<TrainingParticipant> rows = trainingParticipantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>().eq(TrainingParticipant::getUserId, userId));
    List<ArchiveViewVO> trainings = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (TrainingParticipant row : rows) {
      TrainingPlan plan = trainingPlanMapper.selectById(row.getTrainingPlanId());
      if (plan == null || !"ENDED".equals(plan.getPublishStatus())) {
        continue;
      }
      ArchiveViewVO view = buildView(plan, false, includeCertificateFacts, false);
      trainings.add(view);
      for (String item : view.getMissingItems()) {
        if (!missing.contains(item)) {
          missing.add(item);
        }
      }
    }
    ArchivePersonVO vo = new ArchivePersonVO();
    vo.setUserId(user.getUserId());
    vo.setUserName(user.getUserName());
    vo.setUserCode(user.getUserCode());
    vo.setMissingItems(missing);
    vo.setTrainings(trainings);
    return vo;
  }

  @Override
  public StoredDownload openPlanFile(Long trainingPlanId) {
    ArchiveViewVO view = buildView(requireEndedPlan(trainingPlanId), true, true, false);
    if (!view.getMissingItems().isEmpty()) {
      throw missingFacts(view.getMissingItems());
    }
    if (view.getFileInfoId() == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 9204, "档案文件尚未生成");
    }
    return generatedFileStore.open(view.getFileInfoId(), 9206, "档案文件不存在");
  }

  @Override
  public StoredDownload openPersonFile(Long userId) {
    ArchivePersonVO person = getPerson(userId, true);
    if (person.getMissingItems() != null && !person.getMissingItems().isEmpty()) {
      throw missingFacts(person.getMissingItems());
    }
    if (person.getTrainings() == null || person.getTrainings().isEmpty()) {
      throw new BusinessException(HttpStatus.CONFLICT, 9204, "该人员暂无已结束培训档案");
    }
    // 个人档案按当前可见的已结束培训事实即时渲染 PDF，不落库也不伪造文件 ID。
    byte[] content = archivePdfRenderer.renderPerson(person);
    String fileCode = StringUtils.hasText(person.getUserCode()) ? person.getUserCode() : String.valueOf(userId);
    return new StoredDownload(new ByteArrayResource(content), "archive-person-" + fileCode + ".pdf",
                              "application/pdf", content.length);
  }

  /**
   * 组装一期/企业/个人三视角共用的档案事实。
   * <p>一次查询聚合计划、企业、培训内容、参训人、线下成绩、奖惩与资料，并批量取用户，
   * 避免按参训人数线性放大查询；签到情况由线下成绩映射，不作为缺失项。
   */
  private ArchiveViewVO buildView(TrainingPlan plan, boolean requireEnded, boolean includeCertificateFacts,
                                  boolean includeExpertInspections) {
    if (requireEnded && !"ENDED".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 9202, "仅已结束培训可以形成档案");
    }
    Long planId = plan.getTrainingPlanId();
    Organization org = plan.getOrganizationId() == null ? null : organizationMapper.selectById(plan.getOrganizationId());
    TrainingType type = plan.getTrainingTypeId() == null ? null : trainingTypeMapper.selectById(plan.getTrainingTypeId());
    List<TrainingParticipant> participants = trainingParticipantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>().eq(TrainingParticipant::getTrainingPlanId, planId));
    List<TrainingRewardPenalty> rewards = rewardPenaltyMapper.selectList(
      new LambdaQueryWrapper<TrainingRewardPenalty>()
        .eq(TrainingRewardPenalty::getTrainingPlanId, planId)
        // 与正式结果口径一致：已停用（作废）的奖惩不再作为档案事实呈现。
        .eq(TrainingRewardPenalty::getStatus, 1));
    List<TrainingAttachment> attachments = attachmentMapper.selectList(
      new LambdaQueryWrapper<TrainingAttachment>()
        .eq(TrainingAttachment::getTrainingPlanId, planId)
        // 与奖惩口径一致：停用的资料不再作为档案事实呈现，行为与「停用即不再引用」的直觉一致。
        .eq(TrainingAttachment::getStatus, 1));
    // 培训内容只有 plan_stage_id，没有 training_plan_id，必须先按计划取阶段再按阶段取内容。
    List<TrainingPlanStage> stages = planStageMapper.selectList(
      new LambdaQueryWrapper<TrainingPlanStage>().eq(TrainingPlanStage::getTrainingPlanId, planId));
    List<Long> stageIds = stages.stream().map(TrainingPlanStage::getPlanStageId).filter(Objects::nonNull).toList();
    List<TrainingPlanContent> contents = stageIds.isEmpty() ? List.of()
      : planContentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>()
        .in(TrainingPlanContent::getPlanStageId, stageIds)
        .orderByAsc(TrainingPlanContent::getSortOrder));

    List<Long> participantIds = participants.stream()
      .map(TrainingParticipant::getTrainingParticipantId).filter(Objects::nonNull).toList();
    Map<Long, List<TrainingOfflineScore>> offlineScores = loadOfflineScores(participantIds);
    Map<Long, SysUser> users = loadUsers(participants.stream().map(TrainingParticipant::getUserId).toList());
    Map<Long, String> stageNames = stages.stream()
      .filter(stage -> stage.getPlanStageId() != null)
      .collect(Collectors.toMap(TrainingPlanStage::getPlanStageId,
                                stage -> Objects.toString(stage.getStageName(), ""), (first, ignored) -> first));

    List<String> missing = new ArrayList<>();
    if ("ENDED".equals(plan.getPublishStatus())
      && participants.stream().anyMatch(p -> "INCOMPLETE".equals(p.getResultStatus()))) {
      missing.add("RESULTS");
    }

    Map<String, Object> planSnap = new LinkedHashMap<>();
    planSnap.put("trainingPlanId", planId);
    planSnap.put("planCode", plan.getPlanCode());
    planSnap.put("planName", plan.getPlanName());
    planSnap.put("publishStatus", plan.getPublishStatus());
    planSnap.put("startTime", plan.getStartTime());
    planSnap.put("endTime", plan.getEndTime());
    planSnap.put("endedTime", plan.getEndedTime());
    planSnap.put("location", plan.getLocation());
    planSnap.put("trainingNature", plan.getTrainingNature());
    planSnap.put("applicableRequiredHours", plan.getApplicableRequiredHours());
    planSnap.put("trainingTypeId", plan.getTrainingTypeId());
    planSnap.put("trainingTypeName", type == null ? null : type.getTypeName());

    Map<String, Object> orgSnap = new LinkedHashMap<>();
    orgSnap.put("organizationId", plan.getOrganizationId());
    orgSnap.put("organizationName", org == null ? null : org.getOrgName());
    orgSnap.put("orgCode", org == null ? null : org.getOrgCode());

    List<Map<String, Object>> contentSnaps = new ArrayList<>();
    for (TrainingPlanContent content : contents) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("planContentId", content.getPlanContentId());
      item.put("planStageId", content.getPlanStageId());
      item.put("stageName", stageNames.get(content.getPlanStageId()));
      item.put("contentName", content.getContentName());
      item.put("contentType", content.getContentType());
      item.put("isRequired", content.getIsRequired());
      item.put("isScored", content.getIsScored());
      item.put("scoreWeight", content.getScoreWeight());
      item.put("requiredHours", content.getRequiredHours());
      item.put("sortOrder", content.getSortOrder());
      contentSnaps.add(item);
    }

    boolean hasOfflineContent = contents.stream().anyMatch(content -> "OFFLINE".equals(content.getContentType()));
    List<Map<String, Object>> participantSnaps = new ArrayList<>();
    List<Map<String, Object>> attendanceSnaps = new ArrayList<>();
    List<Map<String, Object>> scoreSnaps = new ArrayList<>();
    List<Map<String, Object>> evaluationSnaps = new ArrayList<>();
    int signedCount = 0;
    int notRecordedCount = 0;
    BigDecimal onlineHoursTotal = BigDecimal.ZERO;
    BigDecimal offlineHoursTotal = BigDecimal.ZERO;
    BigDecimal scoreSum = BigDecimal.ZERO;
    int scoredCount = 0;
    for (TrainingParticipant p : participants) {
      SysUser user = p.getUserId() == null ? null : users.get(p.getUserId());
      String userName = user == null ? null : user.getUserName();

      Map<String, Object> member = new LinkedHashMap<>();
      member.put("trainingParticipantId", p.getTrainingParticipantId());
      member.put("userId", p.getUserId());
      member.put("userName", userName);
      member.put("userCode", user == null ? null : user.getUserCode());
      member.put("participantOrgId", p.getParticipantOrgId());
      member.put("participantOrgName", org == null ? null : org.getOrgName());
      participantSnaps.add(member);

      List<TrainingOfflineScore> rows = offlineScores.getOrDefault(p.getTrainingParticipantId(), List.of());
      String attendanceStatus = hasOfflineContent
        ? (p.getOfflineScore() != null || !rows.isEmpty() ? ATTENDANCE_SIGNED : ATTENDANCE_NOT_RECORDED)
        : ATTENDANCE_NOT_APPLICABLE;
      if (ATTENDANCE_SIGNED.equals(attendanceStatus)) {
        signedCount++;
      } else if (ATTENDANCE_NOT_RECORDED.equals(attendanceStatus)) {
        notRecordedCount++;
      }
      Map<String, Object> attendance = new LinkedHashMap<>();
      attendance.put("trainingParticipantId", p.getTrainingParticipantId());
      attendance.put("userId", p.getUserId());
      attendance.put("userName", userName);
      attendance.put("signInStatus", attendanceStatus);
      attendance.put("offlineScore", p.getOfflineScore());
      attendance.put("recordedTime", rows.stream()
        .map(TrainingOfflineScore::getScoredTime).filter(Objects::nonNull)
        .max(Comparator.naturalOrder()).orElse(null));
      attendanceSnaps.add(attendance);

      Map<String, Object> score = new LinkedHashMap<>();
      score.put("trainingParticipantId", p.getTrainingParticipantId());
      score.put("userId", p.getUserId());
      score.put("userName", userName);
      score.put("examScore", p.getExamScore());
      score.put("courseScore", p.getCourseScore());
      score.put("offlineScore", p.getOfflineScore());
      score.put("compositeScore", p.getCompositeScore());
      score.put("resultStatus", p.getResultStatus());
      scoreSnaps.add(score);

      Map<String, Object> evaluation = new LinkedHashMap<>();
      evaluation.put("trainingParticipantId", p.getTrainingParticipantId());
      evaluation.put("userId", p.getUserId());
      evaluation.put("userName", userName);
      evaluation.put("compositeScore", p.getCompositeScore());
      evaluation.put("resultStatus", p.getResultStatus());
      evaluationSnaps.add(evaluation);

      onlineHoursTotal = onlineHoursTotal.add(p.getOnlineHours() == null ? BigDecimal.ZERO : p.getOnlineHours());
      offlineHoursTotal = offlineHoursTotal.add(p.getOfflineHours() == null ? BigDecimal.ZERO : p.getOfflineHours());
      if (p.getCompositeScore() != null) {
        scoreSum = scoreSum.add(p.getCompositeScore());
        scoredCount++;
      }
    }

    List<Map<String, Object>> materialSnaps = new ArrayList<>();
    List<Map<String, Object>> attachmentSnaps = new ArrayList<>();
    for (TrainingAttachment attachment : attachments) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("trainingAttachmentId", attachment.getTrainingAttachmentId());
      item.put("attachmentType", attachment.getAttachmentType());
      item.put("attachmentName", attachment.getAttachmentName());
      item.put("description", attachment.getDescription());
      item.put("fileInfoId", attachment.getFileInfoId());
      item.put("planStageId", attachment.getPlanStageId());
      item.put("stageName", stageNames.get(attachment.getPlanStageId()));
      item.put("sortOrder", attachment.getSortOrder());
      if (IMAGE_ATTACHMENT_TYPES.contains(attachment.getAttachmentType())) {
        materialSnaps.add(item);
      } else {
        attachmentSnaps.add(item);
      }
    }

    List<Map<String, Object>> rewardSnaps = new ArrayList<>();
    for (TrainingRewardPenalty reward : rewards) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("rewardPenaltyId", reward.getRewardPenaltyId());
      item.put("trainingParticipantId", reward.getTrainingParticipantId());
      item.put("recordType", reward.getRecordType());
      item.put("title", reward.getTitle());
      item.put("description", reward.getDescription());
      item.put("scoreChange", reward.getScoreChange());
      item.put("occurredDate", reward.getOccurredDate());
      item.put("evidenceFileId", reward.getEvidenceFileId());
      item.put("status", reward.getStatus());
      rewardSnaps.add(item);
    }

    long passedCount = participants.stream().filter(p -> "PASSED".equals(p.getResultStatus())).count();
    long incompleteCount = participants.stream().filter(p -> "INCOMPLETE".equals(p.getResultStatus())).count();
    long failedCount = Math.max(participants.size() - passedCount - incompleteCount, 0);
    long requiredContentCount = contents.stream().filter(content -> Integer.valueOf(1).equals(content.getIsRequired())).count();
    long rewardCount = rewards.stream().filter(r -> "REWARD".equals(r.getRecordType())).count();
    long penaltyCount = rewards.stream().filter(r -> "PENALTY".equals(r.getRecordType())).count();
    long decidedCount = passedCount + failedCount;

    Map<String, Object> statistics = new LinkedHashMap<>();
    statistics.put("participantCount", (long) participants.size());
    statistics.put("completedCount", participants.size() - incompleteCount);
    statistics.put("passedCount", passedCount);
    statistics.put("failedCount", failedCount);
    statistics.put("incompleteCount", incompleteCount);
    statistics.put("passRate", decidedCount == 0 ? null
      : BigDecimal.valueOf(passedCount).divide(BigDecimal.valueOf(decidedCount), 4, RoundingMode.HALF_UP));
    statistics.put("averageCompositeScore", scoredCount == 0 ? null
      : scoreSum.divide(BigDecimal.valueOf(scoredCount), 2, RoundingMode.HALF_UP));
    statistics.put("contentCount", (long) contents.size());
    statistics.put("requiredContentCount", requiredContentCount);
    statistics.put("onlineHoursTotal", onlineHoursTotal);
    statistics.put("offlineHoursTotal", offlineHoursTotal);
    statistics.put("imageCount", (long) materialSnaps.size());
    statistics.put("attachmentCount", (long) attachmentSnaps.size());
    statistics.put("materialCount", (long) attachments.size());
    statistics.put("rewardCount", rewardCount);
    statistics.put("penaltyCount", penaltyCount);
    statistics.put("signedCount", hasOfflineContent ? signedCount : null);
    statistics.put("notRecordedCount", hasOfflineContent ? notRecordedCount : null);

    Long fileInfoId = currentVersionFileInfoId(planId);
    ArchiveViewVO vo = new ArchiveViewVO();
    vo.setTrainingPlanId(planId);
    vo.setPlanCode(plan.getPlanCode());
    vo.setPlanName(plan.getPlanName());
    vo.setPublishStatus(plan.getPublishStatus());
    vo.setOrganizationId(plan.getOrganizationId());
    vo.setOrganizationName(org == null ? null : org.getOrgName());
    vo.setParticipantCount((long) participants.size());
    vo.setMissingItems(missing);
    vo.setPlan(planSnap);
    vo.setOrganization(orgSnap);
    vo.setContents(contentSnaps);
    vo.setParticipants(participantSnaps);
    vo.setAttendance(attendanceSnaps);
    vo.setScores(scoreSnaps);
    vo.setEvaluations(evaluationSnaps);
    if (includeExpertInspections) {
      vo.setExpertInspections(loadExpertInspections(planId));
    }
    vo.setRewardPenalties(rewardSnaps);
    vo.setMaterials(materialSnaps);
    vo.setAttachments(attachmentSnaps);
    vo.setCertificates(includeCertificateFacts ? loadCertificateFacts(planId, participants) : null);
    vo.setStatistics(statistics);
    vo.setFileInfoId(fileInfoId);
    vo.setArchiveFileIsPdf(isPdfFile(fileInfoId));
    return vo;
  }

  /** 一期档案只收录当前绑定版本已提交的课程查课记录，逐项名称取发布版本快照。 */
  private List<Map<String, Object>> loadExpertInspections(Long planId) {
    List<Map<String, Object>> result = new ArrayList<>();
    Map<Long, EvaluationFormVersionDetailVO> versions = new LinkedHashMap<>();
    for (ExpertInspectionSummaryVO summary : expertInspectionService.listByPlan(planId)) {
      if (summary.getEvaluationRecordId() == null) continue;
      EvaluationRecordVO record = evaluationRecordService.get(summary.getEvaluationRecordId());
      EvaluationFormVersionDetailVO version = versions.computeIfAbsent(summary.getFormVersionId(),
        evaluationFormService::getVersionDetail);
      Map<Long, EvaluationRecordItemVO> answers = record.getItems().stream()
        .collect(Collectors.toMap(EvaluationRecordItemVO::getEvaluationItemId, item -> item));
      List<Map<String, Object>> items = new ArrayList<>();
      for (EvaluationItemVO item : version.getItems()) {
        EvaluationRecordItemVO answer = answers.get(item.getEvaluationItemId());
        if (answer == null) continue;
        items.add(snapshot("itemName", item.getItemName(), "itemType", item.getItemType(),
          "score", answer.getScore(), "textValue", answer.getTextValue()));
      }
      result.add(snapshot("contentName", summary.getContentName(), "versionNo", summary.getVersionNo(),
        "evaluatorName", summary.getEvaluatorName(), "submittedTime", summary.getSubmittedTime(),
        "totalScore", summary.getTotalScore(), "summary", record.getSummary(), "items", items));
    }
    return result;
  }

  /** 证书事实只按培训来源聚合，下载仍走既有证书文件端点，不在档案里复制证书文件。 */
  private List<Map<String, Object>> loadCertificateFacts(Long planId, List<TrainingParticipant> participants) {
    List<Long> holderIds = participants.stream().map(TrainingParticipant::getUserId)
      .filter(Objects::nonNull).distinct().toList();
    if (holderIds.isEmpty()) {
      return List.of();
    }
    List<CertificateCertificate> certificates = certificateMapper.selectList(
      new LambdaQueryWrapper<CertificateCertificate>()
        .eq(CertificateCertificate::getSourceType, "TRAINING")
        .eq(CertificateCertificate::getSourceId, planId)
        .in(CertificateCertificate::getHolderUserId, holderIds));
    List<Map<String, Object>> facts = new ArrayList<>();
    for (CertificateCertificate certificate : certificates) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("certificateId", certificate.getCertificateId());
      item.put("certificateNo", certificate.getCertificateNo());
      item.put("holderUserId", certificate.getHolderUserId());
      item.put("sourceType", certificate.getSourceType());
      item.put("sourceId", certificate.getSourceId());
      item.put("issuedTime", certificate.getIssuedTime());
      item.put("certificateStatus", certificate.getCertificateStatus());
      facts.add(item);
    }
    return facts;
  }

  /** 批量读取参训人用户，替换逐人 selectById，查询次数不随参训人数增长。 */
  private Map<Long, SysUser> loadUsers(Collection<Long> userIds) {
    List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
    if (distinct.isEmpty()) {
      return Map.of();
    }
    return userMapper.selectBatchIds(distinct).stream()
      .filter(user -> user.getUserId() != null)
      .collect(Collectors.toMap(SysUser::getUserId, user -> user, (first, ignored) -> first));
  }

  /** 一次取出本期全部线下成绩并按参训人分组，供签到情况映射与录入时间展示。 */
  private Map<Long, List<TrainingOfflineScore>> loadOfflineScores(List<Long> participantIds) {
    if (participantIds.isEmpty()) {
      return Map.of();
    }
    List<TrainingOfflineScore> scores = offlineScoreMapper.selectList(
      new LambdaQueryWrapper<TrainingOfflineScore>()
        .in(TrainingOfflineScore::getTrainingParticipantId, participantIds));
    return scores.stream().filter(score -> score.getTrainingParticipantId() != null)
      .collect(Collectors.groupingBy(TrainingOfflineScore::getTrainingParticipantId));
  }

  /** 历史 JSON 档案不是 PDF 正本，页面据此提示重新更新档案。 */
  private boolean isPdfFile(Long fileInfoId) {
    if (fileInfoId == null) {
      return false;
    }
    SysFileInfo file = fileInfoMapper.selectById(fileInfoId);
    return file != null && "pdf".equalsIgnoreCase(file.getFileSuffix());
  }

  /**
   * 把培训资料登记为本次归档版本的档案素材。
   * <p>只登记启用资料（{@code status = 1}），保证归档文件与在线三视角是同一批资料；
   * 同一受控文件被多条资料引用时只登记一条（`uk_version_file` 语义），
   * 否则会触发唯一键冲突并把整个归档事务一起回滚。
   */
  private void registerAssets(Long archiveVersionId, Long trainingPlanId) {
    List<TrainingAttachment> attachments = attachmentMapper.selectList(
      new LambdaQueryWrapper<TrainingAttachment>()
        .eq(TrainingAttachment::getTrainingPlanId, trainingPlanId)
        .eq(TrainingAttachment::getStatus, 1)
        .orderByAsc(TrainingAttachment::getSortOrder)
        .orderByAsc(TrainingAttachment::getTrainingAttachmentId));
    Set<Long> registeredFileInfoIds = new LinkedHashSet<>();
    int fallbackSortOrder = 0;
    for (TrainingAttachment attachment : attachments) {
      if (attachment.getFileInfoId() == null || !registeredFileInfoIds.add(attachment.getFileInfoId())) {
        continue;
      }
      ArchiveAsset asset = new ArchiveAsset();
      asset.setArchiveVersionId(archiveVersionId);
      asset.setSourceAttachmentId(attachment.getTrainingAttachmentId());
      asset.setAssetType(archiveAssetType(attachment.getAttachmentType()));
      asset.setFileInfoId(attachment.getFileInfoId());
      asset.setFileNameSnapshot(StringUtils.hasText(attachment.getAttachmentName())
                                  ? attachment.getAttachmentName()
                                  : "资料-" + attachment.getTrainingAttachmentId());
      asset.setSortOrder(attachment.getSortOrder() == null ? fallbackSortOrder : attachment.getSortOrder());
      archiveAssetMapper.insert(asset);
      fallbackSortOrder++;
    }
  }

  /**
   * 培训资料类型收敛到 {@code sts_archive_asset.asset_type} 的声明域（NOTICE/ATTENDANCE/IMAGE/VIDEO/SUMMARY/OTHER）。
   * <p>不新增迁移、不改列注释；来源类型仍可通过 {@code source_attachment_id} 与档案事实中的
   * {@code attachmentType} 追溯。
   */
  private static String archiveAssetType(String attachmentType) {
    return IMAGE_ATTACHMENT_TYPES.contains(attachmentType) ? attachmentType : "OTHER";
  }

  /** 快照哈希必须覆盖参训人事实，否则事实变化后归档版本无法被判定为不同。 */
  private static String participantHashInput(ArchiveParticipant row) {
    return row.getMemberSnapshotJson() + row.getLearningSnapshotJson() + row.getExamSnapshotJson()
      + row.getEvaluationSnapshotJson() + row.getRewardPenaltySnapshotJson()
      + row.getCertificateSnapshotJson();
  }

  /** {@code Map.of} 不接受 null 值，而快照字段普遍可空，因此显式构造并保留 null。 */
  private static Map<String, Object> snapshot(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  /** 无 userName 事实时退化为最小成员快照，保证快照始终非空。 */
  private static Map<String, Object> memberSnapshot(Map<Long, List<Map<String, Object>>> memberFacts,
                                                    Long userId, Long trainingParticipantId) {
    List<Map<String, Object>> rows = memberFacts.getOrDefault(trainingParticipantId, List.of());
    return rows.isEmpty() ? snapshot("userId", userId) : rows.get(0);
  }

  /** 档案事实都是 map 列表，这里统一取值并容忍空集合。 */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> facts(Object value) {
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  /** 按事实中的某个键分组，供按参训人或持证人写入快照。 */
  private static Map<Long, List<Map<String, Object>>> groupBy(List<Map<String, Object>> rows, String key) {
    Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      Object value = row.get(key);
      if (value instanceof Number number) {
        grouped.computeIfAbsent(number.longValue(), ignored -> new ArrayList<>()).add(row);
      }
    }
    return grouped;
  }

  /** 查询当前归档版本已生成的受控文件，供正式档案下载复用。 */
  private Long currentVersionFileInfoId(Long trainingPlanId) {
    ArchiveTraining archive = archiveTrainingMapper.selectOne(
      new LambdaQueryWrapper<ArchiveTraining>()
        .eq(ArchiveTraining::getTrainingPlanId, trainingPlanId));
    if (archive == null || archive.getCurrentVersionNo() == null) {
      return null;
    }
    ArchiveVersion version = archiveVersionMapper.selectOne(
      new LambdaQueryWrapper<ArchiveVersion>()
        .eq(ArchiveVersion::getTrainingArchiveId, archive.getTrainingArchiveId())
        .eq(ArchiveVersion::getVersionNo, archive.getCurrentVersionNo()));
    return version == null ? null : version.getFileInfoId();
  }

  private TrainingPlan requireEndedPlan(Long trainingPlanId) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    if (!"ENDED".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 9202, "仅已结束培训可以形成档案");
    }
    return plan;
  }

  private TrainingPlan requirePlan(Long trainingPlanId) {
    TrainingPlan plan = trainingPlanMapper.selectById(trainingPlanId);
    if (plan == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9201, "培训计划不存在");
    }
    return plan;
  }

  private Organization currentOrganization() {
    return organizationMapper.selectById(TenantContext.requireTenantId());
  }

  private BusinessException missingFacts(List<String> missingItems) {
    return new BusinessException(HttpStatus.CONFLICT, 9204,
                                 "缺少归档必需事实: " + String.join(",", missingItems));
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 9210, "快照序列化失败");
    }
  }
}
