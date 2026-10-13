package com.sts.course.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageResult;
import com.sts.course.service.CourseStatisticsService;
import com.sts.course.vo.*;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** 当前租户课程及个人学习统计。 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/course/statistics")
@SaCheckPermission("course:offering:list")
public class CourseStatisticsController {
  private final CourseStatisticsService service;
  /** 单课程/分类统计；课程不传表示当前租户全部课程。 */
  @GetMapping
  public CourseStatisticsVO statistics(@RequestParam(required=false) Long courseId,@RequestParam(required=false) Long offeringId) { return service.statistics(courseId,offeringId); }
  /** 个人学习记录明细。 */
  @GetMapping("/people")
  public PageResult<CourseStatisticsPersonVO> people(@RequestParam(required=false) Long courseId,@RequestParam(required=false) Long offeringId,
      @RequestParam(required=false) String learnerKeyword,@RequestParam(defaultValue="1") @Min(1) int pageNum,@RequestParam(defaultValue="20") @Min(1) int pageSize) {
    return service.people(courseId,offeringId,learnerKeyword,pageNum,pageSize);
  }
}
