package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_standard")
public class TrainingStandard extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingStandardId;
  private Long organizationId;
  private String standardCode;
  private String standardName;
  private Long trainingTypeId;
  private String standardVersion;
  private LocalDate validFrom;
  private LocalDate validTo;
  private String publishStatus;
  private Integer status;
}
