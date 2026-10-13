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
@TableName("sts_exam_exam")
public class Exam extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long examId;
  private Long organizationId;
  private String examCode;
  private String examName;
  private String examType;
  private Long examCategoryId;
  private Long coverFileId;
  private Long paperVersionId;
  private Long questionBankId;
  private Long planContentId;
  private Long responsibleUserId;
  private LocalDateTime openStartTime;
  private LocalDateTime openEndTime;
  private Integer durationSeconds;
  private BigDecimal passRatio;
  private BigDecimal passScore;
  private BigDecimal credit;
  private String questionOrderMode;
  private String multiSelectScoring;
  private Integer isNoticeEnabled;
  private String noticeContent;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private LocalDateTime lockedTime;
  private Integer status;
  /** 题库练习抽题规则 JSON；仅 BANK_PRACTICE 使用，为空表示未配置。 */
  private String drawRuleJson;
}
