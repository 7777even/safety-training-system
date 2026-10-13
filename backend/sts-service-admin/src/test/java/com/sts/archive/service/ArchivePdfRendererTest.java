package com.sts.archive.service;

import com.sts.archive.vo.ArchivePersonVO;
import com.sts.archive.vo.ArchiveViewVO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 档案 PDF 正本渲染：必须是可被解析的 `application/pdf`，且长内容自动分页不被截断。
 */
class ArchivePdfRendererTest {

  private final ArchivePdfRenderer renderer = new ArchivePdfRenderer();

  @Test
  @DisplayName("渲染出可被 PDFBox 打开的一期档案正本，页面为标准 A4 纵向")
  void render_producesOpenablePdf() throws Exception {
    byte[] bytes = renderer.render(sampleView(3));

    assertThat(pdfMagic(bytes)).isEqualTo("%PDF-");
    try (PDDocument document = Loader.loadPDF(bytes)) {
      assertThat(document.getNumberOfPages()).isEqualTo(1);
      PDRectangle box = document.getPage(0).getMediaBox();
      // 必须是真正的 A4（595.28 × 841.89 点），不能把 150dpi 栅格像素数当成点数。
      assertThat(box.getWidth()).isEqualTo(PDRectangle.A4.getWidth());
      assertThat(box.getHeight()).isEqualTo(PDRectangle.A4.getHeight());
    }
  }

  @Test
  @DisplayName("参训人很多时自动分页，不把内容挤在一页")
  void render_paginatesWhenContentOverflows() throws Exception {
    byte[] bytes = renderer.render(sampleView(160));

    try (PDDocument document = Loader.loadPDF(bytes)) {
      assertThat(document.getNumberOfPages()).isGreaterThan(1);
    }
  }

  @Test
  @DisplayName("个人档案渲染为可打开的 PDF")
  void renderPerson_producesOpenablePdf() throws Exception {
    ArchivePersonVO person = new ArchivePersonVO();
    person.setUserId(2001L);
    person.setUserName("张三");
    person.setUserCode("U2001");
    person.setMissingItems(List.of());
    person.setTrainings(List.of(sampleView(2)));

    byte[] bytes = renderer.renderPerson(person);

    assertThat(pdfMagic(bytes)).isEqualTo("%PDF-");
    try (PDDocument document = Loader.loadPDF(bytes)) {
      assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
    }
  }

  @Test
  @DisplayName("无培训记录的个人档案同样产出 PDF，不退化成空文件")
  void renderPerson_withoutTrainings_stillProducesPdf() throws Exception {
    ArchivePersonVO person = new ArchivePersonVO();
    person.setUserId(2002L);
    person.setUserName("李四");
    person.setMissingItems(List.of("RESULTS"));
    person.setTrainings(List.of());

    byte[] bytes = renderer.renderPerson(person);

    assertThat(pdfMagic(bytes)).isEqualTo("%PDF-");
    assertThat(bytes.length).isGreaterThan(1000);
  }

  @Test
  @DisplayName("证书区只呈现数量，不把证书编号与持证人 ID 印进档案文件")
  void certificateSummary_exposesCountOnly() {
    String summary = ArchivePdfRenderer.certificateSummary(sampleView(3));

    assertThat(summary).isEqualTo("本期已签发证书 1 张。");
    // 档案文件持久化后下载只校验 archive:training:list，证书明细必须留在受控端点上。
    assertThat(summary).doesNotContain("CERT-2026-0001").doesNotContain("2001").doesNotContain("4001");
  }

  @Test
  @DisplayName("缺证书权限与本期无证书给出可区分的说明")
  void certificateSummary_distinguishesMissingPermissionAndEmpty() {
    ArchiveViewVO missing = sampleView(1);
    missing.setCertificates(null);
    ArchiveViewVO empty = sampleView(1);
    empty.setCertificates(List.of());

    assertThat(ArchivePdfRenderer.certificateSummary(missing)).contains("certificate:cert:list");
    assertThat(ArchivePdfRenderer.certificateSummary(empty)).isEqualTo("（无已签发证书）");
  }

  private static String pdfMagic(byte[] bytes) {
    return new String(bytes, 0, 5, StandardCharsets.US_ASCII);
  }

