package com.sts.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_org_tag")
public class OrgTag extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long tagId;
  private Long organizationId;
  private String tagName;
  private String description;
  private Integer status;
}
