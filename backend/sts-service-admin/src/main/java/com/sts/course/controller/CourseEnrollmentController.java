package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.CourseEnrollmentAssignRequest;
import com.sts.course.dto.CourseEnrollmentListQuery;
import com.sts.course.service.CourseEnrollmentService;
import com.sts.course.vo.CourseEnrollmentListItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 课程参与人员分配、移除及退课管理接口。
 */
@RestController
@RequiredArgsConstructor
public class CourseEnrollmentController {

  private final CourseEnrollmentService courseEnrollmentService;

  /**
   * 分页查询参与记录。
   */
  @GetMapping("/course/offerings/{offeringId}/enrollments")
  @SaCheckPermission("course:enrollment:list")
  public PageResult<CourseEnrollmentListItemVO> pageEnrollments(
    @PathVariable Long offeringId,
    PageRequest<CourseEnrollmentListQuery> request) {
    return courseEnrollmentService.pageEnrollments(offeringId, request);
  }

  /**
   * 分配参与记录。
   */
  @PostMapping("/course/offerings/{offeringId}/enrollments")
  @SaCheckPermission("course:enrollment:assign")
  public void assignEnrollments(@PathVariable Long offeringId,
                                @RequestBody @Valid CourseEnrollmentAssignRequest request) {
    courseEnrollmentService.assign(offeringId, request.getSelection());
  }

  /**
   * 移除参与记录。
   */
  @DeleteMapping("/course/enrollments/{enrollmentId}")
  @SaCheckPermission("course:enrollment:remove")
  public void removeEnrollment(@PathVariable Long enrollmentId) {
    courseEnrollmentService.removeEnrollment(enrollmentId);
  }

  /**
   * 学员自主报名课程开班。
   */
  @PostMapping("/course/offerings/{offeringId}/self-enroll")
  public Long selfEnroll(@PathVariable Long offeringId) {
    return courseEnrollmentService.selfEnroll(offeringId);
  }

  /**
   * 学员自主退出课程。
   */
  @PostMapping("/course/enrollments/{enrollmentId}/withdraw")
  public void selfWithdraw(@PathVariable Long enrollmentId) {
    courseEnrollmentService.selfWithdraw(enrollmentId);
  }
}
