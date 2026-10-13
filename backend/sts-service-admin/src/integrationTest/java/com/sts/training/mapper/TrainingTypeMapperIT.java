package com.sts.training.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.TrainingType;
import com.sts.training.support.TrainingMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TrainingMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingTypeMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingTypeMapper trainingTypeMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("uk_org_code_deleted：逻辑删除后可复用编码")
  void uniqueKey_allowsReuseAfterLogicDelete() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    TrainingType first = type(orgId, "IT-TYPE-1");
    trainingTypeMapper.insert(first);
    Long id = first.getTrainingTypeId();

    jdbcTemplate.update("UPDATE sts_training_type SET deleted = ? WHERE training_type_id = ?", id, id);

    TrainingType second = type(orgId, "IT-TYPE-1");
    trainingTypeMapper.insert(second);
    assertThat(second.getTrainingTypeId()).isNotNull();
  }

  @Test
  @DisplayName("coverFileId 映射到 cover_file_id")
  void persist_mapsCoverFileIdToCoverFileIdColumn() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    TrainingType type = type(orgId, "IT-COVER-COL");
    type.setCoverFileId(9001L);
    trainingTypeMapper.insert(type);

    Long stored = jdbcTemplate.queryForObject(
      "SELECT cover_file_id FROM sts_training_type WHERE training_type_id = ?",
      Long.class,
      type.getTrainingTypeId());
    assertThat(stored).isEqualTo(9001L);

    Integer oldColumnCount = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
        + "AND table_name = 'sts_training_type' AND column_name = 'image_file_id'",
      Integer.class);
    assertThat(oldColumnCount).isZero();

    TrainingType loaded = trainingTypeMapper.selectById(type.getTrainingTypeId());
    assertThat(loaded.getCoverFileId()).isEqualTo(9001L);
  }

  @Test
  @DisplayName("uk_org_code_deleted：同键第二次插入冲突")
  void uniqueKey_rejectsDuplicate() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    trainingTypeMapper.insert(type(orgId, "IT-TYPE-2"));
    assertThatThrownBy(() -> trainingTypeMapper.insert(type(orgId, "IT-TYPE-2")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  private TrainingType type(Long orgId, String code) {
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode(code);
    type.setTypeName("类型-" + code);
    type.setStatus(1);
    type.setDescription("IT");
    return type;
  }
}
