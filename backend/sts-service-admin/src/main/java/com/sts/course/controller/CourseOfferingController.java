package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.*;
import com.sts.course.service.CourseOfferingService;
import com.sts.course.vo.CourseOfferingListItemVO;
import com.sts.course.vo.CourseOfferingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 课程开班及其发布、结束、取消等生命周期管理接口。
 */
@RestController
@RequestMapping("/course/offerings")
@RequiredArgsConstructor
public class CourseOfferingController {

  private final CourseOfferingService courseOfferingService;

  /**
   * 分页查询课程开班。
   */
  @GetMapping
  @SaCheckPermission("course:offering:list")
  public PageResult<CourseOfferingListItemVO> pageOfferings(PageRequest<CourseOfferingListQuery> request) {
    return courseOfferingService.pageOfferings(request);
  }

  /**
   * 查询课程开班。
   */
  @GetMapping("/{offeringId}")
  @SaCheckPermission("course:offering:list")
  public CourseOfferingVO getOffering(@PathVariable Long offeringId) {
    return courseOfferingService.getOffering(offeringId);
  }

  /**
   * 创建课程开班。
   */
  @PostMapping
  @SaCheckPermission("course:offering:add")
  public Long createOffering(@RequestBody @Valid CourseOfferingCreateRequest request) {
    return courseOfferingService.createOffering(request);
  }

  /**
   * 更新课程开班。
   */
  @PutMapping("/{offeringId}")
  @SaCheckPermission("course:offering:edit")
  public void updateOffering(@PathVariable Long offeringId,
                             @RequestBody @Valid CourseOfferingUpdateRequest request) {
    courseOfferingService.updateOffering(offeringId, request);
  }

  /**
   * 删除课程开班。
   */
  @DeleteMapping("/{offeringId}")
  @SaCheckPermission("course:offering:delete")
  public void deleteOffering(@PathVariable Long offeringId) {
    courseOfferingService.deleteOffering(offeringId);
  }

  /**
   * 发布课程开班。
   */
  @PostMapping("/{offeringId}/publish")
  @SaCheckPermission("course:offering:publish")
  public void publishOffering(@PathVariable Long offeringId,
                              @RequestBody @Valid CourseOfferingPublishRequest request) {
    courseOfferingService.publishOffering(offeringId, request);
  }

  /**
   * 结束课程开班。
   */
  @PutMapping("/{offeringId}/end")
  @SaCheckPermission("course:offering:publish")
  public void endOffering(@PathVariable Long offeringId,
                          @RequestBody @Valid CourseOfferingStatusRequest request) {
    courseOfferingService.endOffering(offeringId, request);
  }

  /**
   * 取消课程开班。
   */
  @PutMapping("/{offeringId}/cancel")
  @SaCheckPermission("course:offering:publish")
  public void cancelOffering(@PathVariable Long offeringId,
                             @RequestBody @Valid CourseOfferingStatusRequest request) {
    courseOfferingService.cancelOffering(offeringId, request);
  }
}
