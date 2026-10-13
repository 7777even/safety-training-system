package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.LearnerCourseOfferingQuery;
import com.sts.course.entity.*;
import com.sts.course.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** 课程收藏读取既有学员可访问范围，不因收藏创建报名或开放课程内容。 */
@Service @RequiredArgsConstructor
public class LearnerCourseFavoriteService {
  /** 本人课程收藏关系持久化。 */
  private final CourseFavoriteMapper favorites;
  /** 原学员报名及选课资格查询。 */
  private final CourseLearningService learning;
  /** 本人课程报名范围。 */
  private final CourseEnrollmentMapper enrollments;
  /** 课程当前有效状态。 */
  private final CourseMapper courseMapper;
  /** 既有开班与冻结版本引用。 */
  private final CourseOfferingMapper offerings;
  /** 课程发布版本。 */
  private final CourseVersionMapper versions;
  /** 课程发布快照解析。 */
  private final CourseSnapshotAssembler snapshots;

  /** 只读课程详情/列表，收藏状态来自本人关系。失效资源不含受保护详情。 */
  public record View(Long courseId, String courseName, String introduction, String teachingObjective,
                     Long enrollmentId, Long offeringId, boolean available, boolean favorite) { }

  /** 本人可访问课程目录，包含已报名课程和允许选课的课程。 */
  public PageResult<View> catalog(PageRequest<LearnerCourseOfferingQuery> request) {
    return page(new ArrayList<>(accessible().values()), request);
  }

  /** 本人收藏列表包含已失效课程，便于用户取消。 */
  public PageResult<View> favorites(PageRequest<LearnerCourseOfferingQuery> request) {
    Map<Long, View> available = accessible(); List<View> rows = new ArrayList<>();
    for (CourseFavorite row : favorites.selectList(new LambdaQueryWrapper<CourseFavorite>()
      .eq(CourseFavorite::getUserId, StpUtil.getLoginIdAsLong()).eq(CourseFavorite::getIsFavorite, 1)
      .orderByDesc(CourseFavorite::getUpdateTime))) {
      rows.add(available.getOrDefault(row.getCourseId(), new View(row.getCourseId(), row.getCourseNameSnapshot(),
        null, null, null, null, false, true)));
    }
    return page(rows, request);
  }

  /** 详情必须仍在本人课程访问范围，不能通过收藏绕过原权限。 */
  public View detail(Long courseId) {
    if (courseMapper.selectById(courseId) == null) throw denied();
    View value = accessible().get(courseId);
    if (value == null) throw denied(); return value;
  }

  /** 收藏按原访问规则校验，取消失效资源不需要重新取得课程内容。 */
  @Transactional
  public void setFavorite(Long courseId, boolean favorite) {
    CourseFavorite existing = favorites.selectOne(new LambdaQueryWrapper<CourseFavorite>()
      .eq(CourseFavorite::getUserId, StpUtil.getLoginIdAsLong()).eq(CourseFavorite::getCourseId, courseId));
    if (!favorite && existing == null) return;
    String title = favorite ? detail(courseId).courseName() : existing.getCourseNameSnapshot();
    CourseFavorite row = new CourseFavorite(); row.setOrganizationId(TenantContext.requireTenantId());
    row.setUserId(StpUtil.getLoginIdAsLong()); row.setCourseId(courseId); row.setIsFavorite(favorite ? 1 : 0);
    row.setCourseNameSnapshot(title); favorites.upsertFavorite(row);
  }

  /** 复用报名详情和选课大厅资格，合并本人可访问课程。 */
  private Map<Long, View> accessible() {
    Map<Long, CourseFavorite> states = favorites.selectList(new LambdaQueryWrapper<CourseFavorite>()
      .eq(CourseFavorite::getUserId, StpUtil.getLoginIdAsLong())).stream().collect(Collectors.toMap(CourseFavorite::getCourseId, r -> r));
    Map<Long, View> result = new LinkedHashMap<>();
    // ponytail: 复用报名详情和选课大厅的访问裁剪；大量报名时改为批量安全摘要。
    for (CourseEnrollment enrollment : enrollments.selectList(new LambdaQueryWrapper<CourseEnrollment>()
      .eq(CourseEnrollment::getUserId, StpUtil.getLoginIdAsLong())
      .in(CourseEnrollment::getLearningStatus, List.of("NOT_STARTED", "LEARNING", "COMPLETED"))
      .orderByDesc(CourseEnrollment::getCourseEnrollmentId))) {
      com.sts.course.vo.CourseEnrollmentDetailVO detail;
      try { detail = learning.getMyEnrollmentDetail(enrollment.getCourseEnrollmentId()); }
      catch (BusinessException unavailable) {
        // 来源撤销后保留收藏关系，列表以不可用资源显示并允许取消。
        if (Set.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST).contains(unavailable.getStatus())) continue;
        throw unavailable;
      }
      var course = detail.getCourse();
      Course current = courseMapper.selectById(course.getCourseId());
      if (current == null || !Objects.equals(current.getStatus(), 1)) continue;
      result.putIfAbsent(course.getCourseId(), new View(course.getCourseId(), course.getCourseName(), course.getIntroduction(),
        course.getTeachingObjective(), enrollment.getCourseEnrollmentId(), enrollment.getCourseOfferingId(), true, isFavorite(states, course.getCourseId())));
    }
    int page = 1;
    while (true) {
      var rows = learning.pageLearnerOfferings(new PageRequest<>(page++, 200, null));
      for (var row : rows.list()) {
        Course current = courseMapper.selectById(row.getCourseId());
        if (current == null || !Objects.equals(current.getStatus(), 1)) continue;
        CourseOffering offering = offerings.selectById(row.getOfferingId());
        CourseVersion version = offering == null ? null : versions.selectById(offering.getCourseVersionId());
        if (version == null) continue;
        var course = snapshots.parseContentSnapshot(version.getContentSnapshotJson()).getCourse();
        result.putIfAbsent(row.getCourseId(), new View(row.getCourseId(), row.getCourseName(), course.getIntroduction(),
          course.getTeachingObjective(), null, row.getOfferingId(), true, isFavorite(states, row.getCourseId())));
      }
      if ((long)(page - 1) * 200 >= rows.total()) break;
    }
    return result;
  }
  /** 读取本人当前课程收藏标志。 */
  private boolean isFavorite(Map<Long, CourseFavorite> states, Long id) {
    return states.containsKey(id) && Objects.equals(states.get(id).getIsFavorite(), 1);
  }
  /** 对已受控课程摘要进行名称筛选与分页。 */
  private PageResult<View> page(List<View> rows, PageRequest<LearnerCourseOfferingQuery> request) {
    String keyword = request.getQuery() == null ? null : request.getQuery().getKeyword();
    rows = rows.stream().filter(v -> keyword == null || keyword.isBlank() || v.courseName() != null && v.courseName().contains(keyword)).toList();
    int start = (int)Math.min((long)(request.getPageNum() - 1) * request.getPageSize(), rows.size());
    return new PageResult<>(rows.subList(start, Math.min(start + request.getPageSize(), rows.size())), rows.size());
  }
  /** 返回课程收藏资源访问拒绝。 */
  private BusinessException denied() { return new BusinessException(HttpStatus.FORBIDDEN, 5093, "无权访问该课程详情"); }
}
