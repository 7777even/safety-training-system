package com.sts.org.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户标签关联。标签用于跨部门、跨岗位圈定培训与考试对象。
 */
@Data
@TableName("sts_org_user_tag")
public class OrgUserTag {
  @TableId(type = IdType.AUTO)
  private Long userTagId;
  private Long userId;
  private Long tagId;

  @TableField(fill = FieldFill.INSERT)
  private Long createBy;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
