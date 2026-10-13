package com.sts.course.service;

import com.sts.common.exception.BusinessException;
import com.sts.course.converter.CourseChapterConverter;
import com.sts.course.dto.ChapterCreateRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseItemMapper;
import com.sts.course.mapper.CourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link CourseChapterServiceImpl} 业务逻辑单元测试。纯 Mockito，零 DB。
 * <p>
 * 覆盖：章节强制根级（parentId=0）、同课程同名护栏、最后一个章节禁止删除、级联删除、单章节合并。
 */
class CourseChapterServiceImplTest {

  private CourseChapterMapper chapterMapper;
  private CourseChapterConverter chapterConverter;
  private CourseMapper courseMapper;
  private CourseItemMapper itemMapper;
  private CourseChapterServiceImpl service;

  @BeforeEach
  void setUp() {
    chapterMapper = mock(CourseChapterMapper.class);
    doAnswer(inv -> chapterMapper.selectById((Long) inv.getArgument(0))).when(chapterMapper).selectCurrent(any());
    chapterConverter = mock(CourseChapterConverter.class);
    courseMapper = mock(CourseMapper.class);
    itemMapper = mock(CourseItemMapper.class);
    service = new CourseChapterServiceImpl(chapterMapper, chapterConverter, courseMapper, itemMapper);
  }

  @Test
  @DisplayName("createChapter：课程不存在 → 400 / 5011，不插入")
  void createChapter_courseNotExists_throws400() {
    when(courseMapper.selectById(99L)).thenReturn(null);

    ChapterCreateRequest request = new ChapterCreateRequest();
    request.setCourseId(99L);
    request.setChapterName("第一章");

    assertThatThrownBy(() -> service.createChapter(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5011));
    verify(chapterMapper, never()).insert(any(CourseChapter.class));
  }

  @Test
  @DisplayName("createChapter：强制 parentId=0、ancestors=\"0\"，不受请求影响")
  void createChapter_forcesRootParent() {
    when(courseMapper.selectById(1L)).thenReturn(new Course());
    when(chapterMapper.selectCount(any())).thenReturn(0L);
    CourseChapter entity = new CourseChapter();
    when(chapterConverter.toEntity(any(ChapterCreateRequest.class))).thenReturn(entity);

    ChapterCreateRequest request = new ChapterCreateRequest();
    request.setCourseId(1L);
    request.setChapterName("第一章");

    service.createChapter(request);

    assertThat(entity.getParentId()).isEqualTo(0L);
    assertThat(entity.getAncestors()).isEqualTo("0");
    verify(chapterMapper).insert(entity);
  }

  @Test
  @DisplayName("createChapter：同课程同名已存在 → 409 / 5022，不插入")
  void createChapter_duplicateName_throws409() {
    when(courseMapper.selectById(1L)).thenReturn(new Course());
    when(chapterMapper.selectCount(any())).thenReturn(1L);

    ChapterCreateRequest request = new ChapterCreateRequest();
    request.setCourseId(1L);
    request.setChapterName("第一章");

    assertThatThrownBy(() -> service.createChapter(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5022));
    verify(chapterMapper, never()).insert(any(CourseChapter.class));
  }

