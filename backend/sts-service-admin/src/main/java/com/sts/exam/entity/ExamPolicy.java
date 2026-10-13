package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_policy")
public class ExamPolicy extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long examPolicyId;
  private Long examId;
  /** 是否启用考试答题端切离页面事件审计：0 否、1 是。 */
  private Integer isAnticheatEnabled;
  private Integer isWatermarkEnabled;
  private Integer isPcAllowed;
  private Integer isAppAllowed;
  private Integer isH5Allowed;
  private Integer switchLimit;
  private Integer isSplitScreenForceSubmit;
  private Integer isCopyRefreshDevtoolDisabled;
  private Integer inactivitySeconds;
  private Integer inactivityCountdownSeconds;
  private Integer minimumAnswerSeconds;
  private Integer isRetakeEnabled;
  private String retakeScope;
  private LocalDateTime retakeStartTime;
  private LocalDateTime retakeEndTime;
  private Integer retakeMaxCount;
  private String retakeScoreMode;
  private Integer isRetakeRecordVisible;
  private BigDecimal passPoint;
  private BigDecimal failPoint;
  private BigDecimal absentPoint;
  private Integer lateForbidSeconds;
  private String answerDetailMode;
  private Integer isAnalysisVisible;
  private Integer isScoreVisible;
  private Integer isRankingEnabled;
}
