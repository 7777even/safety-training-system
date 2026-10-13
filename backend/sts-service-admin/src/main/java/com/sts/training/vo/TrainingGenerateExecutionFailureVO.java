package com.sts.training.vo;

import lombok.Data;

/**
 * 计划级生成执行时单个内容的失败项。
 */
@Data
public class TrainingGenerateExecutionFailureVO {
  /**
   * 计划内容 ID。
   */
  private Long planContentId;
  /**
   * 内容类型。
   */
  private String contentType;
  /**
   * 业务错误码。
   */
  private Integer code;
  /**
   * 脱敏后的错误说明。
   */
  private String message;
  /**
   * COURSE 已落下的开课 ID（缺阅卷人失败时仍为草稿）；尚未生成或其他内容类型时为空。
   */
  private Long offeringId;
}
