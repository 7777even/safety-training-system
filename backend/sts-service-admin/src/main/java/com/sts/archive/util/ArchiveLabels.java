package com.sts.archive.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 档案成品的中文口径。
 *
 * <p>库内大量字段以英文编码存储（计划状态、内容类型、结果状态、资料类型……），而档案 PDF 是**交付给业务方看得见的成品**，
 * 不允许直接印出 {@code COURSE} / {@code PASSED} / {@code REWARD} 这类编码。此处把档案用到的编码集中译为中文，
 * 与证书成品 `CertificateSourceTypes` 是同一做法。
 *
 * <p>未知编码**原样返回**而不是吞掉或替换成占位符：便于从成品上发现脏数据。
 */
public final class ArchiveLabels {

  private static final String EMPTY = "—";

  private static final Map<String, String> PLAN_STATUS = new LinkedHashMap<>();
  private static final Map<String, String> TRAINING_NATURE = new LinkedHashMap<>();
  private static final Map<String, String> CONTENT_TYPE = new LinkedHashMap<>();
  private static final Map<String, String> RESULT_STATUS = new LinkedHashMap<>();
  private static final Map<String, String> REWARD_RECORD_TYPE = new LinkedHashMap<>();
  private static final Map<String, String> ATTACHMENT_TYPE = new LinkedHashMap<>();
  private static final Map<String, String> CERTIFICATE_STATUS = new LinkedHashMap<>();

  static {
    PLAN_STATUS.put("DRAFT", "草稿");
    PLAN_STATUS.put("PUBLISHED", "已发布");
    PLAN_STATUS.put("RUNNING", "执行中");
    PLAN_STATUS.put("ENDED", "已结束");
    PLAN_STATUS.put("CANCELLED", "已取消");
    PLAN_STATUS.put("ARCHIVED", "已归档");

    TRAINING_NATURE.put("INITIAL", "初培");
    TRAINING_NATURE.put("REVIEW", "复审");
    TRAINING_NATURE.put("UNSPECIFIED", "未明确");

    CONTENT_TYPE.put("COURSE", "课程");
    CONTENT_TYPE.put("EXAM", "理论考试");
    CONTENT_TYPE.put("QUESTION_BANK", "题库练习");
    CONTENT_TYPE.put("OFFLINE", "线下培训");

    RESULT_STATUS.put("PASSED", "合格");
    RESULT_STATUS.put("FAILED", "不合格");
    RESULT_STATUS.put("INCOMPLETE", "结果待补全");

    REWARD_RECORD_TYPE.put("REWARD", "奖励");
    REWARD_RECORD_TYPE.put("PENALTY", "处罚");

    ATTACHMENT_TYPE.put("GENERAL", "一般资料");
    ATTACHMENT_TYPE.put("IMAGE", "图片");
    ATTACHMENT_TYPE.put("VIDEO", "视频");
    ATTACHMENT_TYPE.put("REWARD_PENALTY", "奖惩佐证");
    ATTACHMENT_TYPE.put("EVALUATION", "评价佐证");

    CERTIFICATE_STATUS.put("ISSUED", "已签发");
  }

  private ArchiveLabels() {}

  /** 培训计划发布状态。 */
  public static String planStatus(Object code) {
    return label(code, PLAN_STATUS);
  }

  /** 培训性质。 */
  public static String trainingNature(Object code) {
    return label(code, TRAINING_NATURE);
  }

  /** 培训内容类型。 */
  public static String contentType(Object code) {
    return label(code, CONTENT_TYPE);
  }

  /** 参训人结果状态。 */
  public static String resultStatus(Object code) {
    return label(code, RESULT_STATUS);
  }

  /** 奖惩记录类型。 */
  public static String rewardRecordType(Object code) {
    return label(code, REWARD_RECORD_TYPE);
  }

  /** 培训资料类型。 */
  public static String attachmentType(Object code) {
    return label(code, ATTACHMENT_TYPE);
  }

  /** 证书状态。 */
  public static String certificateStatus(Object code) {
    return label(code, CERTIFICATE_STATUS);
  }

  private static String label(Object code, Map<String, String> labels) {
    if (code == null) {
      return EMPTY;
    }
    String key = String.valueOf(code);
    return labels.getOrDefault(key, key);
  }
}
