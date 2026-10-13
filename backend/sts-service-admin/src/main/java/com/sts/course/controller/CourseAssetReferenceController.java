package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.course.service.CourseAssetReferenceService;
import com.sts.course.vo.CourseAssetReferenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 素材被课程引用情况查询接口。
 */
@RestController
@RequestMapping("/course/asset-references")
@RequiredArgsConstructor
public class CourseAssetReferenceController {

  private final CourseAssetReferenceService assetReferenceService;

  /**
   * 查询引用关系。
   */
  @GetMapping
  @SaCheckPermission("course:item:list")
  public CourseAssetReferenceVO getReferences(@RequestParam Long assetId) {
    return assetReferenceService.getReferences(assetId);
  }
}
