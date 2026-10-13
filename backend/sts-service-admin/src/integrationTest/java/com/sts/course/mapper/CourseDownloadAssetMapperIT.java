package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseDownloadAsset;
import com.sts.org.entity.Organization;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseDownloadAssetMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_course_asset_deleted(course_id, asset_id, deleted)}：同课程同素材只能有一条在线记录；
 * 逻辑删除后编码可复用。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseDownloadAssetMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseDownloadAssetMapper downloadAssetMapper;

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Autowired
  private DataSource dataSource;

  @Test
  @DisplayName("uk_course_asset_deleted：同课程同素材第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateCourseAsset() {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, "SAFETY-DL-001");
    Long assetId = insertAsset(orgId, "ASSET-DL-001");

    downloadAssetMapper.insert(downloadAsset(courseId, assetId));

    assertThatThrownBy(() -> downloadAssetMapper.insert(downloadAsset(courseId, assetId)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同课程同素材可重新插入")
  void logicalDelete_allowsReinsertSameCourseAsset() {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, "SAFETY-DL-002");
    Long assetId = insertAsset(orgId, "ASSET-DL-002");

    CourseDownloadAsset original = downloadAsset(courseId, assetId);
    downloadAssetMapper.insert(original);

    int deletedRows = downloadAssetMapper.update(null,
                                                 Wrappers.<CourseDownloadAsset>lambdaUpdate()
                                                   .setSql("deleted = course_download_asset_id")
                                                   .eq(CourseDownloadAsset::getCourseDownloadAssetId,
                                                       original.getCourseDownloadAssetId()));
    assertThat(deletedRows).isEqualTo(1);

    CourseDownloadAsset reinserted = downloadAsset(courseId, assetId);
    downloadAssetMapper.insert(reinserted);

    CourseDownloadAsset found = downloadAssetMapper.selectOne(
      new LambdaQueryWrapper<CourseDownloadAsset>()
        .eq(CourseDownloadAsset::getCourseId, courseId)
        .eq(CourseDownloadAsset::getAssetId, assetId)
        .eq(CourseDownloadAsset::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getCourseDownloadAssetId()).isEqualTo(reinserted.getCourseDownloadAssetId());
  }

  private Long defaultOrganizationId() {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();
    return organization.getOrganizationId();
  }

  private Long createCourse(Long orgId, String code) {
    Course course = new Course();
    course.setOrganizationId(orgId);
    course.setCourseCode(code);
    course.setCourseName("安全教育课程");
    course.setContentMode("MULTI_CHAPTER");
    course.setCredit(BigDecimal.ONE);
    course.setPointMode("NONE");
    course.setPointValue(BigDecimal.ZERO);
    course.setSourceDownloadMode("NONE");
    course.setIsSpeedPlayAllowed(0);
    course.setIsStudyAnticheatEnabled(0);
    course.setIsStudyDescriptionEnabled(1);
    course.setIsWatermarkEnabled(0);
    course.setPublishStatus("DRAFT");
    course.setStatus(1);
    courseMapper.insert(course);
    return course.getCourseId();
  }

  private Long insertAsset(Long orgId, String assetCode) {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(
           """
             INSERT INTO sts_asset_asset
               (organization_id, asset_code, asset_name, asset_type, source_type, process_status, status)
             VALUES (?, ?, ?, 'DOCUMENT', 'UPLOAD', 'SUCCEEDED', 1)
             """,
           Statement.RETURN_GENERATED_KEYS)) {
      ps.setLong(1, orgId);
      ps.setString(2, assetCode);
      ps.setString(3, "测试素材");
      ps.executeUpdate();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        assertThat(rs.next()).isTrue();
        return rs.getLong(1);
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to seed asset row", ex);
    }
  }

  private static CourseDownloadAsset downloadAsset(Long courseId, Long assetId) {
    CourseDownloadAsset entity = new CourseDownloadAsset();
    entity.setCourseId(courseId);
    entity.setAssetId(assetId);
    entity.setSortOrder(0);
    entity.setStatus(1);
    return entity;
  }
}
