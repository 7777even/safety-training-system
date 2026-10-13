package com.sts.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_paper_paper")
public class Paper extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long paperId;
  private Long organizationId;
  private String paperCode;
  private String paperName;
  private String paperType;
  private Long paperCategoryId;
  private Long coverFileId;
  private Integer currentVersionNo;
  private String archiveStatus;
  private Integer status;
}
