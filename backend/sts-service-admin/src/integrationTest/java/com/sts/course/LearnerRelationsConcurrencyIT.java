package com.sts.course;

import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.mapper.*;
import com.sts.course.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 两条并发连接重复收藏/关注，真实唯一键必须仅留下一个关系。 */
@SpringBootTest(classes = LearnerBatchMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LearnerRelationsConcurrencyIT extends AbstractMysqlIntegrationTest {
  @Autowired CourseFavoriteMapper courses;
  @Autowired TeacherFollowMapper teachers;
  @Autowired JdbcTemplate jdbc;
  @Test void concurrentRepeatedRelationsKeepOneRow() throws Exception {
    long tenant = TenantContext.requireTenantId();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1); var ready = new CountDownLatch(2);
      Callable<Void> operation = () -> {
        TenantContext.setTenantId(tenant);
        try {
          ready.countDown(); start.await();
          var course = new CourseFavorite(); course.setOrganizationId(tenant); course.setUserId(987010L);
          course.setCourseId(20L); course.setCourseNameSnapshot("并发课程"); course.setIsFavorite(1); courses.upsertFavorite(course);
          var teacher = new TeacherFollow(); teacher.setOrganizationId(tenant); teacher.setUserId(987010L);
          teacher.setTeacherId(30L); teacher.setTeacherNameSnapshot("并发教师"); teacher.setIsFollowed(1); teachers.upsertFollow(teacher);
          return null;
        } finally { TenantContext.clear(); }
      };
      var first = executor.submit(operation); var second = executor.submit(operation);
      boolean bothReady = ready.await(10,TimeUnit.SECONDS); start.countDown();
      assertThat(bothReady).isTrue();
      first.get(20,TimeUnit.SECONDS); second.get(20,TimeUnit.SECONDS);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sts_course_favorite WHERE organization_id=? AND user_id=987010",Integer.class,tenant)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sts_teacher_follow WHERE organization_id=? AND user_id=987010",Integer.class,tenant)).isEqualTo(1);
    } finally {
      jdbc.update("DELETE FROM sts_course_favorite WHERE organization_id=? AND user_id=987010",tenant);
      jdbc.update("DELETE FROM sts_teacher_follow WHERE organization_id=? AND user_id=987010",tenant);
    }
  }
}
