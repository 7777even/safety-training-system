package com.sts.archive.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.admin.storage.StoredDownload;
import com.sts.archive.dto.ArchiveCreateFromPlanRequest;
import com.sts.archive.entity.ArchiveAsset;
import com.sts.archive.entity.ArchiveParticipant;
import com.sts.archive.entity.ArchiveTraining;
import com.sts.archive.entity.ArchiveVersion;
import com.sts.archive.mapper.ArchiveAssetMapper;
import com.sts.archive.mapper.ArchiveParticipantMapper;
import com.sts.archive.mapper.ArchiveTrainingMapper;
import com.sts.archive.mapper.ArchiveVersionMapper;
import com.sts.archive.vo.ArchiveViewVO;
import com.sts.certificate.entity.CertificateCertificate;
import com.sts.certificate.mapper.CertificateCertificateMapper;
import com.sts.common.exception.BusinessException;
import com.sts.evaluation.service.EvaluationFormService;
import com.sts.evaluation.service.EvaluationRecordService;
import com.sts.evaluation.service.ExpertInspectionService;
import com.sts.evaluation.vo.EvaluationFormVersionDetailVO;
import com.sts.evaluation.vo.EvaluationItemVO;
import com.sts.evaluation.vo.EvaluationRecordItemVO;
import com.sts.evaluation.vo.EvaluationRecordVO;
import com.sts.evaluation.vo.ExpertInspectionSummaryVO;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.TrainingAttachment;
import com.sts.training.entity.TrainingOfflineScore;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 归档前置仅使用已结束培训和正式结果，不能再虚构签到缺失；档案事实按三视角共用一份组装。 */
class ArchiveServiceImplTest {

  /**
   * 以最小可用依赖装配被测服务；未显式 stub 的查询由 Mockito 返回空集合。
   */
  private static Fixture fixture() {
    return new Fixture();
  }

  private static final class Fixture {
    final TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    final TrainingParticipantMapper participantMapper = mock(TrainingParticipantMapper.class);
    final TrainingRewardPenaltyMapper rewardMapper = mock(TrainingRewardPenaltyMapper.class);
    final TrainingAttachmentMapper attachmentMapper = mock(TrainingAttachmentMapper.class);
    final TrainingPlanContentMapper planContentMapper = mock(TrainingPlanContentMapper.class);
    final TrainingPlanStageMapper planStageMapper = mock(TrainingPlanStageMapper.class);
    final TrainingOfflineScoreMapper offlineScoreMapper = mock(TrainingOfflineScoreMapper.class);
    final CertificateCertificateMapper certificateMapper = mock(CertificateCertificateMapper.class);
    final SysUserMapper userMapper = mock(SysUserMapper.class);
    final SysFileInfoMapper fileInfoMapper = mock(SysFileInfoMapper.class);
    final ArchiveTrainingMapper archiveMapper = mock(ArchiveTrainingMapper.class);
    final ArchiveVersionMapper versionMapper = mock(ArchiveVersionMapper.class);
    final ArchiveParticipantMapper archiveParticipantMapper = mock(ArchiveParticipantMapper.class);
    final ArchiveAssetMapper assetMapper = mock(ArchiveAssetMapper.class);
    final GeneratedFileStore generatedFileStore = mock(GeneratedFileStore.class);
    final ExpertInspectionService expertInspectionService = mock(ExpertInspectionService.class);
    final EvaluationRecordService evaluationRecordService = mock(EvaluationRecordService.class);
    final EvaluationFormService evaluationFormService = mock(EvaluationFormService.class);

    ArchiveServiceImpl service() {
      return new ArchiveServiceImpl(
        archiveMapper, versionMapper, archiveParticipantMapper,
        planMapper, participantMapper, rewardMapper, attachmentMapper,
        planContentMapper, planStageMapper, offlineScoreMapper, certificateMapper,
        mock(TrainingTypeMapper.class), mock(OrganizationMapper.class), userMapper, fileInfoMapper,
        assetMapper, new ObjectMapper(), generatedFileStore, new ArchivePdfRenderer(),
        expertInspectionService, evaluationRecordService, evaluationFormService);
    }
  }

