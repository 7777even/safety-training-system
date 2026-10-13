package com.sts.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.question.dto.*;
import com.sts.question.entity.QuestionBank;
import com.sts.question.entity.QuestionBankCategory;
import com.sts.question.entity.QuestionBankKnowledge;
import com.sts.question.mapper.QuestionBankCategoryMapper;
import com.sts.question.mapper.QuestionBankKnowledgeMapper;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.question.vo.BankCategoryVO;
import com.sts.question.vo.QuestionBankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl
  extends BaseServiceImpl<QuestionBankMapper, QuestionBank>
  implements QuestionBankService {

  private final QuestionBankCategoryMapper categoryMapper;
  private final QuestionBankMapper bankMapper;
  private final QuestionBankKnowledgeMapper bankKnowledgeMapper;
  private final QuestionBankCategoryDeleteHelper categoryDeleteHelper;

  @Override
  public List<BankCategoryVO> listCategories() {
    return categoryMapper.selectList(new LambdaQueryWrapper<QuestionBankCategory>()
        .orderByAsc(QuestionBankCategory::getSortOrder))
      .stream().map(this::toCategoryVO).toList();
  }

  @Override
  @Transactional
  public Long createCategory(BankCategoryCreateRequest request) {
    QuestionBankCategory entity = new QuestionBankCategory();
    entity.setCategoryName(request.getCategoryName().trim());
    entity.setDescription(request.getDescription());
    entity.setSortOrder(request.getSortOrder());
    entity.setStatus(request.getStatus());
    try {
      categoryMapper.insert(entity);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 6012, "同机构下题库分类名称已存在");
    }
    return entity.getBankCategoryId();
  }

  @Override
  @Transactional
  public void updateCategory(Long bankCategoryId, BankCategoryUpdateRequest request) {
    QuestionBankCategory entity = requireCategory(bankCategoryId);
    entity.setVersion(request.getVersion());
    entity.setCategoryName(request.getCategoryName().trim());
    entity.setDescription(request.getDescription());
    entity.setSortOrder(request.getSortOrder());
    entity.setStatus(request.getStatus());
    try {
      categoryMapper.updateById(entity);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 6012, "同机构下题库分类名称已存在");
    }
  }

  @Override
  @Transactional
  public void deleteCategory(Long bankCategoryId) {
    requireCategory(bankCategoryId);
    Long used = bankMapper.selectCount(new LambdaQueryWrapper<QuestionBank>()
      .eq(QuestionBank::getBankCategoryId, bankCategoryId));
    if (used != null && used > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 6013, "分类下存在题库，不能删除");
    }
    categoryDeleteHelper.removeById(bankCategoryId);
  }

  private QuestionBankCategory requireCategory(Long id) {
    QuestionBankCategory entity = categoryMapper.selectById(id);
    if (entity == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6011, "题库分类不存在");
    }
    return entity;
  }

  @Override
  public PageResult<QuestionBankVO> pageBanks(PageRequest<QuestionBankQuery> request) {
    QuestionBankQuery query = request.getQuery();
    LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper
        .eq(query.getBankCategoryId() != null, QuestionBank::getBankCategoryId, query.getBankCategoryId())
        .like(StringUtils.hasText(query.getBankName()), QuestionBank::getBankName, query.getBankName())
        .eq(StringUtils.hasText(query.getArchiveStatus()), QuestionBank::getArchiveStatus, query.getArchiveStatus())
        .eq(query.getStatus() != null, QuestionBank::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(QuestionBank::getCreateTime);
    Page<QuestionBank> page = bankMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toBankVO).toList(), page.getTotal());
  }

  @Override
  public QuestionBankVO getBank(Long questionBankId) {
    return toBankVO(requireBank(questionBankId));
  }

  @Override
  @Transactional
  public Long createBank(QuestionBankCreateRequest request) {
    QuestionBank entity = new QuestionBank();
    entity.setBankCode(request.getBankCode().trim());
    entity.setBankName(request.getBankName().trim());
    entity.setBankCategoryId(request.getBankCategoryId());
    entity.setDifficulty(request.getDifficulty());
    entity.setQuestionOrderMode(request.getQuestionOrderMode());
    entity.setArchiveStatus("ACTIVE");
    entity.setStatus(request.getStatus());
    try {
      bankMapper.insert(entity);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 6022, "同机构下题库编码已存在");
    }
    replaceBankKnowledge(entity.getQuestionBankId(), request.getKnowledgePointIds());
    return entity.getQuestionBankId();
  }

  @Override
  @Transactional
  public void updateBank(Long questionBankId, QuestionBankUpdateRequest request) {
    QuestionBank entity = requireBank(questionBankId);
    entity.setVersion(request.getVersion());
    entity.setBankName(request.getBankName().trim());
    entity.setBankCategoryId(request.getBankCategoryId());
    entity.setDifficulty(request.getDifficulty());
    entity.setQuestionOrderMode(request.getQuestionOrderMode());
    entity.setStatus(request.getStatus());
    bankMapper.updateById(entity);
    if (request.getKnowledgePointIds() != null) {
      replaceBankKnowledge(questionBankId, request.getKnowledgePointIds());
    }
  }

  @Override
  @Transactional
  public void deleteBank(Long questionBankId) {
    QuestionBank entity = requireBank(questionBankId);
    bankKnowledgeMapper.delete(new LambdaQueryWrapper<QuestionBankKnowledge>()
      .eq(QuestionBankKnowledge::getQuestionBankId, questionBankId));
    this.removeById(entity.getQuestionBankId());
  }

  private void replaceBankKnowledge(Long bankId, List<Long> knowledgePointIds) {
    bankKnowledgeMapper.delete(new LambdaQueryWrapper<QuestionBankKnowledge>()
      .eq(QuestionBankKnowledge::getQuestionBankId, bankId));
    if (knowledgePointIds == null) {
      return;
    }
    for (Long kpId : knowledgePointIds) {
      QuestionBankKnowledge link = new QuestionBankKnowledge();
      link.setQuestionBankId(bankId);
      link.setKnowledgePointId(kpId);
      bankKnowledgeMapper.insert(link);
    }
  }

  private QuestionBank requireBank(Long id) {
    QuestionBank entity = bankMapper.selectById(id);
    if (entity == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6021, "题库不存在");
    }
    return entity;
  }

  private BankCategoryVO toCategoryVO(QuestionBankCategory entity) {
    BankCategoryVO vo = new BankCategoryVO();
    vo.setBankCategoryId(entity.getBankCategoryId());
    vo.setOrganizationId(entity.getOrganizationId());
    vo.setCategoryName(entity.getCategoryName());
    vo.setDescription(entity.getDescription());
    vo.setSortOrder(entity.getSortOrder());
    vo.setStatus(entity.getStatus());
    vo.setVersion(entity.getVersion());
    vo.setCreateTime(entity.getCreateTime());
    return vo;
  }

  private QuestionBankVO toBankVO(QuestionBank entity) {
    QuestionBankVO vo = new QuestionBankVO();
    vo.setQuestionBankId(entity.getQuestionBankId());
    vo.setOrganizationId(entity.getOrganizationId());
    vo.setBankCode(entity.getBankCode());
    vo.setBankName(entity.getBankName());
    vo.setBankCategoryId(entity.getBankCategoryId());
    vo.setDifficulty(entity.getDifficulty());
    vo.setQuestionOrderMode(entity.getQuestionOrderMode());
    vo.setArchiveStatus(entity.getArchiveStatus());
    vo.setStatus(entity.getStatus());
    vo.setVersion(entity.getVersion());
    vo.setCreateTime(entity.getCreateTime());
    List<Long> kpIds = bankKnowledgeMapper.selectList(new LambdaQueryWrapper<QuestionBankKnowledge>()
        .eq(QuestionBankKnowledge::getQuestionBankId, entity.getQuestionBankId()))
      .stream().map(QuestionBankKnowledge::getKnowledgePointId).toList();
    vo.setKnowledgePointIds(kpIds.isEmpty() ? Collections.emptyList() : kpIds);
    return vo;
  }
}
