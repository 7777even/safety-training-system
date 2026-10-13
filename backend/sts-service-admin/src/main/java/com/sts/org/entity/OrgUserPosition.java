package com.sts.org.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户岗位关联。每个用户至多一个主岗位（{@code isPrimary=1}）。
 */
@Data
@TableName("sts_org_user_position")
public class OrgUserPosition {
  @TableId(type = IdType.AUTO)
  private Long userPositionId;
  private Long userId;
  private Long positionId;
  private Integer isPrimary;

  @TableField(fill = FieldFill.INSERT)
  private Long createBy;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
