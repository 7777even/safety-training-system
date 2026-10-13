package com.sts.course.support;

import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.service.AssetReferenceService;
import com.sts.course.mapper.*;
import com.sts.course.service.*;
import com.sts.person.service.PersonSelectionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

/**
 * 课程发布 / 开课发布集成测试的显式 Service 装配（真实 Mapper + 最小 stub 依赖）。
 */
@TestConfiguration
public class CoursePublishServiceItConfig {

  @Bean
  CourseItemService courseItemService() {
    return mock(CourseItemService.class);
  }

  @Bean
  AssetReferenceService assetReferenceService() {
    return mock(AssetReferenceService.class);
  }

  @Bean
  CourseSnapshotAssembler courseSnapshotAssembler(
    CourseCourseCategoryMapper courseCategoryRelMapper,
    CourseChapterMapper chapterMapper,
    CourseItemMapper itemMapper,
    CourseDownloadAssetMapper downloadAssetMapper,
    AssetMapper assetMapper,
    CourseItemService courseItemService,
    AssetReferenceService assetReferenceService,
    CourseItemQuestionBankMapper itemQuestionBankMapper,
    com.sts.question.mapper.QuestionBankMapper questionBankMapper,
    com.sts.paper.mapper.PaperVersionMapper paperVersionMapper,
    com.sts.paper.mapper.PaperMapper paperMapper, TeacherFollowMapper teacherMapper) {
    return new CourseSnapshotAssembler(
      courseCategoryRelMapper, chapterMapper, itemMapper, downloadAssetMapper,
      assetMapper, courseItemService, assetReferenceService, itemQuestionBankMapper,
      questionBankMapper, paperVersionMapper, paperMapper, teacherMapper);
  }

  @Bean
  CoursePublishService coursePublishService(
    CourseMapper courseMapper,
    CourseVersionMapper courseVersionMapper,
    CourseSnapshotAssembler snapshotAssembler) {
    return new CoursePublishServiceImpl(courseMapper, courseVersionMapper, snapshotAssembler);
  }

  @Bean
  PersonSelectionService personSelectionService() {
    return mock(PersonSelectionService.class);
  }

  @Bean
  CourseOfferingService courseOfferingService(
    CourseOfferingMapper offeringMapper,
    CourseMapper courseMapper,
    CourseOfferingEligibleUserMapper eligibleUserMapper,
    PersonSelectionService personSelectionService,
    CourseVersionMapper courseVersionMapper,
    CourseSnapshotAssembler snapshotAssembler) {
    return new CourseOfferingServiceImpl(offeringMapper, courseMapper, eligibleUserMapper, personSelectionService,
                                         courseVersionMapper, snapshotAssembler,
                                         mock(com.sts.exam.service.AssessmentExecutionService.class),
                                         mock(com.sts.exam.service.AssessmentReviewerService.class));
  }
}
