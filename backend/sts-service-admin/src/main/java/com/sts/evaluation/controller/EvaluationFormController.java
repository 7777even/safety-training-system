package com.sts.evaluation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.evaluation.dto.EvaluationFormCreateRequest;
import com.sts.evaluation.dto.EvaluationFormPublishRequest;
import com.sts.evaluation.dto.EvaluationFormQuery;
import com.sts.evaluation.dto.EvaluationFormUpdateRequest;
import com.sts.evaluation.service.EvaluationFormService;
import com.sts.evaluation.vo.EvaluationFormVO;
import com.sts.evaluation.vo.EvaluationFormVersionDetailVO;
import com.sts.evaluation.vo.EvaluationFormVersionSummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评价表单及其发布版本管理接口。
 */
@RestController
@RequiredArgsConstructor
public class EvaluationFormController {

  private final EvaluationFormService evaluationFormService;

  /**
   * 分页查询评价表单。
   */
  @GetMapping("/evaluation/forms")
  @SaCheckPermission("evaluation:form:list")
  public PageResult<EvaluationFormVO> page(PageRequest<EvaluationFormQuery> request) {
    return evaluationFormService.page(request);
  }

  /**
   * 查询评价表单。
   */
  @GetMapping("/evaluation/forms/{evaluationFormId}")
  @SaCheckPermission("evaluation:form:list")
  public EvaluationFormVO get(@PathVariable Long evaluationFormId) {
    return evaluationFormService.get(evaluationFormId);
  }

  /**
   * 创建评价表单。
   */
  @PostMapping("/evaluation/forms")
  @SaCheckPermission("evaluation:form:add")
  public Long create(@RequestBody @Valid EvaluationFormCreateRequest request) {
    return evaluationFormService.create(request);
  }

  /**
   * 更新评价表单。
   */
  @PutMapping("/evaluation/forms/{evaluationFormId}")
  @SaCheckPermission("evaluation:form:edit")
  public void update(@PathVariable Long evaluationFormId,
                     @RequestBody @Valid EvaluationFormUpdateRequest request) {
    evaluationFormService.update(evaluationFormId, request);
  }

  /**
   * 删除评价表单。
   */
  @DeleteMapping("/evaluation/forms/{evaluationFormId}")
  @SaCheckPermission("evaluation:form:delete")
  public void delete(@PathVariable Long evaluationFormId) {
    evaluationFormService.delete(evaluationFormId);
  }

  /**
   * 查询版本详情。
   */
  @GetMapping("/evaluation/forms/versions/{formVersionId}")
  @SaCheckPermission("evaluation:form:list")
  public EvaluationFormVersionDetailVO getVersionDetail(@PathVariable Long formVersionId) {
    return evaluationFormService.getVersionDetail(formVersionId);
  }

  /**
   * 查询版本摘要列表。
   */
  @GetMapping("/evaluation/forms/{evaluationFormId}/versions")
  @SaCheckPermission("evaluation:form:list")
  public List<EvaluationFormVersionSummaryVO> listVersionSummaries(@PathVariable Long evaluationFormId) {
    return evaluationFormService.listVersionSummaries(evaluationFormId);
  }

  /**
   * 发布评价表单的新版本。
   */
  @PostMapping("/evaluation/forms/{evaluationFormId}/publish")
  @SaCheckPermission("evaluation:form:publish")
  public EvaluationFormVersionDetailVO publishNewVersion(@PathVariable Long evaluationFormId,
                                                         @RequestBody @Valid EvaluationFormPublishRequest request) {
    return evaluationFormService.publishNewVersion(evaluationFormId, request);
  }
}
