package com.sts.admin.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sts.cache")
public class CacheProperties {

    private String keyPrefix = "sts:cache";

    private Evict evict = new Evict();

    @Data
    public static class Evict {
        private String channel = "sts:cache:evict";
    }
}
