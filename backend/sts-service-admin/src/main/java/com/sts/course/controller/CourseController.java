package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.*;
import com.sts.course.service.CourseService;
import com.sts.course.vo.CourseListItemVO;
import com.sts.course.vo.CourseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 课程基本信息管理（草稿态）。
 * <p>
 * 已知缺口：同 {@code com.sts.course.controller.CourseCategoryController}，操作审计留待后续统一基础设施升级时补齐。
 */
@RestController
@RequestMapping("/course/courses")
@RequiredArgsConstructor
public class CourseController {

  private final CourseService courseService;

  /**
   * 分页查询课程。
   */
  @GetMapping
  @SaCheckPermission("course:course:list")
  public PageResult<CourseListItemVO> pageCourses(PageRequest<CourseListQuery> request) {
    return courseService.pageCourses(request);
  }

  /**
   * 查询课程。
   */
  @GetMapping("/{courseId}")
  @SaCheckPermission("course:course:list")
  public CourseVO getCourse(@PathVariable Long courseId) {
    return courseService.getCourse(courseId);
  }

  /**
   * 创建课程。
   */
  @PostMapping
  @SaCheckPermission("course:course:add")
  public Long createCourse(@RequestBody @Valid CourseCreateRequest request) {
    return courseService.createCourse(request);
  }

  /**
   * 更新课程。
   */
  @PutMapping("/{courseId}")
  @SaCheckPermission("course:course:edit")
  public void updateCourse(@PathVariable Long courseId, @RequestBody @Valid CourseUpdateRequest request) {
    courseService.updateCourse(courseId, request);
  }

  /**
   * 删除课程。
   */
  @DeleteMapping("/{courseId}")
  @SaCheckPermission("course:course:delete")
  public void deleteCourse(@PathVariable Long courseId) {
    courseService.deleteCourse(courseId);
  }

  /**
   * 切换课程内容组织模式。
   */
  @PutMapping("/{courseId}/content-mode")
  @SaCheckPermission("course:course:edit")
  public void switchContentMode(@PathVariable Long courseId, @RequestBody @Valid ContentModeSwitchRequest request) {
    courseService.switchContentMode(courseId, request);
  }

  /**
   * 设置课程所属分类。
   */
  @PutMapping("/{courseId}/categories")
  @SaCheckPermission("course:course:edit")
  public void assignCategories(@PathVariable Long courseId, @RequestBody @Valid CategoryAssignRequest request) {
    courseService.assignCategories(courseId, request);
  }
}
