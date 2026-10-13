package com.sts.admin.display.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DisplayOrganizationCacheRepositoryImpl implements DisplayOrganizationCacheRepository {

    private final OrganizationMapper organizationMapper;
    private final Map<String, Cache<Object, Object>> caffeineCacheMap;

    @Override
    public Map<Long, String> getDisplayNames(Set<Long> organizationIds, String l1CacheName) {
        if (organizationIds == null || organizationIds.isEmpty()) {
            return Map.of();
        }
        Cache<Object, Object> cache = caffeineCacheMap.get(l1CacheName);
        Map<Long, String> result = new HashMap<>();
        Set<Long> missed = new HashSet<>();

        if (cache != null) {
            Map<Object, Object> present = cache.getAllPresent(organizationIds);
            for (Long organizationId : organizationIds) {
                Object value = present.get(organizationId);
                if (value instanceof String name) {
                    result.put(organizationId, name);
                } else {
                    missed.add(organizationId);
                }
            }
        } else {
            missed.addAll(organizationIds);
        }

        if (!missed.isEmpty()) {
            // 租户主数据自身是租户插件豁免表，这里可以跨租户取到监管视角所需的机构名称。
            organizationMapper.selectList(new LambdaQueryWrapper<Organization>().in(Organization::getOrganizationId, missed))
                .forEach(organization -> {
                    result.put(organization.getOrganizationId(), organization.getOrgName());
                    if (cache != null) {
                        cache.put(organization.getOrganizationId(), organization.getOrgName());
                    }
                });
        }
        return result;
    }
}
