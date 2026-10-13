package com.sts.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.question.dto.*;
import com.sts.question.service.QuestionService;
import com.sts.question.vo.QuestionImportResultVO;
import com.sts.question.vo.QuestionUserStateVO;
import com.sts.question.vo.QuestionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题目、题目版本、素材关联及个人题目状态管理接口。
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

  private final QuestionService questionService;

  /**
   * 分页查询题目。
   */
  @GetMapping("/question/questions")
  @SaCheckPermission("question:question:list")
  public PageResult<QuestionVO> page(PageRequest<QuestionQuery> request) {
    return questionService.page(request);
  }

  /**
   * 查询题目。
   */
  @GetMapping("/question/questions/{questionId}")
  @SaCheckPermission("question:question:list")
  public QuestionVO get(@PathVariable Long questionId) {
    return questionService.get(questionId);
  }

  /**
   * 创建题目。
   */
  @PostMapping("/question/questions")
  @SaCheckPermission("question:question:add")
  public Long create(@RequestBody @Valid QuestionCreateRequest request) {
    return questionService.create(request);
  }

  /**
   * 更新题目。
   */
  @PutMapping("/question/questions/{questionId}")
  @SaCheckPermission("question:question:edit")
  public void update(@PathVariable Long questionId,
                     @RequestBody @Valid QuestionUpdateRequest request) {
    questionService.updateContent(questionId, request);
  }

  /**
   * 发布题目。
   */
  @PostMapping("/question/questions/{questionId}/publish")
  @SaCheckPermission("question:question:publish")
  public void publish(@PathVariable Long questionId) {
    questionService.publish(questionId);
  }

  /**
   * 删除题目。
   */
  @DeleteMapping("/question/questions/{questionId}")
  @SaCheckPermission("question:question:delete")
  public void delete(@PathVariable Long questionId) {
    questionService.delete(questionId);
  }

  /**
   * 替换素材。
   */
  @PutMapping("/question/questions/{questionId}/assets")
  @SaCheckPermission("question:question:edit")
  public void replaceAssets(@PathVariable Long questionId,
                            @RequestBody @Valid List<QuestionAssetRequest> assets) {
    questionService.replaceAssets(questionId, assets);
  }

  /**
   * 批量导入题目。
   */
  @PostMapping("/question/banks/{questionBankId}/import")
  @SaCheckPermission("question:import")
  public QuestionImportResultVO importQuestions(@PathVariable Long questionBankId,
                                                @RequestBody @Valid QuestionImportRequest request) {
    return questionService.importQuestions(questionBankId, request);
  }

  /**
   * 新增或更新个人题目状态。
   */
  @PutMapping("/question/user-states")
  @SaCheckPermission("question:user-state:edit")
  public QuestionUserStateVO upsertUserState(@RequestBody @Valid QuestionUserStateUpsertRequest request) {
    return questionService.upsertUserState(request);
  }
}
