package com.sts.question.vo;

import lombok.Data;

/**
 * 题目版本关联的素材。素材是版本的组成部分，随版本一起冻结。
 */
@Data
public class QuestionAssetVO {
  private Long questionAssetId;
  private Long questionVersionId;
  private Long assetId;
  private String assetRole;
  private String optionKey;
  private Integer sortOrder;
}
