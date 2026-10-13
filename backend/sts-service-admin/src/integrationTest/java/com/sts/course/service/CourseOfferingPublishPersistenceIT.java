package com.sts.course.service;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseOfferingCreateRequest;
import com.sts.course.dto.CourseOfferingPublishRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.course.support.CourseItFixtures;
import com.sts.course.support.CourseMapperItConfig;
import com.sts.course.support.CoursePublishItHelper;
import com.sts.course.support.CoursePublishServiceItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 开课发布真 MySQL 集成测试：发布冻结 course_version_id；无课程版本时拒绝。
 */
@SpringBootTest(classes = {CourseMapperItConfig.class, CoursePublishServiceItConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseOfferingPublishPersistenceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CoursePublishService publishService;
  @Autowired
  private CourseOfferingService offeringService;
  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private CourseChapterMapper chapterMapper;
  @Autowired
  private CourseOfferingMapper offeringMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("publishOffering：发布后冻结 course_version_id")
  void publishOffering_freezesCourseVersionId() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Long courseId = CoursePublishItHelper.seedCourseWithChapter(orgId, courseMapper, chapterMapper, "IT-OFF-PUB-");
    CoursePublishItHelper.publishCourse(courseId, publishService, courseMapper);

    Course course = courseMapper.selectById(courseId);
    Long offeringId = offeringService.createOffering(createRequest(courseId, "OFF-IT-001"));

    CourseOffering draft = offeringMapper.selectById(offeringId);
    assertThat(draft.getPublishStatus()).isEqualTo("DRAFT");
    assertThat(draft.getCourseVersionId()).isNull();

    CourseOfferingPublishRequest publishRequest = new CourseOfferingPublishRequest();
    publishRequest.setVersion(draft.getVersion());
    offeringService.publishOffering(offeringId, publishRequest);

    CourseOffering published = offeringMapper.selectById(offeringId);
    assertThat(published.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(published.getCourseVersionId()).isEqualTo(course.getCurrentVersionId());
    assertThat(published.getPublishedTime()).isNotNull();
  }

  @Test
  @DisplayName("publishOffering：课程未发布或无可用版本 → 400/5062")
  void publishOffering_courseNotPublished_throws5062() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Course course = CourseItFixtures.newCourse(orgId, CourseItFixtures.uniqueCourseCode("IT-OFF-DRAFT-"));
    courseMapper.insert(course);

    Long offeringId = offeringService.createOffering(createRequest(course.getCourseId(), "OFF-IT-002"));
    CourseOffering draft = offeringMapper.selectById(offeringId);

    CourseOfferingPublishRequest publishRequest = new CourseOfferingPublishRequest();
    publishRequest.setVersion(draft.getVersion());

    assertThatThrownBy(() -> offeringService.publishOffering(offeringId, publishRequest))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(5062);
      });

    CourseOffering unchanged = offeringMapper.selectById(offeringId);
    assertThat(unchanged.getPublishStatus()).isEqualTo("DRAFT");
    assertThat(unchanged.getCourseVersionId()).isNull();
  }

  private static CourseOfferingCreateRequest createRequest(Long courseId, String offeringCode) {
    CourseOfferingCreateRequest request = new CourseOfferingCreateRequest();
    request.setOfferingCode(offeringCode);
    request.setCourseId(courseId);
    request.setOfferingName("2026 春季班");
    request.setCourseType("REQUIRED");
    request.setEnrollmentType("ASSIGNED");
    request.setStatus(1);
    return request;
  }
}
