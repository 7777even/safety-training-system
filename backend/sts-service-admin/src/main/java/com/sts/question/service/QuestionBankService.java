package com.sts.question.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.question.dto.*;
import com.sts.question.vo.*;

import java.util.List;

public interface QuestionBankService {
  List<BankCategoryVO> listCategories();

  Long createCategory(BankCategoryCreateRequest request);

  void updateCategory(Long bankCategoryId, BankCategoryUpdateRequest request);

  void deleteCategory(Long bankCategoryId);

  PageResult<QuestionBankVO> pageBanks(PageRequest<QuestionBankQuery> request);

  QuestionBankVO getBank(Long questionBankId);

  Long createBank(QuestionBankCreateRequest request);

  void updateBank(Long questionBankId, QuestionBankUpdateRequest request);

  void deleteBank(Long questionBankId);
}
