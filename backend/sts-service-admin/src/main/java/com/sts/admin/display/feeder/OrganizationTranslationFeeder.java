package com.sts.admin.display.feeder;

import com.sts.admin.display.cache.DisplayOrganizationCacheRepository;
import com.sts.common.cache.CacheDomain;
import io.github.easytrans.core.spi.TranslationFeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrganizationTranslationFeeder implements TranslationFeeder {

    private static final String L1_CACHE_NAME = CacheDomain.ORGANIZATION.caffeineCacheName();
    private final DisplayOrganizationCacheRepository organizationCacheRepository;

    @Override
    public String getType() {
        return "ORG_SERVICE";
    }

    @Override
    public Map<Object, String> batchLoad(Set<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> organizationIds = ids.stream()
            .map(id -> ((Number) id).longValue())
            .collect(Collectors.toSet());
        return organizationCacheRepository.getDisplayNames(organizationIds, L1_CACHE_NAME).entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
