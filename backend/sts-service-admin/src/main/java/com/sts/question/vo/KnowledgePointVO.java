package com.sts.question.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgePointVO {
  private Long knowledgePointId;
  private Long organizationId;
  private Long parentId;
  private String knowledgeCode;
  private String knowledgeName;
  private String ancestors;
  private Integer sortOrder;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  private List<KnowledgePointVO> children = new ArrayList<>();
}
