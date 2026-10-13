package com.sts.course.service;

import com.sts.course.dto.CategoryCreateRequest;
import com.sts.course.dto.CategoryUpdateRequest;
import com.sts.course.vo.CourseCategoryTreeVO;

import java.util.List;

public interface CourseCategoryService {

  List<CourseCategoryTreeVO> getCategoryTree(String categoryName, Integer status);

  Long createCategory(CategoryCreateRequest request);

  void updateCategory(Long categoryId, CategoryUpdateRequest request);

  void deleteCategory(Long categoryId);
}
