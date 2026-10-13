package com.sts.archive.service;

import com.sts.archive.util.ArchiveLabels;
import com.sts.archive.vo.ArchivePersonVO;
import com.sts.archive.vo.ArchiveViewVO;
import com.sts.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 档案 PDF 正本渲染器。
 * <p>
 * 复用证书已验证的 AWT 逻辑字体路径绘制页面位图（中文由系统字体呈现），再用依赖中已有的 PDFBox
 * 把页面位图装入 PDF 文档：**零新依赖、零字体资源**。代价是 PDF 内文本不可检索/复制
 * （2026-09-24 已人工确认可接受；若要可检索文本需引入 CJK 字体资源，须另开 Change）。
 * <p>
 * 影像与附件在 PDF 中**只出清单**，二进制原件仍经受控端点另行获取。
 */
@Component
public class ArchivePdfRenderer {

  /**
   * 绘制用的栅格尺寸：A4 纵向 @150dpi 的像素数（1240×1754，四边留白 56px）。
   * <p><b>注意这是像素，不是 PDF 点数</b>：PDF 页面尺寸固定取 {@link PDRectangle#A4}（595.28×841.89 点），
   * 栅格整页铺满该页面，因此实际打印分辨率为 150dpi。把像素数直接当点数会把页面放大成 17×24 英寸的海报。
   */
  private static final int PAGE_WIDTH = 1240;
  private static final int PAGE_HEIGHT = 1754;
  private static final int MARGIN = 56;
  private static final int CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
  private static final int ROW_HEIGHT = 30;

