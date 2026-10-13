package com.sts.question.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BankCategoryVO {
  private Long bankCategoryId;
  private Long organizationId;
  private String categoryName;
  private String description;
  private Integer sortOrder;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
}