  /** 构造覆盖全部章节的档案事实，行数由参训人数控制以便验证分页。 */
  private static ArchiveViewVO sampleView(int participantCount) {
    ArchiveViewVO view = new ArchiveViewVO();
    view.setTrainingPlanId(1001L);
    view.setPlanCode("TP-2026-001");
    view.setPlanName("入职安全培训");
    view.setPublishStatus("ENDED");
    view.setOrganizationId(1L);
    view.setOrganizationName("示范企业");
    view.setParticipantCount((long) participantCount);
    view.setMissingItems(List.of());
    view.setArchiveFileIsPdf(true);
    view.setFileInfoId(88L);
    view.setPlan(snapshot("trainingPlanId", 1001L, "planCode", "TP-2026-001", "planName", "入职安全培训",
                          "publishStatus", "ENDED", "startTime", LocalDateTime.of(2026, 9, 1, 9, 0),
                          "endTime", LocalDateTime.of(2026, 9, 3, 18, 0),
                          "endedTime", LocalDateTime.of(2026, 9, 3, 18, 30),
                          "location", "一号车间", "trainingNature", "INITIAL",
                          "applicableRequiredHours", new BigDecimal("8"), "trainingTypeId", 3L,
                          "trainingTypeName", "入职三级教育"));
    view.setOrganization(snapshot("organizationId", 1L, "organizationName", "示范企业", "orgCode", "DEMO"));
    view.setContents(List.of(snapshot("planContentId", 5001L, "planStageId", 3001L, "stageName", "线下阶段",
                                      "contentName", "线下实操", "contentType", "OFFLINE", "isRequired", 1,
                                      "isScored", 1, "scoreWeight", new BigDecimal("0.4"),
                                      "requiredHours", new BigDecimal("4"), "sortOrder", 1)));
    List<Map<String, Object>> participants = new ArrayList<>();
    List<Map<String, Object>> attendance = new ArrayList<>();
    List<Map<String, Object>> scores = new ArrayList<>();
    List<Map<String, Object>> evaluations = new ArrayList<>();
    for (int i = 1; i <= participantCount; i++) {
      long participantId = 7000L + i;
      long userId = 2000L + i;
      participants.add(snapshot("trainingParticipantId", participantId, "userId", userId,
                                "userName", "学员" + i, "userCode", "U" + userId));
      attendance.add(snapshot("trainingParticipantId", participantId, "userId", userId,
                              "userName", "学员" + i, "signInStatus", i % 2 == 0 ? "SIGNED" : "NOT_RECORDED",
                              "offlineScore", new BigDecimal("88.00"),
                              "recordedTime", LocalDateTime.of(2026, 9, 3, 16, 0)));
      scores.add(snapshot("trainingParticipantId", participantId, "userId", userId, "userName", "学员" + i,
                          "examScore", new BigDecimal("92.00"), "courseScore", new BigDecimal("85.00"),
                          "offlineScore", new BigDecimal("88.00"), "compositeScore", new BigDecimal("88.50"),
                          "resultStatus", "PASSED"));
      evaluations.add(snapshot("trainingParticipantId", participantId, "userId", userId, "userName", "学员" + i,
                               "compositeScore", new BigDecimal("88.50"), "resultStatus", "PASSED"));
    }
    view.setParticipants(participants);
    view.setAttendance(attendance);
    view.setScores(scores);
    view.setEvaluations(evaluations);
    view.setRewardPenalties(List.of(snapshot("rewardPenaltyId", 6001L, "trainingParticipantId", 7001L,
                                             "recordType", "REWARD", "title", "实操表现优秀",
                                             "description", "现场操作规范",
                                             "scoreChange", new BigDecimal("5.00"),
                                             "occurredDate", java.time.LocalDate.of(2026, 9, 3),
                                             "evidenceFileId", 9001L, "status", 1)));
    view.setMaterials(List.of(snapshot("trainingAttachmentId", 8001L, "attachmentType", "IMAGE",
                                       "attachmentName", "现场照片", "description", "实操现场",
                                       "fileInfoId", 9101L, "planStageId", 3001L, "stageName", "线下阶段",
                                       "sortOrder", 1)));
    view.setAttachments(List.of(snapshot("trainingAttachmentId", 8002L, "attachmentType", "GENERAL",
                                         "attachmentName", "培训签到表", "description", "纸质签到表扫描件",
                                         "fileInfoId", 9102L, "planStageId", 3001L, "stageName", "线下阶段",
                                         "sortOrder", 2)));
    view.setCertificates(List.of(snapshot("certificateId", 4001L, "certificateNo", "CERT-2026-0001",
                                           "holderUserId", 2001L, "sourceType", "TRAINING", "sourceId", 1001L,
                                           "issuedTime", LocalDateTime.of(2026, 9, 4, 10, 0),
                                           "certificateStatus", "ISSUED")));
    view.setStatistics(snapshot("participantCount", (long) participantCount, "completedCount",
                                (long) participantCount, "passedCount", (long) participantCount, "failedCount", 0L,
                                "incompleteCount", 0L, "passRate", new BigDecimal("1.0000"),
                                "averageCompositeScore", new BigDecimal("88.50"), "contentCount", 1L,
                                "requiredContentCount", 1L, "onlineHoursTotal", new BigDecimal("0.00"),
                                "offlineHoursTotal", new BigDecimal("64.00"), "imageCount", 1L,
                                "attachmentCount", 1L, "materialCount", 2L, "rewardCount", 1L, "penaltyCount", 0L,
                                "signedCount", participantCount / 2, "notRecordedCount",
                                participantCount - participantCount / 2));
    return view;
  }

  private static Map<String, Object> snapshot(Object... keyValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }
}
