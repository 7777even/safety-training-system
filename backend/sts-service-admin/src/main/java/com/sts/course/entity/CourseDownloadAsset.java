package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_download_asset")
public class CourseDownloadAsset extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long courseDownloadAssetId;
  private Long courseId;
  private Long assetId;
  private Integer sortOrder;
  private Integer status;
}
