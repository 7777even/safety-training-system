package com.sts.regulation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【暂时隐藏】原预警分派/评论/关闭流水；当前中山50最小闭环不读写。
 */
@Data
@TableName("sts_regulation_warning_action")
public class RegulationWarningAction {
  @TableId(type = IdType.AUTO)
  private Long warningActionId;
  private Long warningId;
  private String actionType;
  private Long actionBy;
  private LocalDateTime actionTime;
  private String beforeStatus;
  private String afterStatus;
  private String actionContent;
  private Long createBy;
  private LocalDateTime createTime;
}
