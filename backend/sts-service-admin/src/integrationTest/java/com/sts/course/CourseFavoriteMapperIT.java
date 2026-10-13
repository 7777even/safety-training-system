package com.sts.course;

import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.CourseFavorite;
import com.sts.course.mapper.CourseFavoriteMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** 真SQL验证收藏取消后重加不产生重复关系，并保持租户隔离。 */
@SpringBootTest(classes = LearnerBatchMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseFavoriteMapperIT extends AbstractMysqlIntegrationTest {
  @Autowired CourseFavoriteMapper mapper;
  @Test void repeatedSetCancelAndReaddKeepsOneRow() {
    var row = new CourseFavorite(); row.setOrganizationId(TenantContext.requireTenantId()); row.setUserId(700L);
    row.setCourseId(20L); row.setCourseNameSnapshot("安全课程"); row.setIsFavorite(1);
    mapper.upsertFavorite(row); mapper.upsertFavorite(row); row.setIsFavorite(0); mapper.upsertFavorite(row);
    row.setIsFavorite(1); mapper.upsertFavorite(row);
    var records = mapper.selectList(new LambdaQueryWrapper<CourseFavorite>().eq(CourseFavorite::getUserId, 700L));
    assertThat(records).hasSize(1); assertThat(records.getFirst().getIsFavorite()).isEqualTo(1);
    assertThat(TenantContext.callWithTenant(999999L, () -> mapper.selectById(records.getFirst().getCourseFavoriteId()))).isNull();
  }
}
