package com.sts.course.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CourseCategoryTreeVO {
  private Long courseCategoryId;
  private Long parentId;
  private String categoryName;
  private String description;
  private Integer sortOrder;
  private Integer status;
  private Integer version;
  private List<CourseCategoryTreeVO> children = new ArrayList<>();
}
