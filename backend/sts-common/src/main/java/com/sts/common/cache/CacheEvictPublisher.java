package com.sts.common.cache;

public interface CacheEvictPublisher {
    void publish(CacheEvictMessage message);
}
