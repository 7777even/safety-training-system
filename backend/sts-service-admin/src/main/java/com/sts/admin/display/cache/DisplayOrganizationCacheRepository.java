package com.sts.admin.display.cache;

import java.util.Map;
import java.util.Set;

public interface DisplayOrganizationCacheRepository {

    Map<Long, String> getDisplayNames(Set<Long> organizationIds, String l1CacheName);
}
