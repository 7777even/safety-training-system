package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.converter.CourseCategoryConverter;
import com.sts.course.dto.CategoryCreateRequest;
import com.sts.course.dto.CategoryUpdateRequest;
import com.sts.course.entity.CourseCategory;
import com.sts.course.mapper.CourseCategoryMapper;
import com.sts.course.vo.CourseCategoryTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseCategoryServiceImpl extends BaseServiceImpl<CourseCategoryMapper, CourseCategory>
  implements CourseCategoryService {

  private final CourseCategoryMapper categoryMapper;
  private final CourseCategoryConverter categoryConverter;

  @Override
  public List<CourseCategoryTreeVO> getCategoryTree(String categoryName, Integer status) {
    LambdaQueryWrapper<CourseCategory> wrapper = new LambdaQueryWrapper<>();
    wrapper.like(StringUtils.hasText(categoryName), CourseCategory::getCategoryName, categoryName)
      .eq(status != null, CourseCategory::getStatus, status)
      .orderByAsc(CourseCategory::getSortOrder)
      .orderByAsc(CourseCategory::getCourseCategoryId);
    List<CourseCategory> categories = categoryMapper.selectList(wrapper);
    Map<Long, CourseCategoryTreeVO> nodeMap = categories.stream().collect(Collectors.toMap(
      CourseCategory::getCourseCategoryId,
      categoryConverter::toTreeNode
    ));
    List<CourseCategoryTreeVO> roots = new ArrayList<>();
    for (CourseCategory category : categories) {
      CourseCategoryTreeVO node = nodeMap.get(category.getCourseCategoryId());
      if (category.getParentId() == null || category.getParentId() == 0L) {
        roots.add(node);
      } else {
        CourseCategoryTreeVO parent = nodeMap.get(category.getParentId());
        if (parent != null) {
          parent.getChildren().add(node);
        } else {
          roots.add(node);
        }
      }
    }
    return roots;
  }

  @Override
  @Transactional
  public Long createCategory(CategoryCreateRequest request) {
    validateParent(request.getParentId(), null);
    assertNameNotDuplicated(request.getParentId(), request.getCategoryName(), null);
    String ancestors = buildAncestors(request.getParentId());

    CourseCategory category = categoryConverter.toEntity(request);
    category.setAncestors(ancestors);
    try {
      categoryMapper.insert(category);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 5005, "同层分类名称已存在");
    }
    return category.getCourseCategoryId();
  }

  @Override
  @Transactional
  public void updateCategory(Long categoryId, CategoryUpdateRequest request) {
    CourseCategory category = requireCategory(categoryId);
    category.setVersion(request.getVersion());
    validateParent(request.getParentId(), categoryId);
    assertNotMoveToDescendant(categoryId, request.getParentId());
    assertNameNotDuplicated(request.getParentId(), request.getCategoryName(), categoryId);

    boolean parentChanged = !category.getParentId().equals(request.getParentId());
    categoryConverter.updateEntity(category, request);

    try {
      if (parentChanged) {
        category.setAncestors(buildAncestors(request.getParentId()));
        categoryMapper.updateByIdWithVersionCheck(category);
        refreshChildAncestors(categoryId, category.getAncestors());
      } else {
        categoryMapper.updateByIdWithVersionCheck(category);
      }
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 5005, "同层分类名称已存在");
    }
  }

  @Override
  @Transactional
  public void deleteCategory(Long categoryId) {
    requireCategory(categoryId);
    Long childCount = categoryMapper.selectCount(
      new LambdaQueryWrapper<CourseCategory>()
        .eq(CourseCategory::getParentId, categoryId));
    if (childCount != null && childCount > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5006, "存在子分类，无法删除");
    }
    // 课程与分类关联（sts_course_course_category）在本 Task 无写入路径，暂不校验课程引用；
    // Task 2（sts_course_course 建表）实现课程分类关联读写后，此处须补充“分类被课程引用时禁止删除”的校验。
    this.removeById(categoryId);
  }

  private List<Long> listDescendantIds(Long categoryId) {
    if (categoryId == null) {
      return List.of();
    }
    List<CourseCategory> all = categoryMapper.selectList(
      new LambdaQueryWrapper<>());
    Map<Long, List<Long>> childrenMap = all.stream()
      .collect(Collectors.groupingBy(
        c -> c.getParentId() == null ? 0L : c.getParentId(),
        Collectors.mapping(CourseCategory::getCourseCategoryId, Collectors.toList())
      ));
    Set<Long> ids = new HashSet<>();
    collectDescendants(categoryId, childrenMap, ids);
    return new ArrayList<>(ids);
  }

  private void collectDescendants(Long categoryId, Map<Long, List<Long>> childrenMap, Set<Long> ids) {
    ids.add(categoryId);
    for (Long childId : childrenMap.getOrDefault(categoryId, List.of())) {
      collectDescendants(childId, childrenMap, ids);
    }
  }

  private void assertNotMoveToDescendant(Long categoryId, Long newParentId) {
    if (newParentId == null || newParentId == 0L) {
      return;
    }
    List<Long> descendants = listDescendantIds(categoryId);
    if (descendants.contains(newParentId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5004, "不能将分类移动到其子分类下");
    }
  }

  private void refreshChildAncestors(Long categoryId, String parentAncestors) {
    String currentPrefix = parentAncestors + "," + categoryId;
    List<CourseCategory> children = categoryMapper.selectList(
      new LambdaQueryWrapper<CourseCategory>()
        .eq(CourseCategory::getParentId, categoryId));
    for (CourseCategory child : children) {
      child.setAncestors(currentPrefix);
      categoryMapper.updateById(child);
      refreshChildAncestors(child.getCourseCategoryId(), currentPrefix);
    }
  }

  private String buildAncestors(Long parentId) {
    if (parentId == null || parentId == 0L) {
      return "0";
    }
    CourseCategory parent = requireCategory(parentId);
    return parent.getAncestors() + "," + parentId;
  }

  private void validateParent(Long parentId, Long selfCategoryId) {
    if (parentId == null || parentId == 0L) {
      return;
    }
    if (selfCategoryId != null && parentId.equals(selfCategoryId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5003, "父分类不能是自身");
    }
    CourseCategory parent = categoryMapper.selectOne(
      new LambdaQueryWrapper<CourseCategory>()
        .eq(CourseCategory::getCourseCategoryId, parentId));
    if (parent == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5002, "父分类不存在");
    }
  }

  private void assertNameNotDuplicated(Long parentId,
                                       String categoryName,
                                       Long excludeCategoryId) {
    LambdaQueryWrapper<CourseCategory> wrapper = new LambdaQueryWrapper<CourseCategory>()
      .eq(CourseCategory::getParentId, parentId)
      .eq(CourseCategory::getCategoryName, categoryName);
    if (excludeCategoryId != null) {
      wrapper.ne(CourseCategory::getCourseCategoryId, excludeCategoryId);
    }
    Long count = categoryMapper.selectCount(wrapper);
    if (count != null && count > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5005, "同层分类名称已存在");
    }
  }

  private CourseCategory requireCategory(Long categoryId) {
    CourseCategory category = categoryMapper.selectOne(
      new LambdaQueryWrapper<CourseCategory>()
        .eq(CourseCategory::getCourseCategoryId, categoryId));
    if (category == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5001, "课程分类不存在");
    }
    return category;
  }
}
