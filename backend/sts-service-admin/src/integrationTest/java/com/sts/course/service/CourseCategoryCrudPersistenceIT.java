package com.sts.course.service;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.exception.BusinessException;
import com.sts.course.converter.CourseCategoryConverter;
import com.sts.course.dto.CategoryCreateRequest;
import com.sts.course.dto.CategoryUpdateRequest;
import com.sts.course.entity.CourseCategory;
import com.sts.course.mapper.CourseCategoryMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 课程分类 CRUD 真 MySQL 集成测试（对齐 {@code MenuCrudPersistenceIT}）。
 */
@SpringBootTest(classes = {CourseMapperItConfig.class, CourseCategoryCrudPersistenceIT.ServiceConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseCategoryCrudPersistenceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseCategoryService categoryService;
  @Autowired
  private CourseCategoryMapper categoryMapper;

  @Test
  @DisplayName("分类 CRUD：真库新增、乐观锁修改并逻辑删除")
  void createUpdateAndDelete_persistsWithOptimisticLockAndLogicDelete() {
    Long categoryId = categoryService.createCategory(createRequest(0L, "安全生产"));
    CourseCategory created = categoryMapper.selectById(categoryId);
    assertThat(created).isNotNull();
    assertThat(created.getCategoryName()).isEqualTo("安全生产");
    Integer originalVersion = created.getVersion();

    categoryService.updateCategory(categoryId, updateRequest(0L, "安全设置", originalVersion));
    CourseCategory updated = categoryMapper.selectById(categoryId);
    assertThat(updated.getCategoryName()).isEqualTo("安全设置");
    assertThat(updated.getVersion()).isEqualTo(originalVersion + 1);

    categoryService.deleteCategory(categoryId);
    assertThat(categoryMapper.selectById(categoryId)).isNull();
  }

  @Test
  @DisplayName("删除分类：真库存在子分类时返回 409/5006 且保留父分类")
  void deleteCategory_childExists_conflictAndKeepsParent() {
    Long parentId = categoryService.createCategory(createRequest(0L, "父分类"));
    categoryService.createCategory(createRequest(parentId, "子分类"));

    assertThatThrownBy(() -> categoryService.deleteCategory(parentId))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(5006);
      });
    assertThat(categoryMapper.selectById(parentId)).isNotNull();
  }

  private static CategoryCreateRequest createRequest(Long parentId, String name) {
    CategoryCreateRequest request = new CategoryCreateRequest();
    request.setParentId(parentId);
    request.setCategoryName(name);
    request.setDescription("");
    request.setSortOrder(0);
    request.setStatus(1);
    return request;
  }

  private static CategoryUpdateRequest updateRequest(Long parentId, String name, Integer version) {
    CategoryUpdateRequest request = new CategoryUpdateRequest();
    request.setParentId(parentId);
    request.setCategoryName(name);
    request.setDescription("");
    request.setSortOrder(0);
    request.setStatus(1);
    request.setVersion(version);
    return request;
  }

  @TestConfiguration
  static class ServiceConfig {
    @Bean
    CourseCategoryConverter courseCategoryConverter() {
      return new CourseCategoryConverter() {
        @Override
        public com.sts.course.vo.CourseCategoryTreeVO toTreeNode(CourseCategory category) {
          return new com.sts.course.vo.CourseCategoryTreeVO();
        }

        @Override
        public CourseCategory toEntity(CategoryCreateRequest request) {
          CourseCategory category = new CourseCategory();
          category.setParentId(request.getParentId());
          category.setCategoryName(request.getCategoryName());
          category.setDescription(request.getDescription());
          category.setSortOrder(request.getSortOrder());
          category.setStatus(request.getStatus());
          return category;
        }

        @Override
        public void updateEntity(CourseCategory category, CategoryUpdateRequest request) {
          category.setParentId(request.getParentId());
          category.setCategoryName(request.getCategoryName());
          category.setDescription(request.getDescription());
          category.setSortOrder(request.getSortOrder());
          category.setStatus(request.getStatus());
        }
      };
    }

    @Bean
    CourseCategoryService courseCategoryService(
      CourseCategoryMapper categoryMapper,
      CourseCategoryConverter categoryConverter) {
      return new CourseCategoryServiceImpl(categoryMapper, categoryConverter);
    }
  }
}
