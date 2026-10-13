package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.training.dto.TrainingTemplateCategoryCreateRequest;
import com.sts.training.dto.TrainingTemplateCategoryQuery;
import com.sts.training.dto.TrainingTemplateCategoryUpdateRequest;
import com.sts.training.entity.TrainingTemplate;
import com.sts.training.entity.TrainingTemplateCategory;
import com.sts.training.mapper.TrainingTemplateCategoryMapper;
import com.sts.training.mapper.TrainingTemplateMapper;
import com.sts.training.vo.TrainingTemplateCategoryTreeVO;
import com.sts.training.vo.TrainingTemplateCategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TrainingTemplateCategoryServiceImpl
  extends BaseServiceImpl<TrainingTemplateCategoryMapper, TrainingTemplateCategory>
  implements TrainingTemplateCategoryService {

  private final TrainingTemplateCategoryMapper categoryMapper;
  private final TrainingTemplateMapper trainingTemplateMapper;

  @Override
  public PageResult<TrainingTemplateCategoryVO> page(PageRequest<TrainingTemplateCategoryQuery> request) {
    TrainingTemplateCategoryQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingTemplateCategory> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getParentId() != null, TrainingTemplateCategory::getParentId, query.getParentId())
        .like(StringUtils.hasText(query.getCategoryName()), TrainingTemplateCategory::getCategoryName, query.getCategoryName())
        .eq(query.getStatus() != null, TrainingTemplateCategory::getStatus, query.getStatus());
    }
    wrapper.orderByAsc(TrainingTemplateCategory::getSortOrder);
    Page<TrainingTemplateCategory> page = categoryMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingTemplateCategoryVO get(Long templateCategoryId) {
    return toVO(requireCategory(templateCategoryId));
  }

  @Override
  public List<TrainingTemplateCategoryTreeVO> tree(TrainingTemplateCategoryQuery query) {
    LambdaQueryWrapper<TrainingTemplateCategory> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getStatus() != null, TrainingTemplateCategory::getStatus, query.getStatus());
    }
    Map<Long, TrainingTemplateCategoryTreeVO> nodes = new LinkedHashMap<>();
    for (TrainingTemplateCategory category : categoryMapper.selectList(wrapper)) {
      TrainingTemplateCategoryTreeVO node = new TrainingTemplateCategoryTreeVO();
      BeanUtils.copyProperties(category, node);
      nodes.put(category.getTemplateCategoryId(), node);
    }
    List<TrainingTemplateCategoryTreeVO> roots = new ArrayList<>();
    for (TrainingTemplateCategoryTreeVO node : nodes.values()) {
      TrainingTemplateCategoryTreeVO parent = nodes.get(node.getParentId());
      if (node.getParentId() == null || node.getParentId() == 0L) roots.add(node);
      else if (parent != null) parent.getChildren().add(node);
    }
    Comparator<TrainingTemplateCategoryTreeVO> order = Comparator.comparing(TrainingTemplateCategoryTreeVO::getSortOrder,
                                                                            Comparator.nullsLast(Integer::compareTo)).thenComparing(
      TrainingTemplateCategoryTreeVO::getTemplateCategoryId);
    sortTree(roots, order);
    return roots;
  }

  private void sortTree(List<TrainingTemplateCategoryTreeVO> nodes, Comparator<TrainingTemplateCategoryTreeVO> order) {
    nodes.sort(order);
    nodes.forEach(node -> sortTree(node.getChildren(), order));
  }

  @Override
  @Transactional
  public Long create(TrainingTemplateCategoryCreateRequest request) {
    TrainingTemplateCategory category = new TrainingTemplateCategory();
    Long parentId = request.getParentId() == null ? 0L : request.getParentId();
    category.setParentId(parentId);
    category.setAncestors(buildAncestors(parentId));
    category.setCategoryName(request.getCategoryName().trim());
    category.setSortOrder(request.getSortOrder());
    category.setStatus(request.getStatus());
    try {
      categoryMapper.insert(category);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8037, "同父级下分类名称已存在");
    }
    return category.getTemplateCategoryId();
  }

  @Override
  @Transactional
  public void update(Long templateCategoryId, TrainingTemplateCategoryUpdateRequest request) {
    TrainingTemplateCategory category = requireCategory(templateCategoryId);
    category.setVersion(request.getVersion());
    if (request.getParentId() != null) {
      category.setParentId(request.getParentId());
      category.setAncestors(buildAncestors(request.getParentId()));
    }
    category.setCategoryName(request.getCategoryName().trim());
    category.setSortOrder(request.getSortOrder());
    category.setStatus(request.getStatus());
    int updated;
    try {
      updated = categoryMapper.updateById(category);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8037, "同父级下分类名称已存在");
    }
    if (updated == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8041, "模板分类版本冲突");
    }
  }

  @Override
  @Transactional
  public void delete(Long templateCategoryId) {
    requireCategory(templateCategoryId);
    if (categoryMapper.selectCount(new LambdaQueryWrapper<TrainingTemplateCategory>()
                                     .eq(TrainingTemplateCategory::getParentId, templateCategoryId)) > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8039, "模板分类存在子分类，不可删除");
    }
    if (trainingTemplateMapper.selectCount(new LambdaQueryWrapper<TrainingTemplate>()
                                             .eq(TrainingTemplate::getTemplateCategoryId, templateCategoryId)) > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8040, "模板分类已被培训模板引用，不可删除");
    }
    this.removeById(templateCategoryId);
  }

  TrainingTemplateCategory requireCategory(Long id) {
    TrainingTemplateCategory category = categoryMapper.selectById(id);
    if (category == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8030, "模板分类不存在");
    }
    return category;
  }

  private String buildAncestors(Long parentId) {
    if (parentId == null || parentId == 0L) {
      return "0";
    }
    TrainingTemplateCategory parent = categoryMapper.selectById(parentId);
    if (parent == null) {
      return "0";
    }
    return parent.getAncestors() + "," + parentId;
  }

  private TrainingTemplateCategoryVO toVO(TrainingTemplateCategory category) {
    TrainingTemplateCategoryVO vo = new TrainingTemplateCategoryVO();
    BeanUtils.copyProperties(category, vo);
    return vo;
  }
}
