package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.course.dto.CourseOfflineRequest;
import com.sts.course.dto.CoursePublishRequest;
import com.sts.course.service.CoursePublishService;
import com.sts.course.vo.CourseVersionListItemVO;
import com.sts.course.vo.CourseVersionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程下线及已发布版本查询接口。
 */
@RestController
@RequestMapping("/course/courses")
@RequiredArgsConstructor
public class CoursePublishController {

  private final CoursePublishService coursePublishService;

  /**
   * 发布课程发布。
   */
  @PostMapping("/{courseId}/publish")
  @SaCheckPermission("course:course:publish")
  public Long publish(@PathVariable Long courseId, @RequestBody @Valid CoursePublishRequest request) {
    return coursePublishService.publish(courseId, request);
  }

  /**
   * 下线已发布课程。
   */
  @PutMapping("/{courseId}/offline")
  @SaCheckPermission("course:course:publish")
  public void offline(@PathVariable Long courseId, @RequestBody @Valid CourseOfflineRequest request) {
    coursePublishService.offline(courseId, request);
  }

  /**
   * 查询版本列表。
   */
  @GetMapping("/{courseId}/versions")
  @SaCheckPermission("course:course:list")
  public List<CourseVersionListItemVO> listVersions(@PathVariable Long courseId) {
    return coursePublishService.listVersions(courseId);
  }

  /**
   * 查询版本详情。
   */
  @GetMapping("/{courseId}/versions/{versionId}")
  @SaCheckPermission("course:course:list")
  public CourseVersionVO getVersion(@PathVariable Long courseId, @PathVariable Long versionId) {
    return coursePublishService.getVersion(courseId, versionId);
  }
}
