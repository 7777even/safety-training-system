package com.sts.training.vo;

import lombok.Data;

/**
 * 学员侧培训资料只读项。
 *
 * <p>只暴露展示所需事实：**不返回文件主键 {@code fileInfoId}**，也不返回可直接访问的文件地址；
 * 学员一律按资料记录经受控端点读取文件，避免把存储地址交给前端。
 */
@Data
public class TrainingLearnerMaterialVO {
  private Long trainingAttachmentId;
  private String attachmentName;
  private String description;
  private Long planStageId;
  private String stageName;
  private Integer sortOrder;
  /** 是否已关联可读文件；前端据此决定是否给出「预览」入口，避免发出必然失败的请求。 */
  private Boolean hasFile;
}
