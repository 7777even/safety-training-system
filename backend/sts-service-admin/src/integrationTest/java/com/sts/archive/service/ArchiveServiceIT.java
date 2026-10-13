package com.sts.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.archive.dto.ArchiveCreateFromPlanRequest;
import com.sts.archive.entity.ArchiveAsset;
import com.sts.archive.entity.ArchiveParticipant;
import com.sts.archive.entity.ArchiveTraining;
import com.sts.archive.entity.ArchiveVersion;
import com.sts.archive.mapper.ArchiveAssetMapper;
import com.sts.archive.mapper.ArchiveParticipantMapper;
import com.sts.archive.mapper.ArchiveTrainingMapper;
import com.sts.archive.mapper.ArchiveVersionMapper;
import com.sts.archive.support.ArchiveServiceItConfig;
import com.sts.archive.vo.ArchivePersonVO;
import com.sts.archive.vo.ArchiveViewVO;
import com.sts.common.exception.BusinessException;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.TrainingAttachment;
import com.sts.training.entity.TrainingOfflineScore;
import com.sts.training.entity.TrainingOfflineSession;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = ArchiveServiceItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ArchiveServiceIT extends AbstractMysqlIntegrationTest {

  private static final BigDecimal ZERO_HOURS = new BigDecimal("0.00");

  @Autowired
  private ArchiveService archiveService;
  @Autowired
  private TrainingPlanMapper trainingPlanMapper;
  @Autowired
  private TrainingTypeMapper trainingTypeMapper;
  @Autowired
  private TrainingPlanStageMapper planStageMapper;
  @Autowired
  private TrainingPlanContentMapper planContentMapper;
  @Autowired
  private TrainingParticipantMapper participantMapper;
  @Autowired
  private TrainingOfflineSessionMapper offlineSessionMapper;
  @Autowired
  private TrainingOfflineScoreMapper offlineScoreMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private SysUserMapper userMapper;
  @Autowired
  private SysFileInfoMapper fileInfoMapper;
  @Autowired
  private TrainingAttachmentMapper attachmentMapper;
  @Autowired
  private TrainingRewardPenaltyMapper rewardMapper;
  @Autowired
  private ArchiveTrainingMapper archiveTrainingMapper;
  @Autowired
  private ArchiveVersionMapper archiveVersionMapper;
  @Autowired
  private ArchiveAssetMapper archiveAssetMapper;
  @Autowired
  private ArchiveParticipantMapper archiveParticipantMapper;
  @Autowired
  private GeneratedFileStore generatedFileStore;

  @Test
  @DisplayName("createFromPlan：非 ENDED 计划 409/9202，不落档案")
  void createFromPlan_rejectsRunning() {
    Long planId = insertPlan("RUNNING", "IT-ARCH-RUN");
    ArchiveCreateFromPlanRequest req = request(planId, "IT-ARCH-RUN");
    assertThatThrownBy(() -> archiveService.createFromPlan(req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 9202);
  }

  @Test
  @DisplayName("createFromPlan：ENDED 且结果完整时生成并回读正式档案文件")
  void createFromPlan_persistsGeneratedFile() {
    Long planId = insertPlan("ENDED", "IT-ARCH-END");
    ArchiveCreateFromPlanRequest req = request(planId, "IT-ARCH-END");
    archiveService.createFromPlan(req);

    ArchiveViewVO view = archiveService.getByPlan(planId, false);
    assertThat(view.getMissingItems()).isEmpty();
    assertThat(view.getFileInfoId()).isEqualTo(88L);
  }

  @Test
  @DisplayName("getByPlan：真实计划查询不再产生 ATTENDANCE 缺失项")
  void getByPlan_doesNotReturnAttendance() {
    Long planId = insertPlan("ENDED", "IT-ARCH-VIEW");
    ArchiveViewVO view = archiveService.getByPlan(planId, false);
    assertThat(view.getTrainingPlanId()).isEqualTo(planId);
    assertThat(view.getMissingItems()).doesNotContain("ATTENDANCE").isEmpty();
    assertThat(view.getPublishStatus()).isEqualTo("ENDED");
  }

  @Test
  @DisplayName("三视角共用同一份事实：一期、企业、个人返回一致的内容与统计")
  void threeViewsShareSameFacts() {
    Long planId = insertPlan("ENDED", "IT-ARCH-SHARE");
    Long stageId = insertStage(planId);
    insertOfflineContent(planId, stageId);
    Long signedUserId = insertUser("IT-ARCH-U1");
    Long unsignedUserId = insertUser("IT-ARCH-U2");
    Long signedParticipantId = insertParticipant(planId, signedUserId, "PASSED", new BigDecimal("88.00"));
    Long unsignedParticipantId = insertParticipant(planId, unsignedUserId, "INCOMPLETE", null);
    insertOfflineScore(planId, stageId, signedParticipantId);

    ArchiveViewVO byPlan = archiveService.getByPlan(planId, false);
    ArchiveViewVO fromEnterprise = archiveService.listEnterprise(null, false).stream()
      .filter(view -> planId.equals(view.getTrainingPlanId())).findFirst().orElseThrow();
    ArchivePersonVO person = archiveService.getPerson(signedUserId, false);

    assertThat(fromEnterprise.getStatistics()).isEqualTo(byPlan.getStatistics());
    assertThat(fromEnterprise.getAttendance()).isEqualTo(byPlan.getAttendance());
    ArchiveViewVO fromPerson = person.getTrainings().stream()
      .filter(view -> planId.equals(view.getTrainingPlanId())).findFirst().orElseThrow();
    assertThat(fromPerson.getStatistics()).isEqualTo(byPlan.getStatistics());
    assertThat(fromPerson.getContents()).isEqualTo(byPlan.getContents());

    Map<String, Object> statistics = statistics(byPlan);
    assertThat(statistics).containsEntry("participantCount", 2L).containsEntry("signedCount", 1)
      .containsEntry("notRecordedCount", 1).containsEntry("passedCount", 1L).containsEntry("incompleteCount", 1L);
    // 通过率只按已判定人数（PASSED + FAILED）计算，这里只有 1 人已判定。
    assertThat((BigDecimal) statistics.get("passRate")).isEqualByComparingTo("1.0000");
    assertThat(attendanceStatus(byPlan, signedParticipantId)).isEqualTo("SIGNED");
    assertThat(attendanceStatus(byPlan, unsignedParticipantId)).isEqualTo("NOT_RECORDED");
  }

  @Test
  @DisplayName("停用资料不作为档案事实：三视角与统计剔除、归档素材同步收敛，改回启用后恢复")
  void disabledMaterialIsNotArchiveFact() {
    Long planId = insertPlan("ENDED", "IT-ARCH-OFF");
    Long stageId = insertStage(planId);
    Long userId = insertUser("IT-ARCH-U5");
    insertParticipant(planId, userId, "PASSED", new BigDecimal("86.00"));
    Long imageAttachmentId = insertAttachment(planId, stageId, "IMAGE",
      insertFileInfo("现场照片.png", "png"), "现场照片", 1, 1);
    Long docAttachmentId = insertAttachment(planId, stageId, "GENERAL",
      insertFileInfo("制度.pdf", "pdf"), "制度附件", 2, 1);

    ArchiveViewVO enabled = archiveService.getByPlan(planId, false);
    assertThat(materialNames(enabled)).containsExactly("现场照片");
    assertThat(attachmentNames(enabled)).containsExactly("制度附件");
    assertThat(statistics(enabled)).containsEntry("imageCount", 1L).containsEntry("materialCount", 2L);

    // 停用一般资料：附件清单与资料合计收敛，影像记录不受影响。
    setAttachmentStatus(docAttachmentId, 0);
    ArchiveViewVO docOff = archiveService.getByPlan(planId, false);
    assertThat(attachmentNames(docOff)).isEmpty();
    assertThat(materialNames(docOff)).containsExactly("现场照片");
    assertThat(statistics(docOff)).containsEntry("attachmentCount", 0L).containsEntry("materialCount", 1L);

    // 归档登记的资料集合必须与在线三视角一致，否则档案文件会比页面多出已停用事实。
    archiveService.createFromPlan(request(planId, "IT-ARCH-OFF"));
    assertThat(assetsOf(latestVersion(planId))).hasSize(1);

    // 再停用影像资料：影像记录与影像计数一并收敛。
    setAttachmentStatus(imageAttachmentId, 0);
    ArchiveViewVO allOff = archiveService.getByPlan(planId, false);
    assertThat(materialNames(allOff)).isEmpty();
    assertThat(statistics(allOff)).containsEntry("imageCount", 0L).containsEntry("materialCount", 0L);

    // 记录本身不因该口径被删除，工作台可继续维护并改回启用。
    assertThat(attachmentMapper.selectById(docAttachmentId).getStatus()).isZero();
    assertThat(attachmentMapper.selectById(imageAttachmentId).getStatus()).isZero();

    setAttachmentStatus(docAttachmentId, 1);
    setAttachmentStatus(imageAttachmentId, 1);
    ArchiveViewVO restored = archiveService.getByPlan(planId, false);
    assertThat(materialNames(restored)).containsExactly("现场照片");
    assertThat(attachmentNames(restored)).containsExactly("制度附件");
    assertThat(statistics(restored)).containsEntry("imageCount", 1L).containsEntry("materialCount", 2L);
  }

  /** 档案按人员查询要求该人员真实存在；IT 容器只跑 DDL 快照，没有用户种子数据。 */
  private Long insertUser(String userCode) {
    SysUser user = new SysUser();
    user.setOrganizationId(demoTenantId());
    user.setUserCode(userCode);
    user.setUserName("档案IT-" + userCode);
    user.setStatus(1);
    userMapper.insert(user);
    return user.getUserId();
  }

  @Test
  @DisplayName("归档写实：素材登记、参训人快照非空、事实变化后哈希变化")
  void createFromPlan_writesRealSnapshotsAndAssets() {
    Long planId = insertPlan("ENDED", "IT-ARCH-ASSET");
    Long stageId = insertStage(planId);
    Long userId = insertUser("IT-ARCH-U3");
    Long participantId = insertParticipant(planId, userId, "PASSED", new BigDecimal("90.00"));
    // sts_training_attachment 有 uk_plan_file_deleted(plan_id, file_info_id, deleted)，
    // 同一计划的同一文件无法被两条资料引用，所以这里用两个不同文件；
    // "同一文件只登记一条素材" 的去重分支由单测覆盖。
    Long imageFileId = insertFileInfo("现场照片.png", "png");
    Long docFileId = insertFileInfo("签到表.pdf", "pdf");
    insertAttachment(planId, stageId, "IMAGE", imageFileId, "现场照片", 1);
    insertAttachment(planId, stageId, "GENERAL", docFileId, "签到表", 2);
    insertReward(planId, participantId, "REWARD", "实操表现优秀");

    archiveService.createFromPlan(request(planId, "IT-ARCH-ASSET"));

    ArchiveVersion v1 = latestVersion(planId);
    List<ArchiveAsset> v1Assets = assetsOf(v1);
    assertThat(v1Assets).hasSize(2);
    ArchiveAsset imageAsset = v1Assets.stream()
      .filter(asset -> imageFileId.equals(asset.getFileInfoId())).findFirst().orElseThrow();
    assertThat(imageAsset.getAssetType()).isEqualTo("IMAGE");
    assertThat(imageAsset.getFileNameSnapshot()).isEqualTo("现场照片");
    assertThat(imageAsset.getSourceAttachmentId()).isNotNull();
    // 非影像类型收敛到列的声明域 OTHER，不写入 attachmentType 原值。
    assertThat(v1Assets.stream().filter(asset -> docFileId.equals(asset.getFileInfoId()))
             .findFirst().orElseThrow().getAssetType()).isEqualTo("OTHER");

    ArchiveParticipant row = participantsOf(v1).get(0);
    assertThat(row.getRewardPenaltySnapshotJson()).isNotEqualTo("{}").contains("实操表现优秀");
    assertThat(row.getMemberSnapshotJson()).contains("IT-ARCH-U3");
    assertThat(row.getCertificateSnapshotJson()).isEqualTo("[]");

    // 事实变化后再次归档：版本号递增、哈希变化，素材按版本登记。
    TrainingParticipant changed = participantMapper.selectById(participantId);
    changed.setCompositeScore(new BigDecimal("95.00"));
    participantMapper.updateById(changed);
    archiveService.createFromPlan(request(planId, "IT-ARCH-ASSET"));

    ArchiveVersion v2 = latestVersion(planId);
    assertThat(v2.getVersionNo()).isEqualTo(v1.getVersionNo() + 1);
    assertThat(v2.getSnapshotHash()).isNotEqualTo(v1.getSnapshotHash());
    assertThat(assetsOf(v2)).hasSize(2);

    // 归档落盘的是 PDF 正本，不再是 JSON。
    ArgumentCaptor<byte[]> fileCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(generatedFileStore, org.mockito.Mockito.atLeastOnce()).store(
      fileCaptor.capture(), org.mockito.ArgumentMatchers.endsWith(".pdf"),
      org.mockito.ArgumentMatchers.eq("archive"), org.mockito.ArgumentMatchers.eq("application/pdf"),
      org.mockito.ArgumentMatchers.eq("pdf"));
    assertThat(new String(fileCaptor.getValue(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
  }

  private ArchiveVersion latestVersion(Long planId) {
    ArchiveTraining archive = archiveTrainingMapper.selectOne(
      new LambdaQueryWrapper<ArchiveTraining>().eq(ArchiveTraining::getTrainingPlanId, planId));
    return archiveVersionMapper.selectOne(new LambdaQueryWrapper<ArchiveVersion>()
      .eq(ArchiveVersion::getTrainingArchiveId, archive.getTrainingArchiveId())
      .eq(ArchiveVersion::getVersionNo, archive.getCurrentVersionNo()));
  }

  private List<ArchiveAsset> assetsOf(ArchiveVersion version) {
    return archiveAssetMapper.selectList(new LambdaQueryWrapper<ArchiveAsset>()
      .eq(ArchiveAsset::getArchiveVersionId, version.getArchiveVersionId()));
  }

  private List<ArchiveParticipant> participantsOf(ArchiveVersion version) {
    return archiveParticipantMapper.selectList(new LambdaQueryWrapper<ArchiveParticipant>()
      .eq(ArchiveParticipant::getArchiveVersionId, version.getArchiveVersionId()));
  }

  private Long insertFileInfo(String fileName, String suffix) {
    SysFileInfo file = new SysFileInfo();
    file.setFileName(fileName);
    file.setBucketName("sts");
    file.setObjectName("archive-it/" + fileName);
    file.setFileSize(1024L);
    file.setFileSuffix(suffix);
    file.setFileUrl("/archive-it/" + fileName);
    fileInfoMapper.insert(file);
    return file.getFileInfoId();
  }

  private void insertAttachment(Long planId, Long stageId, String type, Long fileInfoId, String name, int sortOrder) {
    insertAttachment(planId, stageId, type, fileInfoId, name, sortOrder, 1);
  }

  private Long insertAttachment(Long planId, Long stageId, String type, Long fileInfoId, String name,
                                int sortOrder, int status) {
    TrainingAttachment attachment = new TrainingAttachment();
    attachment.setTrainingPlanId(planId);
    attachment.setPlanStageId(stageId);
    attachment.setAttachmentType(type);
    attachment.setFileInfoId(fileInfoId);
    attachment.setAttachmentName(name);
    attachment.setSortOrder(sortOrder);
    attachment.setStatus(status);
    attachmentMapper.insert(attachment);
    return attachment.getTrainingAttachmentId();
  }

  /** 工作台停用/启用资料：档案事实应随之收敛，记录本身保留。 */
  private void setAttachmentStatus(Long attachmentId, int status) {
    TrainingAttachment attachment = attachmentMapper.selectById(attachmentId);
    attachment.setStatus(status);
    attachmentMapper.updateById(attachment);
  }

  private void insertReward(Long planId, Long participantId, String recordType, String title) {
    TrainingRewardPenalty reward = new TrainingRewardPenalty();
    reward.setOrganizationId(demoTenantId());
    reward.setTrainingPlanId(planId);
    reward.setTrainingParticipantId(participantId);
    reward.setRecordType(recordType);
    reward.setTitle(title);
    reward.setStatus(1);
    rewardMapper.insert(reward);
  }

  private Long insertPlan(String publishStatus, String code) {
    Long orgId = demoTenantId();
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode(code + "-TYPE");
    type.setTypeName("档案IT类型");
    type.setStatus(1);
    type.setDescription("IT");
    trainingTypeMapper.insert(type);
    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(orgId);
    plan.setPlanCode(code);
    plan.setPlanName("档案IT-" + code);
    plan.setTrainingTypeId(type.getTrainingTypeId());
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now().minusDays(2));
    plan.setEndTime(LocalDateTime.now().minusDays(1));
    plan.setPublishStatus(publishStatus);
    plan.setStatus(1);
    trainingPlanMapper.insert(plan);
    return plan.getTrainingPlanId();
  }

  private Long insertStage(Long planId) {
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(planId);
    stage.setStageName("线下阶段");
    stage.setStageType("OFFLINE");
    stage.setSortOrder(1);
    stage.setStageStatus("PENDING");
    planStageMapper.insert(stage);
    return stage.getPlanStageId();
  }

  /** 含线下内容的计划才适用签到情况，这里固定写一条线下内容。 */
  private void insertOfflineContent(Long planId, Long stageId) {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanStageId(stageId);
    content.setContentType("OFFLINE");
    content.setContentName("线下实操");
    content.setIsRequired(1);
    content.setIsScored(1);
    content.setSortOrder(1);
    content.setContentStatus("COMPLETED");
    planContentMapper.insert(content);
  }

  private Long insertParticipant(Long planId, Long userId, String resultStatus, BigDecimal offlineScore) {
    Long orgId = demoTenantId();
    TrainingParticipant participant = new TrainingParticipant();
    participant.setOrganizationId(orgId);
    participant.setTrainingPlanId(planId);
    participant.setUserId(userId);
    participant.setParticipantOrgId(orgId);
    participant.setJoinSource("ASSIGN");
    participant.setJoinedTime(LocalDateTime.now().minusDays(2));
    participant.setParticipantStatus("JOINED");
    participant.setOnlineHours(ZERO_HOURS);
    participant.setOfflineHours(ZERO_HOURS);
    participant.setOfflineScore(offlineScore);
    participant.setResultStatus(resultStatus);
    participantMapper.insert(participant);
    return participant.getTrainingParticipantId();
  }

  /** 教师录入线下成绩即视为到场，这里走真实场次 + 成绩记录。 */
  private void insertOfflineScore(Long planId, Long stageId, Long participantId) {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setTrainingPlanId(planId);
    session.setPlanStageId(stageId);
    session.setSessionName("线下实操场次");
    session.setLocation("车间");
    session.setStartTime(LocalDateTime.now().minusHours(5));
    session.setEndTime(LocalDateTime.now().minusHours(2));
    session.setSessionStatus("FINISHED");
    session.setStatus(1);
    offlineSessionMapper.insert(session);

    TrainingOfflineScore score = new TrainingOfflineScore();
    score.setOrganizationId(demoTenantId());
    score.setTrainingParticipantId(participantId);
    score.setOfflineSessionId(session.getOfflineSessionId());
    score.setScoreName("线下实操评分");
    score.setScore(new BigDecimal("88.00"));
    score.setMaxScore(new BigDecimal("100.00"));
    score.setScoreSource("MANUAL");
    score.setScoredTime(LocalDateTime.now().minusHours(3));
    offlineScoreMapper.insert(score);
  }

  private static ArchiveCreateFromPlanRequest request(Long planId, String code) {
    ArchiveCreateFromPlanRequest req = new ArchiveCreateFromPlanRequest();
    req.setTrainingPlanId(planId);
    req.setArchiveCode(code);
    req.setVersionReason("IT");
    return req;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> statistics(ArchiveViewVO view) {
    return (Map<String, Object>) view.getStatistics();
  }

  /** 影像记录（IMAGE/VIDEO）的资料名称。 */
  @SuppressWarnings("unchecked")
  private static List<String> materialNames(ArchiveViewVO view) {
    return ((List<Map<String, Object>>) view.getMaterials()).stream()
      .map(row -> (String) row.get("attachmentName")).toList();
  }

  /** 附件清单（GENERAL/EVALUATION）的资料名称。 */
  @SuppressWarnings("unchecked")
  private static List<String> attachmentNames(ArchiveViewVO view) {
    return ((List<Map<String, Object>>) view.getAttachments()).stream()
      .map(row -> (String) row.get("attachmentName")).toList();
  }

  @SuppressWarnings("unchecked")
  private static String attendanceStatus(ArchiveViewVO view, Long trainingParticipantId) {
    return ((List<Map<String, Object>>) view.getAttendance()).stream()
      .filter(row -> trainingParticipantId.equals(((Number) row.get("trainingParticipantId")).longValue()))
      .map(row -> (String) row.get("signInStatus"))
      .findFirst().orElseThrow();
  }
}
