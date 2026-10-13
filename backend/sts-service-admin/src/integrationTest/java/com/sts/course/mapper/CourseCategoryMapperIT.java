package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.CourseCategory;
import com.sts.org.entity.Organization;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseCategoryMapper} 集成测试（Testcontainers 真 MySQL，schema-course.sql 已随容器初始化脚本执行）。
 * <p>
 * 重点验证 {@code uk_org_parent_name_deleted} 唯一索引：同机构同层同名并发/重复写入只能成功一次；
 * 逻辑删除（{@code deleted=主键ID}）后同名可再次插入，符合 logic-delete.md 的约定。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseCategoryMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseCategoryMapper categoryMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_org_parent_name_deleted：同机构同层同名第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateNameUnderSameParent() {
    Long orgId = defaultOrganizationId();

    CourseCategory first = category(orgId, 0L, "安全生产");
    categoryMapper.insert(first);

    CourseCategory duplicate = category(orgId, 0L, "安全生产");
    assertThatThrownBy(() -> categoryMapper.insert(duplicate))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同名可重新插入；在线记录仍全局唯一")
  void logicalDelete_allowsReinsertSameName() {
    Long orgId = defaultOrganizationId();

    CourseCategory original = category(orgId, 0L, "特种作业");
    categoryMapper.insert(original);

    int deletedRows = categoryMapper.update(null,
                                            com.baomidou.mybatisplus.core.toolkit.Wrappers.<CourseCategory>lambdaUpdate()
                                              .setSql("deleted = course_category_id")
                                              .eq(CourseCategory::getCourseCategoryId, original.getCourseCategoryId()));
    assertThat(deletedRows).isEqualTo(1);

    CourseCategory reinserted = category(orgId, 0L, "特种作业");
    categoryMapper.insert(reinserted);

    CourseCategory found = categoryMapper.selectOne(
      new LambdaQueryWrapper<CourseCategory>()
        .eq(CourseCategory::getOrganizationId, orgId)
        .eq(CourseCategory::getCategoryName, "特种作业")
        .eq(CourseCategory::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getCourseCategoryId()).isEqualTo(reinserted.getCourseCategoryId());
  }

  private Long defaultOrganizationId() {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();
    return organization.getOrganizationId();
  }

  private static CourseCategory category(Long orgId, Long parentId, String name) {
    CourseCategory category = new CourseCategory();
    category.setOrganizationId(orgId);
    category.setParentId(parentId);
    category.setAncestors("0");
    category.setCategoryName(name);
    category.setDescription("");
    category.setSortOrder(0);
    category.setStatus(1);
    return category;
  }
}
