package com.sts.asset.transcode;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
public class OneTimeSourceTokenService {
  private static final String PREFIX = "sts:transcode:source:";
  private static final DefaultRedisScript<String> TAKE = new DefaultRedisScript<>(
    "local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]); end; return v", String.class);
  private final StringRedisTemplate redis;
  private final SecureRandom random = new SecureRandom();

  public OneTimeSourceTokenService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public String issue(Long organizationId, Long jobId, Long fileInfoId, Duration ttl) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    redis.opsForValue().set(PREFIX + token,
                            organizationId + ":" + jobId + ":" + fileInfoId, ttl);
    return token;
  }

  public SourceGrant consume(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
    String value = redis.execute(TAKE, List.of(PREFIX + token));
    if (value == null || !value.matches("\\d+:\\d+:\\d+")) return null;
    String[] parts = value.split(":");
    return new SourceGrant(Long.valueOf(parts[0]), Long.valueOf(parts[1]), Long.valueOf(parts[2]));
  }

  public record SourceGrant(Long organizationId, Long jobId, Long fileInfoId) {
  }
}
