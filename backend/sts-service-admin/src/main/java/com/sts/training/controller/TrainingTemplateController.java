package com.sts.training.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.service.TrainingTemplateCategoryService;
import com.sts.training.service.TrainingTemplateService;
import com.sts.training.vo.TrainingTemplateCategoryTreeVO;
import com.sts.training.vo.TrainingTemplateCategoryVO;
import com.sts.training.vo.TrainingTemplateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 培训模板、模板分类、阶段及发布管理接口。
 */
@RestController
@RequiredArgsConstructor
public class TrainingTemplateController {

  private final TrainingTemplateCategoryService categoryService;
  private final TrainingTemplateService templateService;

  /**
   * 查询培训模板分类树。
   */
  @GetMapping("/training/template-categories/tree")
  @SaCheckPermission("training:template:list")
  public List<TrainingTemplateCategoryTreeVO> treeCategories(TrainingTemplateCategoryQuery query) {
    return categoryService.tree(query);
  }

  /**
   * 分页查询分类。
   */
  @GetMapping("/training/template-categories")
  @SaCheckPermission("training:template:list")
  public PageResult<TrainingTemplateCategoryVO> pageCategories(PageRequest<TrainingTemplateCategoryQuery> request) {
    return categoryService.page(request);
  }

  /**
   * 查询分类。
   */
  @GetMapping("/training/template-categories/{templateCategoryId}")
  @SaCheckPermission("training:template:list")
  public TrainingTemplateCategoryVO getCategory(@PathVariable Long templateCategoryId) {
    return categoryService.get(templateCategoryId);
  }

  /**
   * 创建分类。
   */
  @PostMapping("/training/template-categories")
  @SaCheckPermission("training:template:add")
  public Long createCategory(@RequestBody @Valid TrainingTemplateCategoryCreateRequest request) {
    return categoryService.create(request);
  }

  /**
   * 更新分类。
   */
  @PutMapping("/training/template-categories/{templateCategoryId}")
  @SaCheckPermission("training:template:edit")
  public void updateCategory(@PathVariable Long templateCategoryId,
                             @RequestBody @Valid TrainingTemplateCategoryUpdateRequest request) {
    categoryService.update(templateCategoryId, request);
  }

  /**
   * 删除分类。
   */
  @DeleteMapping("/training/template-categories/{templateCategoryId}")
  @SaCheckPermission("training:template:delete")
  public void deleteCategory(@PathVariable Long templateCategoryId) {
    categoryService.delete(templateCategoryId);
  }

  /**
   * 分页查询模板。
   */
  @GetMapping("/training/templates")
  @SaCheckPermission("training:template:list")
  public PageResult<TrainingTemplateVO> pageTemplates(PageRequest<TrainingTemplateQuery> request) {
    return templateService.page(request);
  }

  /**
   * 查询模板。
   */
  @GetMapping("/training/templates/{trainingTemplateId}")
  @SaCheckPermission("training:template:list")
  public TrainingTemplateVO getTemplate(@PathVariable Long trainingTemplateId) {
    return templateService.get(trainingTemplateId);
  }

  /**
   * 创建模板。
   */
  @PostMapping("/training/templates")
  @SaCheckPermission("training:template:add")
  public Long createTemplate(@RequestBody @Valid TrainingTemplateCreateRequest request) {
    return templateService.create(request);
  }

  /**
   * 更新模板。
   */
  @PutMapping("/training/templates/{trainingTemplateId}")
  @SaCheckPermission("training:template:edit")
  public void updateTemplate(@PathVariable Long trainingTemplateId,
                             @RequestBody @Valid TrainingTemplateUpdateRequest request) {
    templateService.update(trainingTemplateId, request);
  }

  /**
   * 删除模板。
   */
  @DeleteMapping("/training/templates/{trainingTemplateId}")
  @SaCheckPermission("training:template:delete")
  public void deleteTemplate(@PathVariable Long trainingTemplateId) {
    templateService.delete(trainingTemplateId);
  }

  /**
   * 替换阶段。
   */
  @PutMapping("/training/templates/{trainingTemplateId}/stages")
  @SaCheckPermission("training:template:edit")
  public void replaceStages(@PathVariable Long trainingTemplateId,
                            @RequestBody @Valid TrainingTemplateStagesReplaceRequest request) {
    templateService.replaceStages(trainingTemplateId, request);
  }

  /**
   * 发布模板。
   */
  @PostMapping("/training/templates/{trainingTemplateId}/publish")
  @SaCheckPermission("training:template:publish")
  public void publishTemplate(@PathVariable Long trainingTemplateId,
                              @RequestBody @Valid TrainingPublishRequest request) {
    templateService.publish(trainingTemplateId, request.getVersion());
  }
}
