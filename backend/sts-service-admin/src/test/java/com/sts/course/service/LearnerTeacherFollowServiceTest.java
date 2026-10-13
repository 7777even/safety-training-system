package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.TeacherFollow;
import com.sts.course.mapper.TeacherFollowMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** 教师失效后可取消，非有效同机构教师不能被新关注。 */
class LearnerTeacherFollowServiceTest {
  @Test void inactiveTeacherCanBeUnfollowedButCannotBeFollowed() {
    var mapper = mock(TeacherFollowMapper.class); var relation = new TeacherFollow(); relation.setTeacherNameSnapshot("原教师");
    when(mapper.selectOne(any())).thenReturn(relation); when(mapper.selectTeachers(null, 5L)).thenReturn(List.of());
    var service = new LearnerTeacherFollowService(mapper);
    try (var stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L); TenantContext.setTenantId(2L);
      try {
        service.setFollow(5L, false);
        verify(mapper).upsertFollow(argThat(r -> r.getIsFollowed() == 0 && r.getUserId() == 7L));
        assertThatThrownBy(() -> service.setFollow(5L, true)).hasMessageContaining("无权");
      } finally { TenantContext.clear(); }
    }
  }
}
