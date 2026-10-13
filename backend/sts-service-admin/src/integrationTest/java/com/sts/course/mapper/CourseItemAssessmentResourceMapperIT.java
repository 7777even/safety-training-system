package com.sts.course.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.course.entity.CourseItem;
import com.sts.course.entity.CourseItemQuestionBank;
import com.sts.course.support.CourseItFixtures;
import com.sts.course.support.CourseMapperItConfig;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = CourseMapperItConfig.class)
@ActiveProfiles("test")
@Transactional
class CourseItemAssessmentResourceMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private CourseChapterMapper chapterMapper;
  @Autowired
  private CourseItemMapper itemMapper;
  @Autowired
  private CourseItemQuestionBankMapper itemQuestionBankMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void finalAssessmentAndAssetLinkRoundTripAndClear() {
    CourseItem quiz = insertItem("QUIZ");
    quiz.setIsFinalAssessment(1); quiz.setPassScore(new java.math.BigDecimal("60.00")); itemMapper.updateById(quiz);
    assertThat(itemMapper.selectCurrent(quiz.getCourseItemId()).getIsFinalAssessment()).isEqualTo(1);
    assertThat(itemMapper.selectById(quiz.getCourseItemId()).getPassScore()).isEqualByComparingTo("60");
    CourseItem asset = insertItem("ASSET"); asset.setLinkedQuizItemId(quiz.getCourseItemId()); itemMapper.updateById(asset);
    assertThat(itemMapper.selectById(asset.getCourseItemId()).getLinkedQuizItemId()).isEqualTo(quiz.getCourseItemId());
    asset.setLinkedQuizItemId(null); itemMapper.updateById(asset);
    quiz.setPassScore(null); itemMapper.updateById(quiz);
    assertThat(itemMapper.selectById(asset.getCourseItemId()).getLinkedQuizItemId()).isNull();
    assertThat(itemMapper.selectById(quiz.getCourseItemId()).getPassScore()).isNull();
  }

  @Test
  void quizMapsPaperVersionAndCourseSchemaHasNoExamReference() {
    CourseItem item = insertItem("QUIZ");
    item.setPaperVersionId(701L);
    itemMapper.updateById(item);

    CourseItem stored = itemMapper.selectById(item.getCourseItemId());
    assertThat(stored.getPaperVersionId()).isEqualTo(701L);
    assertThat(jdbcTemplate.queryForObject("""
                                             SELECT COUNT(*) FROM information_schema.columns
                                             WHERE table_schema = DATABASE() AND table_name = 'sts_course_item' AND column_name = 'exam_id'
                                             """, Long.class)).isZero();
  }

  @Test
  void questionBankReferenceIsUniquePerCourseItem() {
    CourseItem item = insertItem("QUESTION_BANK");
    CourseItemQuestionBank first = relation(item.getCourseItemId(), 801L);
    itemQuestionBankMapper.insert(first);

    assertThatThrownBy(() -> itemQuestionBankMapper.insert(relation(item.getCourseItemId(), 802L)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  private CourseItem insertItem(String itemType) {
    Long organizationId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Course course = CourseItFixtures.newCourse(organizationId, CourseItFixtures.uniqueCourseCode("ASSESS"));
    courseMapper.insert(course);
    CourseChapter chapter = CourseItFixtures.newChapter(course.getCourseId(), "理论考核");
    chapterMapper.insert(chapter);
    CourseItem item = new CourseItem();
    item.setCourseId(course.getCourseId());
    item.setChapterId(chapter.getChapterId());
    item.setItemType(itemType);
    item.setItemTitle("理论考核内容项");
    item.setIsRequired(1);
    item.setMinimumStudySeconds(0);
    item.setSortOrder(0);
    item.setIsLockedAfterParticipation(0);
    item.setStatus(1);
    itemMapper.insert(item);
    return item;
  }

  private CourseItemQuestionBank relation(Long courseItemId, Long questionBankId) {
    CourseItemQuestionBank relation = new CourseItemQuestionBank();
    relation.setCourseItemId(courseItemId);
    relation.setQuestionBankId(questionBankId);
    return relation;
  }
}
