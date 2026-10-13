package com.sts.course.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖式设置课程分类关联：传入的分类 ID 列表即为课程最终的分类集合。
 */
@Data
public class CategoryAssignRequest {
  private List<Long> categoryIds = new ArrayList<>();
}
