package com.sts.regulator.vo;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

@Data
@Translatable
public class LearningOfferingRowVO {
  private Long courseOfferingId;
  private String offeringName;
  private Long organizationId;

  /**
   * 所属机构名称，由展示翻译按 organizationId 回填。
   */
  @TranslateField(source = "organizationId", type = "ORG_SERVICE")
  private String orgName;

  private long learnerCount;
}
