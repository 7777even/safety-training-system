package com.sts.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.service.TrainingStandardService;
import com.sts.training.vo.TrainingStandardVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 培训标准及标准规则发布管理接口。
 */
@RestController
@RequiredArgsConstructor
public class TrainingStandardController {

  private final TrainingStandardService trainingStandardService;

  /**
   * 分页查询培训标准。
   */
  @GetMapping("/training/standards")
  @SaCheckPermission("training:standard:list")
  public PageResult<TrainingStandardVO> page(PageRequest<TrainingStandardQuery> request) {
    return trainingStandardService.page(request);
  }

  /**
   * 查询培训标准。
   */
  @GetMapping("/training/standards/{trainingStandardId}")
  @SaCheckPermission("training:standard:list")
  public TrainingStandardVO get(@PathVariable Long trainingStandardId) {
    return trainingStandardService.get(trainingStandardId);
  }

  /**
   * 创建培训标准。
   */
  @PostMapping("/training/standards")
  @SaCheckPermission("training:standard:add")
  public Long create(@RequestBody @Valid TrainingStandardCreateRequest request) {
    return trainingStandardService.create(request);
  }

  /**
   * 更新培训标准。
   */
  @PutMapping("/training/standards/{trainingStandardId}")
  @SaCheckPermission("training:standard:edit")
  public void update(@PathVariable Long trainingStandardId,
                     @RequestBody @Valid TrainingStandardUpdateRequest request) {
    trainingStandardService.update(trainingStandardId, request);
  }

  /**
   * 删除培训标准。
   */
  @DeleteMapping("/training/standards/{trainingStandardId}")
  @SaCheckPermission("training:standard:delete")
  public void delete(@PathVariable Long trainingStandardId) {
    trainingStandardService.delete(trainingStandardId);
  }

  /**
   * 替换规则。
   */
  @PutMapping("/training/standards/{trainingStandardId}/rules")
  @SaCheckPermission("training:standard:edit")
  public void replaceRules(@PathVariable Long trainingStandardId,
                           @RequestBody @Valid TrainingStandardRulesReplaceRequest request) {
    trainingStandardService.replaceRules(trainingStandardId, request);
  }

  /**
   * 发布培训标准。
   */
  @PostMapping("/training/standards/{trainingStandardId}/publish")
  @SaCheckPermission("training:standard:publish")
  public void publish(@PathVariable Long trainingStandardId,
                      @RequestBody @Valid TrainingPublishRequest request) {
    trainingStandardService.publish(trainingStandardId, request.getVersion());
  }
}
