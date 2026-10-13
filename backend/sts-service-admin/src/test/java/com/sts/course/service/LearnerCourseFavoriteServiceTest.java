package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.CourseFavorite;
import com.sts.course.mapper.CourseFavoriteMapper;
import com.sts.course.mapper.CourseMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** 本人课程收藏入口验证失效资源仍能取消、非法课程不可读取。 */
class LearnerCourseFavoriteServiceTest {
  @Test void removedEnrollmentResourceKeepsUnavailableFavoriteVisible() {
    var mapper = mock(CourseFavoriteMapper.class);
    var learning = mock(CourseLearningService.class);
    var enrollments = mock(com.sts.course.mapper.CourseEnrollmentMapper.class);
    var relation = new CourseFavorite(); relation.setCourseId(3L); relation.setCourseNameSnapshot("原课程"); relation.setIsFavorite(1);
    when(mapper.selectList(any())).thenReturn(java.util.List.of(relation));
    var enrollment = new com.sts.course.entity.CourseEnrollment(); enrollment.setCourseEnrollmentId(4L);
    when(enrollments.selectList(any())).thenReturn(java.util.List.of(enrollment));
    when(learning.getMyEnrollmentDetail(4L)).thenThrow(new com.sts.common.exception.BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, 5011, "课程不存在"));
    when(learning.pageLearnerOfferings(any())).thenReturn(new com.sts.common.api.PageResult<>(java.util.List.of(),0));
    var service = new LearnerCourseFavoriteService(mapper,learning,enrollments,null,null,null,null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      var result = service.favorites(new com.sts.common.api.PageRequest<>(1,20,null));
      assertThat(result.list()).hasSize(1); assertThat(result.list().getFirst().available()).isFalse();
      assertThat(result.list().getFirst().introduction()).isNull();
    }
  }
  @Test void allowsCancellingUnavailableCourseWithoutEnrollment() {
    var mapper = mock(CourseFavoriteMapper.class); var state = new CourseFavorite();
    state.setCourseId(3L); state.setCourseNameSnapshot("原课程"); when(mapper.selectOne(any())).thenReturn(state);
    var service = new LearnerCourseFavoriteService(mapper, null, null, null, null, null, null);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try { service.setFavorite(3L, false); verify(mapper).upsertFavorite(argThat(row -> row.getIsFavorite() == 0 && row.getUserId() == 7L)); }
      finally { TenantContext.clear(); }
    }
  }
  @Test void inaccessibleCourseHasNoDetails() {
    var courseMapper = mock(CourseMapper.class);
    var service = new LearnerCourseFavoriteService(null, null, null, courseMapper, null, null, null);
    assertThatThrownBy(() -> service.detail(3L)).hasMessageContaining("无权");
  }
}