  private static TrainingPlan endedPlan(long planId) {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(planId);
    plan.setPublishStatus("ENDED");
    return plan;
  }

  @Test
  void planArchiveIncludesSubmittedExpertInspectionAnswersOnly() {
    Fixture f = fixture();
    when(f.planMapper.selectById(1L)).thenReturn(endedPlan(1L));
    ExpertInspectionSummaryVO submitted = new ExpertInspectionSummaryVO();
    submitted.setPlanContentId(11L);
    submitted.setContentName("安全课程");
    submitted.setFormVersionId(21L);
    submitted.setVersionNo(2);
    submitted.setEvaluationRecordId(31L);
    submitted.setEvaluatorName("专家甲");
    ExpertInspectionSummaryVO pending = new ExpertInspectionSummaryVO();
    pending.setPlanContentId(12L);
    when(f.expertInspectionService.listByPlan(1L)).thenReturn(List.of(submitted, pending));
    EvaluationRecordVO record = new EvaluationRecordVO();
    record.setEvaluationRecordId(31L);
    record.setSummary("教学清晰");
    EvaluationRecordItemVO answer = new EvaluationRecordItemVO();
    answer.setEvaluationItemId(41L);
    answer.setTextValue("材料完整");
    record.setItems(List.of(answer));
    when(f.evaluationRecordService.get(31L)).thenReturn(record);
    EvaluationItemVO item = new EvaluationItemVO();
    item.setEvaluationItemId(41L);
    item.setItemName("课程材料");
    item.setItemType("TEXT");
    EvaluationFormVersionDetailVO version = new EvaluationFormVersionDetailVO();
    version.setItems(List.of(item));
    when(f.evaluationFormService.getVersionDetail(21L)).thenReturn(version);

    ArchiveViewVO view = f.service().getByPlan(1L, false);

    assertThat(view.getExpertInspections()).asList().hasSize(1);
    assertThat(new ObjectMapper().valueToTree(view.getExpertInspections()).toString())
      .contains("安全课程", "专家甲", "课程材料", "材料完整", "教学清晰")
      .doesNotContain("12");
  }

  private static TrainingParticipant participant(long id, long userId, String resultStatus) {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(id);
    participant.setUserId(userId);
    participant.setResultStatus(resultStatus);
    return participant;
  }

  private static TrainingPlanContent offlineContent() {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanStageId(1L);
    content.setContentType("OFFLINE");
    content.setContentName("线下实操");
    return content;
  }

  private static TrainingPlanStage offlineStage() {
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(1L);
    stage.setStageName("线下阶段");
    return stage;
  }

  private static TrainingAttachment attachment(long id, String type, long fileInfoId, String name, int sortOrder) {
    TrainingAttachment attachment = new TrainingAttachment();
    attachment.setTrainingAttachmentId(id);
    attachment.setAttachmentType(type);
    attachment.setFileInfoId(fileInfoId);
    attachment.setAttachmentName(name);
    attachment.setSortOrder(sortOrder);
    return attachment;
  }

  private static ArchiveCreateFromPlanRequest request(long planId, String archiveCode) {
    ArchiveCreateFromPlanRequest request = new ArchiveCreateFromPlanRequest();
    request.setTrainingPlanId(planId);
    request.setArchiveCode(archiveCode);
    request.setVersionReason("单元测试");
    return request;
  }

