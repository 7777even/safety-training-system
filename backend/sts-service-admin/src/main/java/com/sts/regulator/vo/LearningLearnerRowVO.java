package com.sts.regulator.vo;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

@Data
@Translatable
public class LearningLearnerRowVO {
  private Long userId;
  private String userName;
  private String userCode;
  private Long organizationId;

  /**
   * 所属机构名称，由展示翻译按 organizationId 回填。
   */
  @TranslateField(source = "organizationId", type = "ORG_SERVICE")
  private String orgName;

  private long offeringCount;
}
