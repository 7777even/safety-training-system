package com.sts.course;

import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.TeacherFollow;
import com.sts.course.mapper.TeacherFollowMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** 真SQL校验同机构教师去重、失效和关注关系重加。 */
@SpringBootTest(classes = LearnerBatchMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TeacherFollowMapperIT extends AbstractMysqlIntegrationTest {
  @Autowired TeacherFollowMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Test void directoryUsesActiveSameTenantTeacherAndFollowCanBeReadded() {
    long tenant = TenantContext.requireTenantId();
    jdbc.update("INSERT INTO us_sys_user (user_id,organization_id,user_code,user_name) VALUES (987001,?, 'teacher-it', '测试讲师')", tenant);
    jdbc.update("INSERT INTO us_sys_role (role_id,organization_id,role_name,role_code,role_level) VALUES (987002,?, '测试教师', 'teacher-it-role', 'TEACHER'),(987003,?, '第二教师角色', 'teacher-it-role2', 'TEACHER')", tenant, tenant);
    jdbc.update("INSERT INTO us_sys_user_role (user_id,role_id) VALUES (987001,987002),(987001,987003)");
    assertThat(mapper.selectTeachers("测试讲师", null)).hasSize(1);
    assertThat(TenantContext.callWithTenant(999999L, () -> mapper.selectTeachers(null, 987001L))).isEmpty();
    var row = new TeacherFollow(); row.setOrganizationId(tenant); row.setUserId(700L);
    row.setTeacherId(987001L); row.setTeacherNameSnapshot("测试讲师"); row.setIsFollowed(1);
    mapper.upsertFollow(row); mapper.upsertFollow(row); row.setIsFollowed(0); mapper.upsertFollow(row);
    row.setIsFollowed(1); mapper.upsertFollow(row);
    var records = mapper.selectList(new LambdaQueryWrapper<TeacherFollow>().eq(TeacherFollow::getUserId,700L));
    assertThat(records).hasSize(1); assertThat(records.getFirst().getIsFollowed()).isEqualTo(1);
    jdbc.update("UPDATE us_sys_user SET status=0 WHERE user_id=987001");
    assertThat(mapper.selectTeachers(null,987001L)).isEmpty();
  }
}