  @Test
  void shouldNotRequireAttendanceForEndedPlanWithCompleteResults() {
    Fixture f = fixture();
    TrainingPlan plan = endedPlan(1L);
    plan.setPlanCode("TP-001");
    plan.setPlanName("完整培训");
    when(f.planMapper.selectById(1L)).thenReturn(plan);
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant(10L, 100L, "PASSED")));

    ArchiveViewVO view = f.service().getByPlan(1L, false);

    assertThat(view.getMissingItems()).doesNotContain("ATTENDANCE").isEmpty();
    assertThat(view.getExpertInspections()).asList().isEmpty();
  }

  @Test
  void shouldStillReportResultsWhenEndedPlanHasIncompleteParticipant() {
    Fixture f = fixture();
    when(f.planMapper.selectById(2L)).thenReturn(endedPlan(2L));
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant(20L, 200L, "INCOMPLETE")));

    assertThat(f.service().getByPlan(2L, false).getMissingItems()).containsExactly("RESULTS");
  }

  @Test
  void shouldMarkAttendanceNotApplicableWhenPlanHasNoOfflineContent() {
    Fixture f = fixture();
    when(f.planMapper.selectById(4L)).thenReturn(endedPlan(4L));
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant(40L, 400L, "PASSED")));

    ArchiveViewVO view = f.service().getByPlan(4L, false);

    assertThat(attendanceStatus(view, 40L)).isEqualTo("NOT_APPLICABLE");
    assertThat(statistics(view)).containsEntry("signedCount", null).containsEntry("notRecordedCount", null);
  }

  @Test
  void shouldMapAttendanceFromRecordedOfflineScore() {
    Fixture f = fixture();
    when(f.planMapper.selectById(5L)).thenReturn(endedPlan(5L));
    when(f.planStageMapper.selectList(any())).thenReturn(List.of(offlineStage()));
    when(f.planContentMapper.selectList(any())).thenReturn(List.of(offlineContent()));
    TrainingParticipant signed = participant(50L, 500L, "PASSED");
    signed.setOfflineScore(new BigDecimal("88.00"));
    TrainingParticipant missing = participant(51L, 501L, "INCOMPLETE");
    when(f.participantMapper.selectList(any())).thenReturn(List.of(signed, missing));
    TrainingOfflineScore row = new TrainingOfflineScore();
    row.setTrainingParticipantId(50L);
    row.setScoredTime(LocalDateTime.of(2026, 9, 20, 10, 0));
    when(f.offlineScoreMapper.selectList(any())).thenReturn(List.of(row));

    ArchiveViewVO view = f.service().getByPlan(5L, false);

    assertThat(attendanceStatus(view, 50L)).isEqualTo("SIGNED");
    assertThat(attendanceStatus(view, 51L)).isEqualTo("NOT_RECORDED");
    assertThat(statistics(view)).containsEntry("signedCount", 1).containsEntry("notRecordedCount", 1);
    assertThat(view.getMissingItems()).containsExactly("RESULTS");
  }

  @Test
  void shouldReturnNullRatiosWhenNobodyIsDecided() {
    Fixture f = fixture();
    when(f.planMapper.selectById(6L)).thenReturn(endedPlan(6L));
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant(60L, 600L, "INCOMPLETE")));

    Map<String, Object> statistics = statistics(f.service().getByPlan(6L, false));

    assertThat(statistics).containsEntry("passRate", null).containsEntry("averageCompositeScore", null);
    assertThat(statistics).containsEntry("participantCount", 1L).containsEntry("incompleteCount", 1L);
  }

  @Test
  void shouldComputePassRateAgainstDecidedParticipantsOnly() {
    Fixture f = fixture();
    when(f.planMapper.selectById(7L)).thenReturn(endedPlan(7L));
    TrainingParticipant passed = participant(70L, 700L, "PASSED");
    passed.setCompositeScore(new BigDecimal("90.00"));
    TrainingParticipant failed = participant(71L, 701L, "FAILED");
    failed.setCompositeScore(new BigDecimal("50.00"));
    TrainingParticipant incomplete = participant(72L, 702L, "INCOMPLETE");
    when(f.participantMapper.selectList(any())).thenReturn(List.of(passed, failed, incomplete));

    Map<String, Object> statistics = statistics(f.service().getByPlan(7L, false));

    assertThat(statistics).containsEntry("passedCount", 1L).containsEntry("failedCount", 1L)
      .containsEntry("incompleteCount", 1L).containsEntry("completedCount", 2L);
    assertThat((BigDecimal) statistics.get("passRate")).isEqualByComparingTo("0.5000");
    assertThat((BigDecimal) statistics.get("averageCompositeScore")).isEqualByComparingTo("70.00");
  }

  @Test
  void shouldOmitCertificateFactsWhenCallerLacksCertificatePermission() {
    Fixture f = fixture();
    when(f.planMapper.selectById(9L)).thenReturn(endedPlan(9L));
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant(90L, 900L, "PASSED")));

    assertThat(f.service().getByPlan(9L, false).getCertificates()).isNull();
    assertThat(f.service().getByPlan(9L, true).getCertificates()).isEqualTo(List.of());
  }

  @Test
  void shouldWriteRealParticipantSnapshotsAndDeduplicateAssets() {
    Fixture f = fixture();
    TrainingPlan plan = endedPlan(11L);
    plan.setPlanCode("TP-011");
    TrainingParticipant participant = participant(110L, 1100L, "PASSED");
    participant.setTrainingPlanId(11L);
    when(f.planMapper.selectById(11L)).thenReturn(plan);
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant));

    TrainingRewardPenalty reward = new TrainingRewardPenalty();
    reward.setTrainingParticipantId(110L);
    reward.setRecordType("REWARD");
    reward.setTitle("实操表现优秀");
    reward.setScoreChange(new BigDecimal("5.00"));
    when(f.rewardMapper.selectList(any())).thenReturn(List.of(reward));

    CertificateCertificate certificate = new CertificateCertificate();
    certificate.setCertificateId(400L);
    certificate.setCertificateNo("CERT-2026-0001");
    certificate.setHolderUserId(1100L);
    certificate.setSourceType("TRAINING");
    certificate.setSourceId(11L);
    when(f.certificateMapper.selectList(any())).thenReturn(List.of(certificate));

    // 两条资料指向同一个受控文件，按唯一键语义只能登记一条档案素材。
    when(f.attachmentMapper.selectList(any())).thenReturn(List.of(
      attachment(1L, "IMAGE", 900L, "现场照片", 1),
      attachment(2L, "GENERAL", 900L, "签到表", 2)));
    when(f.archiveMapper.selectOne(any())).thenReturn(null);

    f.service().createFromPlan(request(11L, "ARC-11"));

    ArgumentCaptor<ArchiveParticipant> participantCaptor = ArgumentCaptor.forClass(ArchiveParticipant.class);
    verify(f.archiveParticipantMapper).insert(participantCaptor.capture());
    assertThat(participantCaptor.getValue().getRewardPenaltySnapshotJson())
      .isNotEqualTo("{}").contains("实操表现优秀");
    assertThat(participantCaptor.getValue().getCertificateSnapshotJson())
      .isNotEqualTo("{}").contains("CERT-2026-0001");

    ArgumentCaptor<ArchiveAsset> assetCaptor = ArgumentCaptor.forClass(ArchiveAsset.class);
    verify(f.assetMapper, times(1)).insert(assetCaptor.capture());
    assertThat(assetCaptor.getValue().getFileInfoId()).isEqualTo(900L);
    assertThat(assetCaptor.getValue().getSourceAttachmentId()).isEqualTo(1L);
    assertThat(assetCaptor.getValue().getAssetType()).isEqualTo("IMAGE");
    assertThat(assetCaptor.getValue().getFileNameSnapshot()).isEqualTo("现场照片");

    // 一期档案正本必须是 PDF，不能再落 JSON 文件。
    ArgumentCaptor<byte[]> fileCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(f.generatedFileStore).store(fileCaptor.capture(), org.mockito.ArgumentMatchers.endsWith(".pdf"),
                                      org.mockito.ArgumentMatchers.eq("archive"),
                                      org.mockito.ArgumentMatchers.eq("application/pdf"),
                                      org.mockito.ArgumentMatchers.eq("pdf"));
    assertThat(new String(fileCaptor.getValue(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
  }

  @Test
  void shouldChangeSnapshotHashWhenParticipantFactsChange() {
    Fixture f = fixture();
    TrainingPlan plan = endedPlan(12L);
    plan.setPlanCode("TP-012");
    when(f.planMapper.selectById(12L)).thenReturn(plan);
    TrainingParticipant participant = participant(120L, 1200L, "PASSED");
    participant.setTrainingPlanId(12L);
    participant.setCompositeScore(new BigDecimal("60.00"));
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant));
    when(f.archiveMapper.selectOne(any())).thenReturn(null);

    f.service().createFromPlan(request(12L, "ARC-12"));
    participant.setCompositeScore(new BigDecimal("95.00"));
    f.service().createFromPlan(request(12L, "ARC-12"));

    ArgumentCaptor<ArchiveVersion> versionCaptor = ArgumentCaptor.forClass(ArchiveVersion.class);
    verify(f.versionMapper, times(2)).insert(versionCaptor.capture());
    List<ArchiveVersion> versions = versionCaptor.getAllValues();
    assertThat(versions.get(0).getSnapshotHash())
      .isNotBlank().isNotEqualTo(versions.get(1).getSnapshotHash());
  }

  @Test
  void shouldComposePersonArchiveFileFromVisibleEndedTrainings() throws Exception {
    Fixture f = fixture();
    TrainingPlan plan = endedPlan(3L);
    plan.setPlanCode("TP-007");
    plan.setPlanName("已完成培训");
    TrainingParticipant participant = participant(70L, 100L, "PASSED");
    participant.setTrainingPlanId(3L);
    SysUser user = new SysUser();
    user.setUserId(100L);
    user.setUserName("张三");
    user.setUserCode("U100");
    when(f.planMapper.selectById(3L)).thenReturn(plan);
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant));
    when(f.userMapper.selectById(100L)).thenReturn(user);

    StoredDownload download = f.service().openPersonFile(100L);

    assertThat(download.contentType()).isEqualTo("application/pdf");
    assertThat(download.fileName()).isEqualTo("archive-person-U100.pdf");
    byte[] content = download.resource().getContentAsByteArray();
    assertThat(content.length).isGreaterThan(1000);
    assertThat(new String(content, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
  }

  @Test
  void shouldRejectPersonArchiveFileWhenAnyEndedTrainingIsIncomplete() {
    Fixture f = fixture();
    TrainingPlan plan = endedPlan(8L);
    TrainingParticipant participant = participant(80L, 101L, "INCOMPLETE");
    participant.setTrainingPlanId(8L);
    SysUser user = new SysUser();
    user.setUserId(101L);
    when(f.planMapper.selectById(8L)).thenReturn(plan);
    when(f.participantMapper.selectList(any())).thenReturn(List.of(participant));
    when(f.userMapper.selectById(101L)).thenReturn(user);

    assertThatThrownBy(() -> f.service().openPersonFile(101L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 9204);
  }

  @Test
  void shouldExposeCurrentArchiveVersionFileForCompletedPlan() {
    Fixture f = fixture();
    when(f.planMapper.selectById(3L)).thenReturn(endedPlan(3L));
    when(f.participantMapper.selectList(any())).thenReturn(List.of());
    ArchiveTraining archive = new ArchiveTraining();
    archive.setTrainingArchiveId(30L);
    archive.setCurrentVersionNo(2);
    ArchiveVersion version = new ArchiveVersion();
    version.setFileInfoId(88L);
    when(f.archiveMapper.selectOne(any())).thenReturn(archive);
    when(f.versionMapper.selectOne(any())).thenReturn(version);

    assertThat(f.service().getByPlan(3L, false).getFileInfoId()).isEqualTo(88L);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> attendance(ArchiveViewVO view) {
    return (List<Map<String, Object>>) view.getAttendance();
  }

  private static String attendanceStatus(ArchiveViewVO view, long trainingParticipantId) {
    return attendance(view).stream()
      .filter(row -> trainingParticipantId == ((Number) row.get("trainingParticipantId")).longValue())
      .map(row -> (String) row.get("signInStatus"))
      .findFirst().orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> statistics(ArchiveViewVO view) {
    return (Map<String, Object>) view.getStatistics();
  }
}
