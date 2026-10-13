package com.sts.training.service;

import com.sts.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * 培训安排学时：考试/题库不填、不计；课程与线下才计入合计。
 */
public final class TrainingArrangedHours {

  static final int CODE_ASSESSMENT_HOURS = 8165;

  private TrainingArrangedHours() {
  }

  /**
   * 保存阶段内容：考试/题库省略或 0 则存 0，正数拒绝。
   */
  static BigDecimal requireForSave(String contentType, BigDecimal hours) {
    if (!isAssessment(contentType)) {
      return hours;
    }
    if (hours != null && hours.compareTo(BigDecimal.ZERO) > 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, CODE_ASSESSMENT_HOURS, assessmentHoursMessage(contentType));
    }
    return BigDecimal.ZERO;
  }

  /**
   * 从模板复制：考试/题库一律归零，不因历史正数失败。
   */
  static BigDecimal forCopy(String contentType, BigDecimal hours) {
    if (isAssessment(contentType)) {
      return BigDecimal.ZERO;
    }
    return hours;
  }

  /**
   * 发布与预警合计用的安排学时；考试/题库贡献 0。
   */
  public static BigDecimal contributingHours(String contentType, BigDecimal hours) {
    if ("COURSE".equals(contentType) || "OFFLINE".equals(contentType)) {
      return hours == null ? BigDecimal.ZERO : hours;
    }
    return BigDecimal.ZERO;
  }

  public static boolean countsAsOnline(String contentType) {
    return "COURSE".equals(contentType);
  }

  public static boolean isAssessment(String contentType) {
    return "EXAM".equals(contentType) || "QUESTION_BANK".equals(contentType);
  }

  private static String assessmentHoursMessage(String contentType) {
    return "QUESTION_BANK".equals(contentType) ? "题库不得填写学时" : "考试不得填写学时";
  }
}
