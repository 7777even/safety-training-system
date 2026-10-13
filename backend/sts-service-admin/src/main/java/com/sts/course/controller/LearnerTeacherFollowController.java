package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sts.common.api.*;
import com.sts.course.dto.LearnerCourseOfferingQuery;
import com.sts.course.service.LearnerTeacherFollowService;
import com.sts.course.service.LearnerTeacherFollowService.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 当前机构教师的本人关注接口，公开字段限制由VO和Mapper共同保证。 */
@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/course/learning")
public class LearnerTeacherFollowController {
  private final LearnerTeacherFollowService service;
  /** 当前机构教师目录。 */
  @GetMapping("/teachers") public PageResult<View> teachers(PageRequest<LearnerCourseOfferingQuery> request) { return service.teachers(request); }
  /** 本人关注的教师列表。 */
  @GetMapping("/teacher-follows") public PageResult<View> follows(PageRequest<LearnerCourseOfferingQuery> request) { return service.follows(request); }
  /** 教师安全详情。 */
  @GetMapping("/teachers/{id}") public View detail(@PathVariable Long id) { return service.detail(id); }
  /** 关注或取消，不接受userId。 */
  @PutMapping("/teacher-follows/{id}") public void set(@PathVariable Long id, @RequestBody @Valid Follow request) { service.setFollow(id, request.followed()); }
  /** 本人教师关注状态。 */
  public record Follow(@NotNull Boolean followed) { }
}
