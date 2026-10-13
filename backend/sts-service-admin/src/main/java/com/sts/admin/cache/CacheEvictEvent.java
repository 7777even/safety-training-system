package com.sts.admin.cache;

import com.sts.common.cache.CacheDomain;
import com.sts.common.cache.ClearScope;

record CacheEvictEvent(CacheDomain domain, ClearScope scope, String key) {
}
