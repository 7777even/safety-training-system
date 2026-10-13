package com.sts.admin.cache;

import com.sts.common.cache.CacheDomain;
import com.sts.common.cache.ClearScope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheEvictService {

    private final ApplicationEventPublisher eventPublisher;

    public void scheduleEvict(CacheDomain domain, ClearScope scope, String key) {
        eventPublisher.publishEvent(new CacheEvictEvent(domain, scope, key));
    }
}
