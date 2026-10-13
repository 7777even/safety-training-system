package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.CourseAssessmentResultQuery;
import com.sts.course.service.CourseAssessmentManagementService;
import com.sts.course.vo.CourseAssessmentResultVO;
import com.sts.exam.dto.ExamReviewTaskQuery;
import com.sts.exam.dto.ExamReviewersReplaceRequest;
import com.sts.exam.dto.ReviewAnswerRequest;
import com.sts.exam.vo.ExamReviewTaskVO;
import com.sts.exam.vo.ExamReviewerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程测验阅卷人、批阅任务和自动生效结果接口。
 */
@RestController
@RequestMapping("/course/offerings/{offeringId}/items/{itemId}/assessment")
@RequiredArgsConstructor
public class CourseAssessmentManagementController {
  private final CourseAssessmentManagementService service;

  @GetMapping("/reviewers")
  @SaCheckPermission("course:offering:list")
  public List<ExamReviewerVO> listReviewers(@PathVariable Long offeringId,
                                            @PathVariable Long itemId) {
    return service.listReviewers(offeringId, itemId);
  }

  @PutMapping("/reviewers")
  @SaCheckPermission("course:offering:edit")
  public void replaceReviewers(@PathVariable Long offeringId, @PathVariable Long itemId,
                               @RequestBody @Valid ExamReviewersReplaceRequest request) {
    service.replaceReviewers(offeringId, itemId, request);
  }

  @GetMapping("/review-tasks")
  @SaCheckPermission("exam:review")
  public PageResult<ExamReviewTaskVO> pageReviewTasks(@PathVariable Long offeringId,
                                                      @PathVariable Long itemId,
                                                      PageRequest<ExamReviewTaskQuery> request) {
    return service.pageReviewTasks(offeringId, itemId, request);
  }

  @PostMapping("/answers/{answerId}/review")
  @SaCheckPermission("exam:review")
  public void reviewAnswer(@PathVariable Long offeringId, @PathVariable Long itemId,
                           @PathVariable Long answerId,
                           @RequestBody @Valid ReviewAnswerRequest request) {
    service.reviewAnswer(offeringId, itemId, answerId, request);
  }

  @GetMapping("/results")
  @SaCheckPermission("course:learning:view")
  public PageResult<CourseAssessmentResultVO> pageResults(@PathVariable Long offeringId,
                                                          @PathVariable Long itemId,
                                                          PageRequest<CourseAssessmentResultQuery> request) {
    return service.pageResults(offeringId, itemId, request);
  }
}
