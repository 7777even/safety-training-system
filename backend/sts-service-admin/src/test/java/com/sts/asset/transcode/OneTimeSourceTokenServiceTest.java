package com.sts.asset.transcode;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class OneTimeSourceTokenServiceTest {

  @Test
  void issueStoresTenantJobAndFileWithRequestedTtl() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    OneTimeSourceTokenService service = new OneTimeSourceTokenService(redis);

    String token = service.issue(7L, 11L, 13L, Duration.ofMinutes(5));

    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    verify(values).set(key.capture(), org.mockito.ArgumentMatchers.eq("7:11:13"),
                       org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    assertThat(key.getValue()).isEqualTo("sts:transcode:source:" + token);
  }

  @Test
  void consumeReturnsTenantBoundGrantAndAtomicallyDeletesToken() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(), anyList())).thenReturn("7:11:13");
    OneTimeSourceTokenService service = new OneTimeSourceTokenService(redis);

    String token = "a".repeat(43);
    OneTimeSourceTokenService.SourceGrant grant = service.consume(token);

    assertThat(grant).isEqualTo(new OneTimeSourceTokenService.SourceGrant(7L, 11L, 13L));
    verify(redis).execute(any(), org.mockito.ArgumentMatchers.eq(
      java.util.List.of("sts:transcode:source:" + token)));
  }
}
