package com.sts.org.vo;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Translatable
public class OrgTagVO {
  private Long tagId;
  private Long organizationId;

  /**
   * 所属机构名称，由展示翻译按 organizationId 回填。
   */
  @TranslateField(source = "organizationId", type = "ORG_SERVICE")
  private String orgName;

  private String tagName;
  private String description;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
}
