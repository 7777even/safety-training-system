package com.sts.training;
import com.sts.admin.support.*;
import com.sts.admin.tenant.TenantContext;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
/** 真库验证矩阵乐观锁、来源快照及租户隔离。 */
@SpringBootTest(classes=TrainingMatrixIT.Config.class) @Transactional
class TrainingMatrixIT extends AbstractMysqlIntegrationTest {
  @Configuration @Import(MapperItConfig.class) @MapperScan("com.sts.training.mapper") static class Config {}
  @Autowired TrainingMatrixMapper matrices;
  @Autowired TrainingMatrixSourceMapper sources;
  @Test void matrixVersionAndImmutableSourceAreIndependent() {
    var row = new TrainingMatrix(); row.setOrganizationId(TenantContext.requireTenantId()); row.setPositionId(1L);
    row.setPositionName("电工"); row.setCourseId(2L); row.setCourseVersionId(3L); row.setCourseName("电气安全");
    row.setContentName("岗位培训"); row.setRequiredHours(new BigDecimal("2")); row.setStatus(1); matrices.insert(row);
    row = matrices.selectById(row.getMatrixId());
    var stale = new TrainingMatrix();
    org.springframework.beans.BeanUtils.copyProperties(row, stale);
    var source = new TrainingMatrixSource(); source.setOrganizationId(TenantContext.requireTenantId()); source.setTrainingPlanId(99L);
    source.setSnapshotJson("[{\"requiredHours\":2,\"courseVersionId\":3}]"); sources.insert(source);
    row.setRequiredHours(new BigDecimal("4")); assertThat(matrices.updateById(row)).isEqualTo(1);
    stale.setRequiredHours(new BigDecimal("9")); assertThat(matrices.updateById(stale)).isZero();
    assertThat(sources.selectById(source.getSourceId()).getSnapshotJson()).contains("2").doesNotContain("9");
    Long tenant = TenantContext.requireTenantId();
    try { TenantContext.setTenantId(tenant+10000); assertThat(matrices.selectById(row.getMatrixId())).isNull(); assertThat(sources.selectById(source.getSourceId())).isNull(); }
    finally { TenantContext.setTenantId(tenant); }
  }
}
