package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.course.dto.CategoryCreateRequest;
import com.sts.course.dto.CategoryUpdateRequest;
import com.sts.course.service.CourseCategoryService;
import com.sts.course.vo.CourseCategoryTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程分类管理。
 * <p>
 * 已知缺口：本控制器暂未接入 {@code com.sts.admin.aop.annotation.OperLog} 操作审计（该切面当前仅为
 * system 域配置切点），课程域的操作审计留待后续统一基础设施升级时补齐。
 */
@RestController
@RequestMapping("/course/categories")
@RequiredArgsConstructor
public class CourseCategoryController {

  private final CourseCategoryService categoryService;

  /**
   * 查询分类树。
   */
  @GetMapping("/tree")
  @SaCheckPermission("course:category:list")
  public List<CourseCategoryTreeVO> getCategoryTree(
    @RequestParam(required = false) String categoryName,
    @RequestParam(required = false) Integer status) {
    return categoryService.getCategoryTree(categoryName, status);
  }

  /**
   * 创建分类。
   */
  @PostMapping
  @SaCheckPermission("course:category:add")
  public Long createCategory(@RequestBody @Valid CategoryCreateRequest request) {
    return categoryService.createCategory(request);
  }

  /**
   * 更新分类。
   */
  @PutMapping("/{categoryId}")
  @SaCheckPermission("course:category:edit")
  public void updateCategory(@PathVariable Long categoryId, @RequestBody @Valid CategoryUpdateRequest request) {
    categoryService.updateCategory(categoryId, request);
  }

  /**
   * 删除分类。
   */
  @DeleteMapping("/{categoryId}")
  @SaCheckPermission("course:category:delete")
  public void deleteCategory(@PathVariable Long categoryId) {
    categoryService.deleteCategory(categoryId);
  }
}