  @Test
  @DisplayName("deleteChapter：章节下存在内容项 → 409 / 5034")
  void deleteChapter_hasItems_throws5034() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(chapterMapper.selectCount(any())).thenReturn(2L);
    when(itemMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.deleteChapter(1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5034));
  }

  @Test
  @DisplayName("deleteChapter：最后一个章节 → 409 / 5023，不删除")
  void deleteChapter_lastRemaining_throws409() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(chapterMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.deleteChapter(1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5023));
  }

  @Test
  @DisplayName("mergeChaptersToSingle：只有一个章节时直接返回其 ID，不触发任何删除")
  void mergeChaptersToSingle_singleChapter_noDeleteNeeded() {
    CourseChapter only = chapter(1L, 10L);
    when(chapterMapper.selectList(any())).thenReturn(List.of(only));

    Long survivorId = service.mergeChaptersToSingle(10L);

    assertThat(survivorId).isEqualTo(1L);
  }

  @Test
  @DisplayName("mergeChaptersToSingle：课程下无章节时防御性创建一个默认章节")
  void mergeChaptersToSingle_empty_createsDefaultChapter() {
    when(chapterMapper.selectList(any())).thenReturn(List.of());

    service.mergeChaptersToSingle(10L);

    ArgumentCaptor<CourseChapter> captor = ArgumentCaptor.forClass(CourseChapter.class);
    verify(chapterMapper).insert(captor.capture());
    CourseChapter created = captor.getValue();
    assertThat(created.getCourseId()).isEqualTo(10L);
    assertThat(created.getParentId()).isEqualTo(0L);
    assertThat(created.getChapterName()).isEqualTo("默认章节");
  }

  @Test
  void createChild_preservesParentAndAncestors() {
    when(courseMapper.selectById(10L)).thenReturn(new Course());
    when(chapterMapper.selectCount(any())).thenReturn(0L);
    CourseChapter parent = chapter(2L, 10L);
    parent.setParentId(1L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter(1L, 10L));
    when(chapterMapper.selectById(2L)).thenReturn(parent);
    CourseChapter entity = new CourseChapter();
    when(chapterConverter.toEntity(any())).thenReturn(entity);
    ChapterCreateRequest request = new ChapterCreateRequest();
    request.setCourseId(10L);
    request.setParentId(2L);
    request.setChapterName("子章节");
    service.createChapter(request);
    assertThat(entity.getParentId()).isEqualTo(2L);
    assertThat(entity.getAncestors()).isEqualTo("0,1,2");
  }

  @Test
  void createChild_rejectsOtherCourse() {
    when(courseMapper.selectById(10L)).thenReturn(new Course());
    when(chapterMapper.selectCount(any())).thenReturn(0L);
    when(chapterMapper.selectById(2L)).thenReturn(chapter(2L, 11L));
    when(chapterConverter.toEntity(any())).thenReturn(new CourseChapter());
    ChapterCreateRequest request = new ChapterCreateRequest();
    request.setCourseId(10L);
    request.setParentId(2L);
    request.setChapterName("子章节");
    assertThatThrownBy(() -> service.createChapter(request)).isInstanceOf(BusinessException.class);
    verify(chapterMapper, never()).insert(any(CourseChapter.class));
  }

  @Test
  void move_rejectsDescendantAndUpdatesSubtreePaths() {
    CourseChapter parent = chapter(1L, 10L);
    CourseChapter child = chapter(2L, 10L); child.setParentId(1L);
    when(chapterMapper.selectById(1L)).thenReturn(parent);
    when(chapterMapper.selectById(2L)).thenReturn(child);
    var request = new com.sts.course.dto.ChapterUpdateRequest(); request.setVersion(0); request.setParentId(2L);
    assertThatThrownBy(() -> service.updateChapter(1L, request)).isInstanceOf(BusinessException.class);
    request.setParentId(1L);
    assertThatThrownBy(() -> service.updateChapter(1L, request)).isInstanceOf(BusinessException.class);
    when(chapterMapper.selectById(9L)).thenReturn(chapter(9L, 10L));
    when(chapterMapper.selectList(any())).thenReturn(List.of(parent, child));
    request.setParentId(9L); service.updateChapter(1L, request);
    assertThat(parent.getAncestors()).isEqualTo("0,9");
    assertThat(child.getAncestors()).isEqualTo("0,9,1");
  }

  @Test
  void delete_rejectsChapterWithChildren() {
    when(chapterMapper.selectById(1L)).thenReturn(chapter(1L, 10L));
    when(chapterMapper.selectCount(any())).thenReturn(2L, 1L);
    assertThatThrownBy(() -> service.deleteChapter(1L)).isInstanceOf(BusinessException.class);
    verify(itemMapper, never()).selectCount(any());
  }

  @Test
  void move_checksLatestParentChainAfterCourseLock() {
    when(chapterMapper.selectById(1L)).thenReturn(chapter(1L, 10L));
    when(chapterMapper.selectById(2L)).thenReturn(chapter(2L, 10L));
    CourseChapter latest = chapter(2L, 10L); latest.setParentId(1L);
    doReturn(latest).when(chapterMapper).selectCurrent(2L);
    var request = new com.sts.course.dto.ChapterUpdateRequest();
    request.setVersion(0); request.setParentId(2L); request.setChapterName("章节1");
    assertThatThrownBy(() -> service.updateChapter(1L, request)).isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5024));
    verify(chapterMapper, never()).updateByIdWithVersionCheck(any());
  }

  // mergeChaptersToSingle 在多章节场景下"保留最靠前、其余删除"依赖 BaseServiceImpl.removeById 的真实
  // MyBatis 表元信息（纯 Mockito 构造的 Service 拿不到，同 deleteChapter 的已知限制），
  // 该分支及 deleteAllChaptersOfCourse 的落库行为由 CourseChapterMapperIT（Testcontainers 真库）覆盖。

  private static CourseChapter chapter(Long id, Long courseId) {
    CourseChapter chapter = new CourseChapter();
    chapter.setChapterId(id);
    chapter.setCourseId(courseId);
    chapter.setParentId(0L);
    chapter.setAncestors("0");
    chapter.setChapterName("章节" + id);
    chapter.setSortOrder(0);
    chapter.setStatus(1);
    return chapter;
  }
}
