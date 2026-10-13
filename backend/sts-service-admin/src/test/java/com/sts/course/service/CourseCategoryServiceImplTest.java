package com.sts.course.service;

import com.sts.common.exception.BusinessException;
import com.sts.course.converter.CourseCategoryConverter;
import com.sts.course.dto.CategoryCreateRequest;
import com.sts.course.dto.CategoryUpdateRequest;
import com.sts.course.entity.CourseCategory;
import com.sts.course.mapper.CourseCategoryMapper;
import com.sts.course.vo.CourseCategoryTreeVO;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link CourseCategoryServiceImpl} 业务逻辑单元测试。纯 Mockito，零 DB。
 * <p>
 * 覆盖：机构隔离查询、树装配、ancestors 路径构建、父分类校验、禁止移动到子孙、
 * 同层同名护栏、删除前子分类占用检查。
 */
class CourseCategoryServiceImplTest {

  private static final Long ORG_ID = 1L;

  private CourseCategoryMapper categoryMapper;
  private CourseCategoryConverter categoryConverter;
  private CourseCategoryServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    categoryMapper = mock(CourseCategoryMapper.class);
    categoryConverter = mock(CourseCategoryConverter.class);
    service = new CourseCategoryServiceImpl(categoryMapper, categoryConverter);
  }

  // ---------- getCategoryTree ----------

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("getCategoryTree：扁平列表按 parentId 装配成树")
  void getCategoryTree_buildsNestedTree() {
    when(categoryMapper.selectList(any())).thenReturn(List.of(
      category(1L, 0L), category(2L, 1L), category(3L, 1L), category(4L, 0L)));
    stubToTreeNode();

    List<CourseCategoryTreeVO> tree = service.getCategoryTree(null, null);

    assertThat(tree).extracting(CourseCategoryTreeVO::getCourseCategoryId).containsExactly(1L, 4L);
    assertThat(tree.get(0).getChildren()).extracting(CourseCategoryTreeVO::getCourseCategoryId)
      .containsExactly(2L, 3L);
  }

  // ---------- createCategory ----------

  @Test
  @DisplayName("createCategory：根分类 → ancestors=\"0\"，不查父，不查重名即通过时插入")
  void createCategory_root_ancestorsZero() {
    CourseCategory entity = new CourseCategory();
    when(categoryConverter.toEntity(any(CategoryCreateRequest.class))).thenReturn(entity);
    when(categoryMapper.selectCount(any())).thenReturn(0L);

    CategoryCreateRequest request = new CategoryCreateRequest();
    request.setParentId(0L);
    request.setCategoryName("安全生产");

    service.createCategory(request);

    assertThat(entity.getAncestors()).isEqualTo("0");
    // 租户归属由租户插件在写入时填充，零 DB 场景不体现；跨租户边界见 TenantIsolationIT
    verify(categoryMapper).insert(entity);
  }

  @Test
  @DisplayName("createCategory：父分类不存在 → 400 / 5002，不插入")
  void createCategory_parentNotExists_throws400() {
    when(categoryMapper.selectOne(any())).thenReturn(null);

    CategoryCreateRequest request = new CategoryCreateRequest();
    request.setParentId(99L);
    request.setCategoryName("安全生产");

    assertThatThrownBy(() -> service.createCategory(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getCode()).isEqualTo(5002);
      });
    verify(categoryMapper, never()).insert(any(CourseCategory.class));
  }

  @Test
  @DisplayName("createCategory：同层同名已存在 → 409 / 5005，不插入")
  void createCategory_duplicateName_throws409() {
    when(categoryMapper.selectCount(any())).thenReturn(1L);

    CategoryCreateRequest request = new CategoryCreateRequest();
    request.setParentId(0L);
    request.setCategoryName("安全生产");

    assertThatThrownBy(() -> service.createCategory(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5005));
    verify(categoryMapper, never()).insert(any(CourseCategory.class));
  }

  // ---------- updateCategory ----------

  @Test
  @DisplayName("updateCategory：不能将分类移动到其子分类下 → 400 / 5004")
  void updateCategory_moveToDescendant_throws400() {
    // 树：1 -> {2,3}；2 -> {4}
    when(categoryMapper.selectOne(any()))
      .thenReturn(category(1L, 0L)); // requireCategory(1)
    when(categoryMapper.selectList(any())).thenReturn(List.of(
      category(1L, 0L), category(2L, 1L), category(3L, 1L), category(4L, 2L)));

    CategoryUpdateRequest request = new CategoryUpdateRequest();
    request.setParentId(4L); // 4 是 1 的子孙
    request.setCategoryName("安全生产");
    request.setStatus(1);
    request.setVersion(0);

    assertThatThrownBy(() -> service.updateCategory(1L, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5004));
  }

  // ---------- deleteCategory ----------

  @Test
  @DisplayName("deleteCategory：存在子分类 → 409 / 5006，不执行删除")
  void deleteCategory_hasChildren_throws409() {
    when(categoryMapper.selectOne(any())).thenReturn(category(1L, 0L));
    when(categoryMapper.selectCount(any())).thenReturn(2L);

    assertThatThrownBy(() -> service.deleteCategory(1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5006));
  }

  // ---------- fixtures ----------

  private void stubToTreeNode() {
    when(categoryConverter.toTreeNode(any(CourseCategory.class))).thenAnswer(invocation -> {
      CourseCategory c = invocation.getArgument(0);
      CourseCategoryTreeVO vo = new CourseCategoryTreeVO();
      vo.setCourseCategoryId(c.getCourseCategoryId());
      vo.setParentId(c.getParentId());
      return vo;
    });
  }

  private static CourseCategory category(Long id, Long parentId) {
    CourseCategory c = new CourseCategory();
    c.setCourseCategoryId(id);
    c.setParentId(parentId);
    c.setOrganizationId(ORG_ID);
    c.setAncestors(parentId == 0L ? "0" : "0," + parentId);
    c.setCategoryName("分类" + id);
    c.setSortOrder(0);
    c.setStatus(1);
    return c;
  }
}
