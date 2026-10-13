package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_knowledge_point")
public class KnowledgePoint extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long knowledgePointId;
  private Long organizationId;
  private Long parentId;
  private String knowledgeCode;
  private String knowledgeName;
  private String ancestors;
  private Integer sortOrder;
  private Integer status;
}
