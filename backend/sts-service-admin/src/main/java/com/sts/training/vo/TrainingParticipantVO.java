package com.sts.training.vo;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Translatable
public class TrainingParticipantVO {
  private Long trainingParticipantId;
  private Long organizationId;
  private Long trainingPlanId;
  private Long userId;

  /**
   * 参训人姓名，由展示翻译按 userId 回填；监管只读下钻据此展示人名而不是用户主键。
   */
  @TranslateField(source = "userId", type = "USER_SERVICE")
  private String userName;

  private Long participantOrgId;
  private String joinSource;
  private LocalDateTime joinedTime;
  private String participantStatus;
  private BigDecimal onlineHours;
  private BigDecimal offlineHours;
}
