package com.sts.admin.display;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sts.admin.display.cache.DisplayOrganizationCacheRepository;
import com.sts.admin.display.cache.DisplayOrganizationCacheRepositoryImpl;
import com.sts.admin.display.feeder.OrganizationTranslationFeeder;
import com.sts.common.cache.CacheDomain;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 机构名称展示翻译链路单元测试。
 * <p>
 * 覆盖「机构 ID → 机构名称」翻译器类型与批量装载，以及底层缓存的命中/回源行为；
 * 该链路是「所属机构」等列展示机构名称而不是主键的唯一数据来源。
 */
class OrganizationTranslationDisplayTest {

  private static Organization organization(long id, String name) {
    Organization organization = new Organization();
    organization.setOrganizationId(id);
    organization.setOrgCode("ORG" + id);
    organization.setOrgName(name);
    organization.setOrgType("ENTERPRISE");
    organization.setStatus(1);
    return organization;
  }

  @Test
  @DisplayName("ORG_SERVICE 翻译器按机构 ID 批量回填机构名称")
  void feederMapsOrganizationIdsToNames() {
    DisplayOrganizationCacheRepository repository = mock(DisplayOrganizationCacheRepository.class);
    when(repository.getDisplayNames(any(), any()))
      .thenReturn(Map.of(2L, "示例企业", 3L, "示例培训机构"));

    OrganizationTranslationFeeder feeder = new OrganizationTranslationFeeder(repository);

    assertThat(feeder.getType()).isEqualTo("ORG_SERVICE");
    assertThat(feeder.batchLoad(Set.of(2, 3)))
      .containsEntry(2L, "示例企业")
      .containsEntry(3L, "示例培训机构");
  }

  @Test
  @DisplayName("ORG_SERVICE 翻译器空入参不发查询")
  void feederSkipsEmptyIds() {
    DisplayOrganizationCacheRepository repository = mock(DisplayOrganizationCacheRepository.class);
    OrganizationTranslationFeeder feeder = new OrganizationTranslationFeeder(repository);

    assertThat(feeder.batchLoad(Set.of())).isEmpty();
    verify(repository, never()).getDisplayNames(any(), any());
  }

  @Test
  @DisplayName("机构展示名缓存未命中时回源一次，命中后不再查库")
  void cacheRepositoryLoadsOnceThenHitsCache() {
    OrganizationMapper organizationMapper = mock(OrganizationMapper.class);
    when(organizationMapper.selectList(any(LambdaQueryWrapper.class)))
      .thenReturn(List.of(organization(2L, "示例企业")));

    Map<String, Cache<Object, Object>> cacheMap = new HashMap<>();
    String l1CacheName = CacheDomain.ORGANIZATION.caffeineCacheName();
    cacheMap.put(l1CacheName, Caffeine.newBuilder().maximumSize(64).build());

    DisplayOrganizationCacheRepositoryImpl repository =
      new DisplayOrganizationCacheRepositoryImpl(organizationMapper, cacheMap);

    assertThat(repository.getDisplayNames(Set.of(2L), l1CacheName)).containsEntry(2L, "示例企业");
    assertThat(repository.getDisplayNames(Set.of(2L), l1CacheName)).containsEntry(2L, "示例企业");
    verify(organizationMapper).selectList(any(LambdaQueryWrapper.class));
  }

  @Test
  @DisplayName("缺少该缓存域时仍能回源，不抛异常")
  void cacheRepositoryFallsBackWhenCacheMissing() {
    OrganizationMapper organizationMapper = mock(OrganizationMapper.class);
    when(organizationMapper.selectList(any(LambdaQueryWrapper.class)))
      .thenReturn(List.of(organization(3L, "示例培训机构")));

    DisplayOrganizationCacheRepositoryImpl repository =
      new DisplayOrganizationCacheRepositoryImpl(organizationMapper, Map.of());

    assertThat(repository.getDisplayNames(Set.of(3L), CacheDomain.ORGANIZATION.caffeineCacheName()))
      .containsEntry(3L, "示例培训机构");
  }
}
