package com.sts.admin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "sts.auth")
public class AuthConfigProperties {
    private List<String> enabledTypes = new ArrayList<>();
    private String redisKeyPrefix = "sts:satoken:";

    public List<String> getEnabledTypes() {
        return enabledTypes;
    }

    public void setEnabledTypes(List<String> enabledTypes) {
        this.enabledTypes = enabledTypes;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }
}
