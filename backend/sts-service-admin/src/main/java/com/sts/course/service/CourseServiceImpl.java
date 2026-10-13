package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.converter.CourseConverter;
import com.sts.course.dto.*;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.course.entity.CourseCourseCategory;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.*;
import com.sts.course.vo.CourseListItemVO;
import com.sts.course.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl extends BaseServiceImpl<CourseMapper, Course> implements CourseService {

  private static final String ROOT_ANCESTORS = "0";
  private static final String DEFAULT_CHAPTER_NAME = "默认章节";
  private static final String STATUS_DRAFT = "DRAFT";
  private static final Set<String> ACTIVE_OFFERING_STATUSES = Set.of("DRAFT", "PUBLISHED");

  private final CourseMapper courseMapper;
  private final CourseConverter courseConverter;
  private final CourseChapterMapper chapterMapper;
  private final CourseChapterService chapterService;
  private final CourseItemService itemService;
  private final CourseDownloadAssetService downloadAssetService;
  private final CourseCourseCategoryMapper courseCategoryRelMapper;
  private final CourseCategoryMapper categoryMapper;
  private final CourseOfferingMapper offeringMapper;
  private final CourseCoverService coverService;
  /** 复用同租户有效教师目录及资格。 */
  private final TeacherFollowMapper teacherMapper;

  @Override
  public PageResult<CourseListItemVO> pageCourses(PageRequest<CourseListQuery> request) {
    CourseListQuery query = request.getQuery();
    // 租户条件由租户插件注入
    LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.and(StringUtils.hasText(query.getCourseName()), w -> w.like(Course::getCourseName, query.getCourseName())
          .or().like(Course::getSyllabus, query.getCourseName()))
        .eq(query.getTeacherUserId() != null, Course::getTeacherUserId, query.getTeacherUserId())
        .eq(StringUtils.hasText(query.getPublishStatus()), Course::getPublishStatus, query.getPublishStatus())
        .eq(query.getStatus() != null, Course::getStatus, query.getStatus());
      if (query.getCategoryId() != null) {
        List<Long> courseIds = courseCategoryRelMapper.selectList(
            new LambdaQueryWrapper<CourseCourseCategory>()
              .eq(CourseCourseCategory::getCourseCategoryId, query.getCategoryId()))
          .stream().map(CourseCourseCategory::getCourseId).toList();
        if (courseIds.isEmpty()) {
          return new PageResult<>(List.of(), 0);
        }
        wrapper.in(Course::getCourseId, courseIds);
      }
    }
    wrapper.orderByDesc(Course::getCreateTime);
    Page<Course> page = courseMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    List<CourseListItemVO> list = page.getRecords().stream().map(courseConverter::toListItem).toList();
    return new PageResult<>(list, page.getTotal());
  }

  @Override
  public CourseVO getCourse(Long courseId) {
    Course course = requireCourseForDetail(courseId);
    CourseVO vo = courseConverter.toVO(course);
    vo.setCategoryIds(listCategoryIds(courseId));
    if (course.getTeacherUserId() != null) vo.setTeacherName(teacherMapper.selectTeachers(null, course.getTeacherUserId()).stream()
      .map(com.sts.course.vo.TeacherPublicVO::getTeacherName).findFirst().orElse(null));
    return vo;
  }

  @Override
  @Transactional
  public Long createCourse(CourseCreateRequest request) {

    validateTeacher(request.getTeacherUserId());
    validateCoverReference(request.getCoverFileId());

    Course course = courseConverter.toEntity(request);
    course.setPublishStatus(STATUS_DRAFT);
    try {
      courseMapper.insert(course);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 5012, "课程编码已存在");
    }

    CourseChapter defaultChapter = new CourseChapter();
    defaultChapter.setCourseId(course.getCourseId());
    defaultChapter.setParentId(0L);
    defaultChapter.setAncestors(ROOT_ANCESTORS);
    defaultChapter.setChapterName(DEFAULT_CHAPTER_NAME);
    defaultChapter.setSortOrder(0);
    defaultChapter.setStatus(1);
    chapterMapper.insert(defaultChapter);

    if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
      replaceCategories(course.getCourseId(), request.getCategoryIds());
    }
    return course.getCourseId();
  }

  @Override
  @Transactional
  public void updateCourse(Long courseId, CourseUpdateRequest request) {
    Course course = requireCourse(courseId);
    validateTeacher(request.getTeacherUserId());
    validateCoverReference(request.getCoverFileId());
    course.setVersion(request.getVersion());
    courseConverter.updateEntity(course, request);
    courseMapper.updateByIdWithVersionCheck(course);
  }

  @Override
  @Transactional
  public void deleteCourse(Long courseId) {
    requireCourse(courseId);
    itemService.deleteAllOfCourse(courseId);
    downloadAssetService.deleteAllOfCourse(courseId);
    chapterService.deleteAllChaptersOfCourse(courseId);
    courseCategoryRelMapper.delete(
      new LambdaQueryWrapper<CourseCourseCategory>().eq(CourseCourseCategory::getCourseId, courseId));
    this.removeById(courseId);
  }

  @Override
  @Transactional
  public void switchContentMode(Long courseId, ContentModeSwitchRequest request) {
    Course course = requireCourse(courseId);
    if (Objects.equals(course.getContentMode(), request.getContentMode())) {
      // 目标模式与当前一致：幂等处理，只校验版本号是否为最新，不做任何结构变更。
      course.setVersion(request.getVersion());
      course.setContentMode(request.getContentMode());
      courseMapper.updateByIdWithVersionCheck(course);
      return;
    }
    assertNoActiveOffering(courseId);
    if ("SINGLE".equals(request.getContentMode())) {
      // 合并章节：保留排序最靠前的一个，其余逻辑删除。内容项的搬迁待 Task 3（sts_course_item 建表后）在本方法内补充。
      chapterService.mergeChaptersToSingle(courseId);
    }
    course.setVersion(request.getVersion());
    course.setContentMode(request.getContentMode());
    courseMapper.updateByIdWithVersionCheck(course);
  }

  @Override
  @Transactional
  public void assignCategories(Long courseId, CategoryAssignRequest request) {
    requireCourse(courseId);
    replaceCategories(courseId, request.getCategoryIds());
  }

  /** 沿用教师目录真实角色及租户校验，前端选择不能代替授权。 */
  private void validateTeacher(Long teacherId) {
    if (teacherId != null && teacherMapper.selectTeachers(null, teacherId).isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5014, "主讲教师必须是本机构启用教师");
    }
  }

  private void replaceCategories(Long courseId, List<Long> categoryIds) {
    Set<Long> distinctIds = categoryIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (!distinctIds.isEmpty()) {
      Long matchedCount = categoryMapper.selectCount(
        new LambdaQueryWrapper<com.sts.course.entity.CourseCategory>()
          .in(com.sts.course.entity.CourseCategory::getCourseCategoryId, distinctIds));
      if (matchedCount == null || matchedCount != distinctIds.size()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5013, "存在不属于当前机构的课程分类");
      }
    }
    courseCategoryRelMapper.delete(
      new LambdaQueryWrapper<CourseCourseCategory>().eq(CourseCourseCategory::getCourseId, courseId));
    for (Long categoryId : distinctIds) {
      CourseCourseCategory rel = new CourseCourseCategory();
      rel.setCourseId(courseId);
      rel.setCourseCategoryId(categoryId);
      courseCategoryRelMapper.insert(rel);
    }
  }

  private List<Long> listCategoryIds(Long courseId) {
    return courseCategoryRelMapper.selectList(
        new LambdaQueryWrapper<CourseCourseCategory>().eq(CourseCourseCategory::getCourseId, courseId))
      .stream().map(CourseCourseCategory::getCourseCategoryId).toList();
  }

  private Course requireCourse(Long courseId) {
    Course course = courseMapper.selectOne(
      new LambdaQueryWrapper<Course>()
        .eq(Course::getCourseId, courseId));
    if (course == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5011, "课程不存在");
    }
    return course;
  }

  /**
   * 课程详情只允许当前机构读取，其他机构资源按不存在处理。
   */
  private Course requireCourseForDetail(Long courseId) {
    Long callerOrganizationId = TenantContext.requireTenantId();
    Course course = courseMapper.selectById(courseId);
    if (course == null || !Objects.equals(course.getOrganizationId(), callerOrganizationId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5011, "课程不存在");
    }
    return course;
  }

  private void assertNoActiveOffering(Long courseId) {
    Long count = offeringMapper.selectCount(
      new LambdaQueryWrapper<CourseOffering>()
        .eq(CourseOffering::getCourseId, courseId)
        .in(CourseOffering::getPublishStatus, ACTIVE_OFFERING_STATUSES));
    if (count != null && count > 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5065, "存在有效开课，不能切换内容模式");
    }
  }

  private void validateCoverReference(Long coverFileId) {
    if (coverFileId != null) {
      coverService.validateReference(coverFileId);
    }
  }
}
