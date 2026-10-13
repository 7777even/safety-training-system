package com.sts.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_paper_version")
public class PaperVersion extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long paperVersionId;
  private Long paperId;
  private Integer versionNo;
  private Integer durationSeconds;
  private BigDecimal totalScore;
  private BigDecimal passRatio;
  private BigDecimal passScore;
  private Integer questionCount;
  private String questionOrderMode;
  private String multiSelectScoring;
  private String drawSettingMode;
  private Long randomSeed;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
}
