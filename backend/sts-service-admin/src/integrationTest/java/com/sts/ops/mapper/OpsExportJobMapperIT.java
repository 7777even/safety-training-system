package com.sts.ops.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.ops.entity.OpsExportJob;
import com.sts.ops.support.OpsMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OpsExportJobMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OpsExportJobMapper exportJobMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("insert：导出任务落库")
  void insert_persistsJob() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    OpsExportJob job = new OpsExportJob();
    job.setOrganizationId(orgId);
    job.setExportDomain("COURSES");
    job.setExportFormat("CSV");
    job.setRequestJson("{\"filter\":{}}");
    job.setJobStatus("SUCCEEDED");
    job.setRequestedBy(1L);
    exportJobMapper.insert(job);
    assertThat(job.getExportJobId()).isNotNull();
  }
}
