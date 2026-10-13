package com.sts.course.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseCoverPersistenceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private SysFileInfoMapper fileInfoMapper;
  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void fileTenantInjectionAndCourseCoverMappingUseRealSchema() {
    Long organizationId = demoTenantId();
    TenantContext.setTenantId(organizationId);
    SysFileInfo cover = coverFile("123e4567-e89b-12d3-a456-426614174000");
    fileInfoMapper.insert(cover);

    assertThat(jdbcTemplate.queryForObject(
      "SELECT organization_id FROM us_sys_file_info WHERE file_info_id = ?",
      Long.class, cover.getFileInfoId())).isEqualTo(organizationId);

    Course course = course("COVER-PERSIST-IT");
    course.setCoverFileId(cover.getFileInfoId());
    courseMapper.insert(course);
    Course stored = courseMapper.selectById(course.getCourseId());
    assertThat(stored.getCoverFileId()).isEqualTo(cover.getFileInfoId());

    stored.setCoverFileId(null);
    courseMapper.updateById(stored);
    assertThat(courseMapper.selectById(course.getCourseId()).getCoverFileId()).isNull();
  }

  @Test
  void referenceValidationHonorsTenantAndLogicDeleteFilters() {
    Long organizationId = demoTenantId();
    CourseCoverServiceImpl service = new CourseCoverServiceImpl(
      fileInfoMapper, courseMapper, mock(StorageService.class), tenantObjectKeys());

    TenantContext.setTenantId(organizationId + 1000);
    SysFileInfo otherTenant = coverFile("223e4567-e89b-12d3-a456-426614174000");
    fileInfoMapper.insert(otherTenant);

    TenantContext.setTenantId(organizationId);
    assertThatThrownBy(() -> service.validateReference(otherTenant.getFileInfoId()))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> assertThat(error.getCode()).isEqualTo(5091));

    SysFileInfo deleted = coverFile("323e4567-e89b-12d3-a456-426614174000");
    fileInfoMapper.insert(deleted);
    jdbcTemplate.update("UPDATE us_sys_file_info SET deleted = file_info_id WHERE file_info_id = ?",
                        deleted.getFileInfoId());
    assertThatThrownBy(() -> service.validateReference(deleted.getFileInfoId()))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> assertThat(error.getCode()).isEqualTo(5091));
  }

  private static TenantObjectKeyService tenantObjectKeys() {
    TenantObjectKeyService objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.isLegacyOrCurrentTenantKey(anyString(), eq("course-cover/"))).thenReturn(true);
    return objectKeys;
  }

  private static SysFileInfo coverFile(String uuid) {
    SysFileInfo file = new SysFileInfo();
    file.setFileName("cover.png");
    file.setBucketName("course-cover");
    file.setObjectName("course-cover/2026/08/" + uuid + ".png");
    file.setFileSize(8L);
    file.setFileSuffix("png");
    file.setFileUrl("minio-private://" + file.getObjectName());
    return file;
  }

  private static Course course(String code) {
    Course course = new Course();
    course.setCourseCode(code);
    course.setCourseName("课程封面持久化测试");
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
    return course;
  }
}
