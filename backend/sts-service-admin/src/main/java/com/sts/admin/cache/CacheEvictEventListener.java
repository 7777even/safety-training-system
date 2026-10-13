package com.sts.admin.cache;

import com.sts.admin.config.AsyncConfig;
import com.sts.common.cache.CacheEvictMessage;
import com.sts.common.cache.CacheEvictPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class CacheEvictEventListener {

    private final CacheEvictPublisher publisher;

    @Async(AsyncConfig.IMPORTANT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onEvict(CacheEvictEvent event) {
        publisher.publish(new CacheEvictMessage(
            event.domain().cacheName(),
            event.key(),
            event.scope()
        ));
    }
}
