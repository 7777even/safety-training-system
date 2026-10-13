package com.sts.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.question.dto.*;
import com.sts.question.service.QuestionBankService;
import com.sts.question.vo.BankCategoryVO;
import com.sts.question.vo.QuestionBankVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题库及题库分类管理接口。
 */
@RestController
@RequiredArgsConstructor
public class QuestionBankController {

  private final QuestionBankService questionBankService;

  /**
   * 查询分类列表。
   */
  @GetMapping("/question/bank-categories")
  @SaCheckPermission("question:bank:list")
  public List<BankCategoryVO> listCategories() {
    return questionBankService.listCategories();
  }

  /**
   * 创建分类。
   */
  @PostMapping("/question/bank-categories")
  @SaCheckPermission("question:bank:add")
  public Long createCategory(@RequestBody @Valid BankCategoryCreateRequest request) {
    return questionBankService.createCategory(request);
  }

  /**
   * 更新分类。
   */
  @PutMapping("/question/bank-categories/{bankCategoryId}")
  @SaCheckPermission("question:bank:edit")
  public void updateCategory(@PathVariable Long bankCategoryId,
                             @RequestBody @Valid BankCategoryUpdateRequest request) {
    questionBankService.updateCategory(bankCategoryId, request);
  }

  /**
   * 删除分类。
   */
  @DeleteMapping("/question/bank-categories/{bankCategoryId}")
  @SaCheckPermission("question:bank:delete")
  public void deleteCategory(@PathVariable Long bankCategoryId) {
    questionBankService.deleteCategory(bankCategoryId);
  }

  /**
   * 分页查询题库。
   */
  @GetMapping("/question/banks")
  @SaCheckPermission("question:bank:list")
  public PageResult<QuestionBankVO> pageBanks(PageRequest<QuestionBankQuery> request) {
    return questionBankService.pageBanks(request);
  }

  /**
   * 查询题库。
   */
  @GetMapping("/question/banks/{questionBankId}")
  @SaCheckPermission("question:bank:list")
  public QuestionBankVO getBank(@PathVariable Long questionBankId) {
    return questionBankService.getBank(questionBankId);
  }

  /**
   * 创建题库。
   */
  @PostMapping("/question/banks")
  @SaCheckPermission("question:bank:add")
  public Long createBank(@RequestBody @Valid QuestionBankCreateRequest request) {
    return questionBankService.createBank(request);
  }

  /**
   * 更新题库。
   */
  @PutMapping("/question/banks/{questionBankId}")
  @SaCheckPermission("question:bank:edit")
  public void updateBank(@PathVariable Long questionBankId,
                         @RequestBody @Valid QuestionBankUpdateRequest request) {
    questionBankService.updateBank(questionBankId, request);
  }

  /**
   * 删除题库。
   */
  @DeleteMapping("/question/banks/{questionBankId}")
  @SaCheckPermission("question:bank:delete")
  public void deleteBank(@PathVariable Long questionBankId) {
    questionBankService.deleteBank(questionBankId);
  }
}
