package com.sts.survey.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.survey.dto.SurveyCopyRequest;
import com.sts.survey.dto.SurveyCreateRequest;
import com.sts.survey.dto.SurveyInstanceCreateRequest;
import com.sts.survey.dto.SurveyInstanceQuery;
import com.sts.survey.dto.SurveyQuery;
import com.sts.survey.dto.SurveyQuestionsReplaceRequest;
import com.sts.survey.dto.SurveySubmitRequest;
import com.sts.survey.dto.SurveyUpdateRequest;
import com.sts.survey.service.SurveyInstanceService;
import com.sts.survey.service.SurveyService;
import com.sts.survey.vo.SurveyFillVO;
import com.sts.survey.vo.SurveyInstanceVO;
import com.sts.survey.vo.SurveyPreviewVO;
import com.sts.survey.vo.SurveyStatsVO;
import com.sts.survey.vo.SurveyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问卷最小闭环：设计、复制、预览、投放、填写和统计。
 */
@RestController
@RequiredArgsConstructor
public class SurveyController {

  private final SurveyService surveyService;
  private final SurveyInstanceService surveyInstanceService;

  /**
   * 分页查询问卷。
   */
  @GetMapping("/survey/surveys")
  @SaCheckPermission("survey:survey:list")
  public PageResult<SurveyVO> pageSurveys(PageRequest<SurveyQuery> request) {
    return surveyService.page(request);
  }

  /**
   * 查询问卷详情，含题目。
   */
  @GetMapping("/survey/surveys/{surveyId}")
  @SaCheckPermission("survey:survey:list")
  public SurveyVO getSurvey(@PathVariable Long surveyId) {
    return surveyService.get(surveyId);
  }

  /**
   * 创建未投放问卷。
   */
  @PostMapping("/survey/surveys")
  @SaCheckPermission("survey:survey:add")
  public Long createSurvey(@RequestBody @Valid SurveyCreateRequest request) {
    return surveyService.create(request);
  }

  /**
   * 更新未投放问卷。
   */
  @PutMapping("/survey/surveys/{surveyId}")
  @SaCheckPermission("survey:survey:edit")
  public void updateSurvey(@PathVariable Long surveyId, @RequestBody @Valid SurveyUpdateRequest request) {
    surveyService.update(surveyId, request);
  }

  /**
   * 删除未投放问卷。
   */
  @DeleteMapping("/survey/surveys/{surveyId}")
  @SaCheckPermission("survey:survey:delete")
  public void deleteSurvey(@PathVariable Long surveyId) {
    surveyService.delete(surveyId);
  }

  /**
   * 替换未投放问卷题目，仅单选/多选/文本。
   */
  @PutMapping("/survey/surveys/{surveyId}/questions")
  @SaCheckPermission("survey:survey:edit")
  public void replaceQuestions(@PathVariable Long surveyId, @RequestBody @Valid SurveyQuestionsReplaceRequest request) {
    surveyService.replaceQuestions(surveyId, request);
  }

  /**
   * 复制问卷为新草稿。
   */
  @PostMapping("/survey/surveys/{surveyId}/copy")
  @SaCheckPermission("survey:survey:add")
  public Long copySurvey(@PathVariable Long surveyId, @RequestBody @Valid SurveyCopyRequest request) {
    return surveyService.copy(surveyId, request);
  }

  /**
   * 电脑或手机预览。
   */
  @GetMapping("/survey/surveys/{surveyId}/preview")
  @SaCheckPermission("survey:survey:list")
  public SurveyPreviewVO previewSurvey(@PathVariable Long surveyId, @RequestParam String client) {
    return surveyService.preview(surveyId, client);
  }

  /**
   * 分页查询问卷投放。
   */
  @GetMapping("/survey/instances")
  @SaCheckPermission("survey:instance:list")
  public PageResult<SurveyInstanceVO> pageInstances(PageRequest<SurveyInstanceQuery> request) {
    return surveyInstanceService.page(request);
  }

  /**
   * 查询投放详情，含填写链接和二维码内容。
   */
  @GetMapping("/survey/instances/{surveyInstanceId}")
  @SaCheckPermission("survey:instance:list")
  public SurveyInstanceVO getInstance(@PathVariable Long surveyInstanceId) {
    return surveyInstanceService.get(surveyInstanceId);
  }

  /**
   * 创建投放并冻结题目快照。
   */
  @PostMapping("/survey/instances")
  @SaCheckPermission("survey:instance:add")
  public SurveyInstanceVO createInstance(@RequestBody @Valid SurveyInstanceCreateRequest request) {
    return surveyInstanceService.create(request);
  }

  /**
   * 登录用户打开填写页。
   */
  @GetMapping("/survey/instances/{surveyInstanceId}/fill")
  public SurveyFillVO getFill(@PathVariable Long surveyInstanceId) {
    return surveyInstanceService.getFill(surveyInstanceId);
  }

  /**
   * 登录用户提交本人答卷。
   */
  @PostMapping("/survey/instances/{surveyInstanceId}/submit")
  public void submit(@PathVariable Long surveyInstanceId, @RequestBody @Valid SurveySubmitRequest request) {
    surveyInstanceService.submit(surveyInstanceId, request);
  }

  /**
   * 查看结果统计；管理员或按投放可见性授权的填写人。
   */
  @GetMapping("/survey/instances/{surveyInstanceId}/stats")
  public SurveyStatsVO stats(@PathVariable Long surveyInstanceId) {
    return surveyInstanceService.stats(surveyInstanceId);
  }
}
