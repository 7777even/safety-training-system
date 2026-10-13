package com.sts.archive.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.archive.entity.ArchiveTraining;
import com.sts.archive.entity.ArchiveVersion;
import com.sts.archive.support.ArchiveMapperItConfig;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ArchiveMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ArchiveMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private ArchiveTrainingMapper archiveTrainingMapper;
  @Autowired
  private ArchiveVersionMapper archiveVersionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_archive_version 唯一")
  void archiveVersionUnique() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    ArchiveTraining archive = new ArchiveTraining();
    archive.setOrganizationId(orgId);
    archive.setArchiveCode("IT-ARCH-1");
    archive.setTrainingPlanId(999L);
    archive.setCurrentVersionNo(0);
    archive.setArchiveStatus("DRAFT");
    archive.setStatus(1);
    archiveTrainingMapper.insert(archive);

    ArchiveVersion v1 = version(archive.getTrainingArchiveId(), 1);
    archiveVersionMapper.insert(v1);

    ArchiveVersion dup = version(archive.getTrainingArchiveId(), 1);
    assertThatThrownBy(() -> archiveVersionMapper.insert(dup)).isInstanceOf(DuplicateKeyException.class);
  }

  private ArchiveVersion version(Long archiveId, int no) {
    ArchiveVersion v = new ArchiveVersion();
    v.setTrainingArchiveId(archiveId);
    v.setVersionNo(no);
    v.setPlanSnapshotJson("{}");
    v.setOrganizationSnapshotJson("{}");
    v.setSummarySnapshotJson("{}");
    v.setSnapshotHash("h");
    v.setGeneratedTime(LocalDateTime.now());
    v.setGeneratedBy(0L);
    v.setVersionReason("IT");
    return v;
  }
}
