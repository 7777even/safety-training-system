package com.sts.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingReferenceResourceQuery;
import com.sts.training.service.TrainingReferenceResourceService;
import com.sts.training.vo.TrainingReferenceResourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 培训计划可引用课程、考试等资源的查询接口。
 */
@RestController
@RequiredArgsConstructor
public class TrainingReferenceResourceController {
  private final TrainingReferenceResourceService service;

  /**
   * 分页查询培训引用资源。
   */
  @GetMapping("/training/reference-resources")
  @SaCheckPermission("training:plan:list")
  public PageResult<TrainingReferenceResourceVO> page(
    PageRequest<TrainingReferenceResourceQuery> request) {
    return service.page(request);
  }
}