  private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 36);
  private static final Font SECTION_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 22);
  private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
  private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
  private static final Font MUTED_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
  private static final Font TABLE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 15);

  private static final Color TEXT_COLOR = new Color(0x1F, 0x24, 0x2B);
  private static final Color MUTED_COLOR = new Color(0x5A, 0x63, 0x6E);
  private static final Color LINE_COLOR = new Color(0xD5, 0xDA, 0xE0);

  private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final String EMPTY_CELL = "—";
  private static final String NOT_APPLICABLE_TEXT = "不适用";
  private static final String SIGNED_TEXT = "已到场";
  private static final String NOT_RECORDED_TEXT = "未记录";

  /**
   * 渲染一期一档 PDF：封面信息 → 数据统计 → 培训内容 → 参训人员成绩（含签到情况）→ 综合评价
   * → 专家查课 → 奖惩 → 影像记录 → 附件 → 证书清单。
   */
  public byte[] render(ArchiveViewVO view) {
    PageWriter writer = new PageWriter();
    writer.title("培训档案");
    writer.line("生成时间：" + DATE_TIME_FORMAT.format(LocalDateTime.now()), MUTED_FONT);
    writer.gap(4);
    drawPlanCover(writer, view);
    drawTrainings(writer, view);
    return toPdf(writer.pages());
  }

  /** 渲染个人档案 PDF：人员信息 + 逐期培训记录的完整详情。 */
  public byte[] renderPerson(ArchivePersonVO person) {
    PageWriter writer = new PageWriter();
    writer.title("个人培训档案");
    writer.line("生成时间：" + DATE_TIME_FORMAT.format(LocalDateTime.now()), MUTED_FONT);
    writer.gap(4);
    writer.section("人员信息");
    writer.line("姓名：" + text(person.getUserName()) + "　　工号：" + text(person.getUserCode())
                  + "　　用户ID：" + text(person.getUserId()), BODY_FONT);
    List<String> missing = person.getMissingItems() == null ? List.of() : person.getMissingItems();
    writer.line(missing.isEmpty() ? "归档缺失项：无" : "归档缺失项：" + String.join("、", missing), BODY_FONT);
    List<ArchiveViewVO> trainings = person.getTrainings() == null ? List.of() : person.getTrainings();
    if (trainings.isEmpty()) {
      writer.line("暂无已结束培训记录。", MUTED_FONT);
    }
    for (int index = 0; index < trainings.size(); index++) {
      ArchiveViewVO view = trainings.get(index);
      writer.section("培训记录 " + (index + 1) + "：" + text(view.getPlanName())
                       + "（" + text(view.getPlanCode()) + "）");
      drawTrainings(writer, view);
    }
    return toPdf(writer.pages());
  }

  private void drawPlanCover(PageWriter writer, ArchiveViewVO view) {
    writer.section("培训计划");
    Map<String, Object> plan = fact(view.getPlan());
    Map<String, Object> organization = fact(view.getOrganization());
    writer.line("培训计划编号：" + text(view.getPlanCode()) + "　　计划状态：" + ArchiveLabels.planStatus(view.getPublishStatus()),
                BODY_FONT);
    writer.line("培训计划名称：" + text(view.getPlanName()), BODY_FONT);
    writer.line("培训企业：" + text(view.getOrganizationName())
                  + (organization.isEmpty() ? "" : "（" + text(organization.get("orgCode")) + "）"), BODY_FONT);
    writer.line("培训类型：" + text(plan.get("trainingTypeName"))
                  + "　　培训性质：" + ArchiveLabels.trainingNature(plan.get("trainingNature")), BODY_FONT);
    writer.line("培训地点：" + text(plan.get("location"))
                  + "　　适用要求学时：" + text(plan.get("applicableRequiredHours")), BODY_FONT);
    writer.line("计划开始：" + dateTime(plan.get("startTime"))
                  + "　　计划结束：" + dateTime(plan.get("endTime")), BODY_FONT);
    writer.line("实际结束：" + dateTime(plan.get("endedTime")), BODY_FONT);
    // 历史 JSON 档案不是 PDF 正本，正本内容里同样标注，便于线下交付时识别。
    writer.line(Boolean.TRUE.equals(view.getArchiveFileIsPdf())
                  ? "档案文件：PDF 正本"
                  : "档案文件：历史 JSON 档案，需重新更新档案以获得 PDF 正本", BODY_FONT);
    writer.line("参训人数：" + text(view.getParticipantCount())
                  + "　　归档缺失项：" + (view.getMissingItems() == null || view.getMissingItems().isEmpty()
      ? "无" : String.join("、", view.getMissingItems())), BODY_FONT);
  }

  private void drawTrainings(PageWriter writer, ArchiveViewVO view) {
    drawStatistics(writer, view);
    drawContents(writer, view);
    drawScores(writer, view);
    drawEvaluations(writer, view);
    if (view.getExpertInspections() != null) {
      drawExpertInspections(writer, view);
    }
    drawRewards(writer, view);
    drawMaterials(writer, "影像记录", facts(view.getMaterials()));
    drawMaterials(writer, "附件清单", facts(view.getAttachments()));
    drawCertificates(writer, view);
  }

  private void drawStatistics(PageWriter writer, ArchiveViewVO view) {
    writer.section("数据统计");
    Map<String, Object> statistics = fact(view.getStatistics());
    writer.line("参训人数：" + text(statistics.get("participantCount"))
                  + "　　已完成：" + text(statistics.get("completedCount"))
                  + "　　通过：" + text(statistics.get("passedCount"))
                  + "　　未通过：" + text(statistics.get("failedCount"))
                  + "　　结果待补全：" + text(statistics.get("incompleteCount")), BODY_FONT);
    writer.line("通过率：" + rate(statistics.get("passRate"))
                  + "　　平均综合分：" + decimal(statistics.get("averageCompositeScore")), BODY_FONT);
    writer.line("培训内容：" + text(statistics.get("contentCount"))
                  + " 项（必修 " + text(statistics.get("requiredContentCount")) + " 项）"
                  + "　　线上学时合计：" + decimal(statistics.get("onlineHoursTotal"))
                  + "　　线下学时合计：" + decimal(statistics.get("offlineHoursTotal")), BODY_FONT);
    writer.line("影像记录：" + text(statistics.get("imageCount"))
                  + "　　附件：" + text(statistics.get("attachmentCount"))
                  + "　　奖励：" + text(statistics.get("rewardCount"))
                  + "　　惩罚：" + text(statistics.get("penaltyCount")), BODY_FONT);
    if (statistics.get("signedCount") != null || statistics.get("notRecordedCount") != null) {
      writer.line("已到场（已录入线下成绩）：" + text(statistics.get("signedCount"))
                    + "　　尚未录入线下成绩：" + text(statistics.get("notRecordedCount")), BODY_FONT);
    }
  }

  private void drawContents(PageWriter writer, ArchiveViewVO view) {
    writer.section("培训内容");
    List<Map<String, Object>> contents = facts(view.getContents());
    if (contents.isEmpty()) {
      writer.line("（无培训内容）", MUTED_FONT);
      return;
    }
    int[] widths = {70, 300, 120, 70, 70, 90, 120, 288};
    writer.header(new String[] {"序号", "内容名称", "类型", "必修", "计分", "权重", "要求学时", "所属阶段"},
                  widths);
    int index = 1;
    for (Map<String, Object> content : contents) {
      writer.row(new String[] {String.valueOf(index++), text(content.get("contentName")),
        ArchiveLabels.contentType(content.get("contentType")), yesNo(content.get("isRequired")), yesNo(content.get("isScored")),
        decimal(content.get("scoreWeight")), decimal(content.get("requiredHours")),
        text(content.get("stageName"))}, widths);
    }
  }

  private void drawScores(PageWriter writer, ArchiveViewVO view) {
    writer.section("参训人员成绩（含签到情况）");
    List<Map<String, Object>> scores = facts(view.getScores());
    if (scores.isEmpty()) {
      writer.line("（无参训人员成绩）", MUTED_FONT);
      return;
    }
    Map<Long, String> signIn = new LinkedHashMap<>();
    for (Map<String, Object> row : facts(view.getAttendance())) {
      Long key = longValue(row.get("trainingParticipantId"));
      if (key != null) {
        signIn.put(key, text(row.get("signInStatus")));
      }
    }
    // 成绩事实只有姓名没有工号，工号统一从参训人清单关联，避免在成绩里重复存一份。
    Map<Long, String> userCodes = new LinkedHashMap<>();
    for (Map<String, Object> row : facts(view.getParticipants())) {
      Long key = longValue(row.get("trainingParticipantId"));
      if (key != null) {
        userCodes.put(key, text(row.get("userCode")));
      }
    }
    int[] widths = {60, 170, 150, 130, 130, 130, 130, 130, 98};
    writer.header(new String[] {"序号", "姓名", "工号", "签到情况", "课程分", "考试分", "线下分", "综合分", "结果"},
                  widths);
    int index = 1;
    for (Map<String, Object> score : scores) {
      Long participantId = longValue(score.get("trainingParticipantId"));
      String status = participantId == null ? null : signIn.get(participantId);
      writer.row(new String[] {String.valueOf(index++), text(score.get("userName")),
        participantId == null ? EMPTY_CELL : userCodes.getOrDefault(participantId, EMPTY_CELL),
        signInText(status), decimal(score.get("courseScore")),
        decimal(score.get("examScore")), decimal(score.get("offlineScore")),
        decimal(score.get("compositeScore")), ArchiveLabels.resultStatus(score.get("resultStatus"))}, widths);
    }
  }

  private void drawEvaluations(PageWriter writer, ArchiveViewVO view) {
    writer.section("综合评价");
    List<Map<String, Object>> evaluations = facts(view.getEvaluations());
    if (evaluations.isEmpty()) {
      writer.line("（无综合评价）", MUTED_FONT);
      return;
    }
    int[] widths = {70, 260, 220, 220, 358};
    writer.header(new String[] {"序号", "姓名", "综合分", "结果状态", "判定说明"}, widths);
    int index = 1;
    for (Map<String, Object> evaluation : evaluations) {
      writer.row(new String[] {String.valueOf(index++), text(evaluation.get("userName")),
        decimal(evaluation.get("compositeScore")), ArchiveLabels.resultStatus(evaluation.get("resultStatus")),
        "按综合分与结果状态判定"}, widths);
    }
  }

  private void drawRewards(PageWriter writer, ArchiveViewVO view) {
    writer.section("奖惩记录");
    List<Map<String, Object>> rewards = facts(view.getRewardPenalties());
    if (rewards.isEmpty()) {
      writer.line("（无奖惩记录）", MUTED_FONT);
      return;
    }
    int[] widths = {60, 130, 110, 330, 110, 130, 258};
    writer.header(new String[] {"序号", "参训人ID", "类型", "标题", "分数变动", "发生日期", "说明"}, widths);
    int index = 1;
    for (Map<String, Object> reward : rewards) {
      writer.row(new String[] {String.valueOf(index++), text(reward.get("trainingParticipantId")),
        ArchiveLabels.rewardRecordType(reward.get("recordType")), text(reward.get("title")), decimal(reward.get("scoreChange")),
        text(reward.get("occurredDate")), text(reward.get("description"))}, widths);
    }
  }

  /** 一期档案逐项保留专家意见，长文本由 PageWriter 自动换行和分页。 */
  private void drawExpertInspections(PageWriter writer, ArchiveViewVO view) {
    writer.section("专家查课");
    List<Map<String, Object>> inspections = facts(view.getExpertInspections());
    if (inspections.isEmpty()) {
      writer.line("（无已提交专家查课记录）", MUTED_FONT);
      return;
    }
    for (Map<String, Object> inspection : inspections) {
      writer.line("课程：" + text(inspection.get("contentName")) + "　　表单版本：v" + text(inspection.get("versionNo")), BODY_FONT);
      writer.line("评价人：" + text(inspection.get("evaluatorName")) + "　　提交时间：" + dateTime(inspection.get("submittedTime"))
        + "　　总分：" + decimal(inspection.get("totalScore")), BODY_FONT);
      writer.line("查课摘要：" + text(inspection.get("summary")), BODY_FONT);
      for (Map<String, Object> item : facts(inspection.get("items"))) {
        String answer = "SCORE".equals(item.get("itemType")) ? decimal(item.get("score")) + " 分" : text(item.get("textValue"));
        writer.line(text(item.get("itemName")) + "：" + answer, BODY_FONT);
      }
    }
  }

  /** 影像与附件只出清单；二进制原件不经 PDF 分发。 */
  private void drawMaterials(PageWriter writer, String title, List<Map<String, Object>> rows) {
    writer.section(title);
    if (rows.isEmpty()) {
      writer.line("（无" + title + "）", MUTED_FONT);
      return;
    }
    int[] widths = {60, 300, 110, 160, 130, 368};
    writer.header(new String[] {"序号", "名称", "类型", "所属阶段", "文件ID", "说明"}, widths);
    int index = 1;
    for (Map<String, Object> row : rows) {
      writer.row(new String[] {String.valueOf(index++), text(row.get("attachmentName")),
        ArchiveLabels.attachmentType(row.get("attachmentType")), text(row.get("stageName")), text(row.get("fileInfoId")),
        text(row.get("description"))}, widths);
    }
  }

  /**
   * 证书区只呈现数量，**不内嵌证书编号、持证人 ID 与来源 ID**。
   *
   * <p>档案文件一经持久化，下载只校验 {@code archive:training:list}；若把证书明细印进 PDF，
   * 等于用档案权限旁路了证书清单的 {@code certificate:cert:list} 门控（且交付物会外带内部主键）。
   * 证书明细 MUST 经受控端点在线查看。
   */
  private void drawCertificates(PageWriter writer, ArchiveViewVO view) {
    writer.section("证书清单");
    writer.line(certificateSummary(view), MUTED_FONT);
    if (view.getCertificates() != null) {
      writer.line("证书编号与持证人信息不在档案文件中呈现，请按证书查询权限在档案详情页在线查看。", MUTED_FONT);
    }
  }

  /** 证书数量文案；返回值不含任何证书标识，理由见 {@link #drawCertificates}。 */
  static String certificateSummary(ArchiveViewVO view) {
    if (view.getCertificates() == null) {
      return "未返回证书清单（当前调用方缺少 certificate:cert:list 权限）。";
    }
    int count = facts(view.getCertificates()).size();
    return count == 0 ? "（无已签发证书）" : "本期已签发证书 " + count + " 张。";
  }

  private byte[] toPdf(List<BufferedImage> pages) {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      float pageWidth = PDRectangle.A4.getWidth();
      float pageHeight = PDRectangle.A4.getHeight();
      for (BufferedImage image : pages) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
          content.drawImage(pdfImage, 0, 0, pageWidth, pageHeight);
        }
      }
      document.save(out);
      return out.toByteArray();
    } catch (IOException | RuntimeException ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 9211, "档案文件生成失败");
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> facts(Object value) {
    return value == null ? List.of() : (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> fact(Object value) {
    return value == null ? Map.of() : (Map<String, Object>) value;
  }

  private static String text(Object value) {
    return value == null ? EMPTY_CELL : String.valueOf(value);
  }

  private static String decimal(Object value) {
    if (value == null) {
      return EMPTY_CELL;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    return String.valueOf(value);
  }

  /** 比率为空表示分母为 0，展示为空占位而不是 0%。 */
  private static String rate(Object value) {
    if (!(value instanceof BigDecimal bigDecimal)) {
      return EMPTY_CELL;
    }
    return bigDecimal.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
  }

  private static String dateTime(Object value) {
    if (value == null) {
      return EMPTY_CELL;
    }
    if (value instanceof LocalDateTime localDateTime) {
      return DATE_TIME_FORMAT.format(localDateTime);
    }
    return String.valueOf(value);
  }

  private static String yesNo(Object value) {
    if (value == null) {
      return EMPTY_CELL;
    }
    return Integer.valueOf(1).equals(value) ? "是" : "否";
  }

  private static String signInText(String status) {
    if (status == null) {
      return EMPTY_CELL;
    }
    return switch (status) {
      case "SIGNED" -> SIGNED_TEXT;
      case "NOT_RECORDED" -> NOT_RECORDED_TEXT;
      case "NOT_APPLICABLE" -> NOT_APPLICABLE_TEXT;
      default -> status;
    };
  }

  private static Long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : null;
  }

  /**
   * 逐页绘制的位图写入器：行数超过单页可用高度时自动新开一页，避免内容被截断。
   */
  private static final class PageWriter {

    private final List<BufferedImage> pages = new ArrayList<>();
    private Graphics2D graphics;
    private int cursorY;

    PageWriter() {
      newPage();
    }

    List<BufferedImage> pages() {
      if (graphics != null) {
        graphics.dispose();
        graphics = null;
      }
      return pages;
    }

    void gap(int height) {
      reserve(height);
      cursorY += height;
    }

    void title(String text) {
      reserve(60);
      graphics.setFont(TITLE_FONT);
      graphics.setColor(TEXT_COLOR);
      cursorY += 44;
      graphics.drawString(text, MARGIN, cursorY);
      cursorY += 20;
    }

    void section(String text) {
      reserve(64);
      cursorY += 16;
      graphics.setFont(SECTION_FONT);
      graphics.setColor(TEXT_COLOR);
      cursorY += 26;
      graphics.drawString(text, MARGIN, cursorY);
      cursorY += 12;
      graphics.setColor(LINE_COLOR);
      graphics.drawLine(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY);
      cursorY += 8;
      graphics.setColor(TEXT_COLOR);
    }

    void line(String text, Font font) {
      for (String part : wrap(text, font)) {
        reserve(ROW_HEIGHT);
        graphics.setFont(font);
        graphics.setColor(font == MUTED_FONT ? MUTED_COLOR : TEXT_COLOR);
        cursorY += 22;
        graphics.drawString(part, MARGIN, cursorY);
        cursorY += 8;
      }
    }

    void header(String[] cells, int[] widths) {
      drawCells(cells, widths, HEADER_FONT);
      graphics.setColor(LINE_COLOR);
      graphics.drawLine(MARGIN, cursorY - 4, PAGE_WIDTH - MARGIN, cursorY - 4);
      graphics.setColor(TEXT_COLOR);
    }

    void row(String[] cells, int[] widths) {
      drawCells(cells, widths, TABLE_FONT);
    }

    /** 单元格按列宽截断并左对齐，避免串列；影像/附件只出清单，不含二进制。 */
    private void drawCells(String[] cells, int[] widths, Font font) {
      reserve(ROW_HEIGHT);
      graphics.setFont(font);
      graphics.setColor(TEXT_COLOR);
      int x = MARGIN;
      for (int i = 0; i < cells.length && i < widths.length; i++) {
        graphics.drawString(ellipsize(cells[i], widths[i] - 10), x, cursorY + 20);
        x += widths[i];
      }
      cursorY += ROW_HEIGHT;
    }

    private void newPage() {
      BufferedImage page = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
      graphics = page.createGraphics();
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
      graphics.setColor(TEXT_COLOR);
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      pages.add(page);
      cursorY = MARGIN;
    }

    private void reserve(int height) {
      if (cursorY + height > PAGE_HEIGHT - MARGIN) {
        newPage();
      }
    }

    private String ellipsize(String text, int maxWidth) {
      if (text == null) {
        return "";
      }
      FontMetrics metrics = graphics.getFontMetrics();
      if (metrics.stringWidth(text) <= maxWidth) {
        return text;
      }
      String suffix = "…";
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        if (metrics.stringWidth(builder.toString() + text.charAt(i) + suffix) > maxWidth) {
          break;
        }
        builder.append(text.charAt(i));
      }
      return builder + suffix;
    }

    /** 按可用宽度逐字折行；中文没有词边界，逐字折行是可接受的最小实现。 */
    private List<String> wrap(String text, Font font) {
      graphics.setFont(font);
      FontMetrics metrics = graphics.getFontMetrics();
      List<String> lines = new ArrayList<>();
      String content = text == null ? "" : text;
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < content.length(); i++) {
        char current = content.charAt(i);
        if (current == '\n') {
          lines.add(builder.toString());
          builder.setLength(0);
          continue;
        }
        builder.append(current);
        if (metrics.stringWidth(builder.toString()) > CONTENT_WIDTH) {
          builder.deleteCharAt(builder.length() - 1);
          lines.add(builder.toString());
          builder.setLength(0);
          builder.append(current);
        }
      }
      lines.add(builder.toString());
      return lines;
    }
  }
}
