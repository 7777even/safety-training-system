package com.sts.common.cache;

import java.io.Serializable;

public record CacheEvictMessage(
    String cacheName,
    String key,
    ClearScope scope
) implements Serializable {
}
