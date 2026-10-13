package com.sts.asset.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.asset.entity.Asset;
import com.sts.asset.support.AssetMapperItConfig;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AssetMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_org_asset_code_deleted(organization_id, asset_code, deleted)}：
 * 同机构同素材编码只能有一条在线记录；逻辑删除后可重新插入。
 */
@SpringBootTest(classes = AssetMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class AssetMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private AssetMapper assetMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("insert + selectById：真库往返，自增主键回填")
  void insertThenSelectById_roundTrips() {
    Long orgId = defaultOrganizationId();

    Asset asset = asset(orgId, "ASSET-IT-RT");
    assetMapper.insert(asset);

    assertThat(asset.getAssetId()).isNotNull();
    Asset found = assetMapper.selectById(asset.getAssetId());
    assertThat(found).isNotNull();
    assertThat(found.getAssetCode()).isEqualTo("ASSET-IT-RT");
    assertThat(found.getCreateTime()).isNotNull();
    assertThat(found.getDeleted()).isEqualTo(0L);
    assertThat(found.getVersion()).isZero();
  }

  @Test
  @DisplayName("updateById 乐观锁：过期版本的并发更新影响 0 行")
  void updateById_staleVersionAffectsZeroRows() {
    Long orgId = defaultOrganizationId();
    Asset asset = asset(orgId, "ASSET-IT-LOCK");
    assetMapper.insert(asset);
    Long id = asset.getAssetId();

    Asset writerA = assetMapper.selectById(id);
    assertThat(writerA.getVersion()).isZero();
    writerA.setAssetName("A-first");
    assertThat(assetMapper.updateById(writerA)).isEqualTo(1);

    Asset writerB = staleCopy(id, "B-stale");
    assertThat(assetMapper.updateById(writerB)).isZero();

    Asset reloaded = assetMapper.selectById(id);
    assertThat(reloaded.getAssetName()).isEqualTo("A-first");
    assertThat(reloaded.getVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("updateByIdWithVersionCheck：过期版本命中 0 行时抛 HTTP 409 / code 2040")
  void updateByIdWithVersionCheck_onStale_throws409() {
    Long orgId = defaultOrganizationId();
    Asset asset = asset(orgId, "ASSET-IT-409");
    assetMapper.insert(asset);
    Long id = asset.getAssetId();

    Asset writerA = assetMapper.selectById(id);
    writerA.setAssetName("A-win");
    assetMapper.updateByIdWithVersionCheck(writerA);

    Asset writerB = staleCopy(id, "B-lose");
    assertThatThrownBy(() -> assetMapper.updateByIdWithVersionCheck(writerB))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> {
        BusinessException be = (BusinessException) ex;
        assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(be.getCode()).isEqualTo(2040);
      });
  }

  @Test
  @DisplayName("uk_org_asset_code_deleted：同机构同素材编码第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateAssetCode() {
    Long orgId = defaultOrganizationId();

    assetMapper.insert(asset(orgId, "ASSET-IT-001"));

    assertThatThrownBy(() -> assetMapper.insert(asset(orgId, "ASSET-IT-001")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同素材编码可重新插入")
  void logicalDelete_allowsReinsertSameAssetCode() {
    Long orgId = defaultOrganizationId();

    Asset original = asset(orgId, "ASSET-IT-002");
    assetMapper.insert(original);

    int deletedRows = assetMapper.update(null,
                                         Wrappers.<Asset>lambdaUpdate()
                                           .setSql("deleted = asset_id")
                                           .eq(Asset::getAssetId, original.getAssetId()));
    assertThat(deletedRows).isEqualTo(1);

    Asset reinserted = asset(orgId, "ASSET-IT-002");
    assetMapper.insert(reinserted);

    Asset found = assetMapper.selectOne(
      new LambdaQueryWrapper<Asset>()
        .eq(Asset::getOrganizationId, orgId)
        .eq(Asset::getAssetCode, "ASSET-IT-002")
        .eq(Asset::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getAssetId()).isEqualTo(reinserted.getAssetId());
  }

  private Long defaultOrganizationId() {
    Long orgId = jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'DEMO' AND deleted = 0",
      Long.class);
    assertThat(orgId).isNotNull();
    return orgId;
  }

  private static Asset staleCopy(Long assetId, String assetName) {
    Asset asset = new Asset();
    asset.setAssetId(assetId);
    asset.setAssetName(assetName);
    asset.setVersion(0);
    return asset;
  }

  private static Asset asset(Long orgId, String assetCode) {
    Asset asset = new Asset();
    asset.setOrganizationId(orgId);
    asset.setAssetCode(assetCode);
    asset.setAssetName("安全手册");
    asset.setAssetType("DOCUMENT");
    asset.setSourceType("UPLOAD");
    asset.setProcessStatus("SUCCEEDED");
    asset.setStatus(1);
    return asset;
  }
}
