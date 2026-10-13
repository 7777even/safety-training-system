package com.sts.training.dto;

import lombok.Data;

/**
 * 学员统一进入请求。仅考试/题库练习使用客户端设备信息；禁止携带身份或考试上下文。
 */
@Data
public class TrainingLearnerEnterRequest {
  /**
   * 客户端作答令牌；EXAM/QUESTION_BANK 必填。
   */
  private String clientAttemptToken;
  /**
   * 客户端平台。
   */
  private String platform;
  /**
   * 操作系统。
   */
  private String operatingSystem;
  /**
   * 浏览器。
   */
  private String browser;
  /**
   * 客户端 IP。
   */
  private String clientIp;
}
