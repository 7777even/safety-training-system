package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sts.common.api.*;
import com.sts.course.dto.LearnerCourseOfferingQuery;
import com.sts.course.service.LearnerCourseFavoriteService;
import com.sts.course.service.LearnerCourseFavoriteService.View;
import com.sts.question.dto.LearnerPracticeModels.Favorite;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 本人课程收藏和只读详情，54/58共用同一业务关系。 */
@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/course/learning")
public class LearnerCourseFavoriteController {
  private final LearnerCourseFavoriteService service;
  /** 本人可访问课程及收藏状态。 */
  @GetMapping("/favorite-course-catalog") public PageResult<View> catalog(PageRequest<LearnerCourseOfferingQuery> request) { return service.catalog(request); }
  /** 本人关注/收藏课程。 */
  @GetMapping("/favorite-courses") public PageResult<View> favorites(PageRequest<LearnerCourseOfferingQuery> request) { return service.favorites(request); }
  /** 受控课程详情，不自动报名。 */
  @GetMapping("/favorite-courses/{id}") public View detail(@PathVariable Long id) { return service.detail(id); }
  /** 幂等收藏/取消。 */
  @PutMapping("/favorite-courses/{id}") public void set(@PathVariable Long id, @RequestBody @Valid Favorite request) { service.setFavorite(id, request.favorite()); }
}
