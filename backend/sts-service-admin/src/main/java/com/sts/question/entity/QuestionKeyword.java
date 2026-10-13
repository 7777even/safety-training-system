package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_keyword")
public class QuestionKeyword extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long questionKeywordId;
  private Long questionVersionId;
  private String keyword;
  private BigDecimal weight;
  private Integer sortOrder;
}
