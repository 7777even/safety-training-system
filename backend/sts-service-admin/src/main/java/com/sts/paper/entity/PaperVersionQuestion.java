package com.sts.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_paper_version_question")
public class PaperVersionQuestion extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long paperQuestionId;
  private Long paperVersionId;
  private Long questionId;
  private Long questionVersionId;
  private String questionType;
  private BigDecimal score;
  private Integer sortOrder;
  private Long sourceBankId;
}
