package com.sts.learner.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.learner.mapper.LearnerMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** SSE仅通知消息数量变化，正文仍走受控分页；不以自增ID假设事务提交顺序。 */
@Component @RequiredArgsConstructor @Slf4j
public class LearnerMessageStream {
  /** 本人消息持久化查询。 */
  private final LearnerMessageMapper mapper;
  /** 连接不保存业务正文；关闭后立即释放。 */
  private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
  /** 建立连接时冻结身份，后台线程不读取请求ThreadLocal。 */
  private static class Connection {
    final Long org, user;
    final String token;
    final SseEmitter emitter;
    long total = -1;
    Connection(Long org, Long user, String token, SseEmitter emitter) {
      this.org = org; this.user = user; this.token = token; this.emitter = emitter;
    }
  }

  /** 每次连接都会通知重新拉取列表，重连和离线消息无需客户端游标。 */
  public SseEmitter connect() {
    var emitter = new SseEmitter(3600000L);
    var connection = new Connection(TenantContext.requireTenantId(), StpUtil.getLoginIdAsLong(), StpUtil.getTokenValue(), emitter);
    emitter.onCompletion(() -> connections.remove(connection));
    emitter.onTimeout(() -> { connections.remove(connection); emitter.complete(); });
    emitter.onError(error -> connections.remove(connection));
    connections.add(connection);
    return emitter;
  }

  /** 3秒扫描在线本人消息；同机构/用户的多个连接共用一次计数查询。 */
  @Scheduled(fixedDelay = 3000)
  public void push() {
    List<Connection> active = new ArrayList<>();
    for (Connection c : connections) {
      try {
        Object loginId = StpUtil.getLoginIdByToken(c.token);
        if (loginId != null && c.user.toString().equals(loginId.toString())) { active.add(c); continue; }
      } catch (RuntimeException e) { log.debug("学员消息连接鉴权失败，用户={}", c.user); }
      connections.remove(c); c.emitter.complete();
    }
    Map<String, Long> totals = new HashMap<>();
    Map<Long, Set<Long>> usersByOrg = new HashMap<>();
    for (Connection c : active) usersByOrg.computeIfAbsent(c.org, ignored -> new HashSet<>()).add(c.user);
    for (var entry : usersByOrg.entrySet()) {
      Long org = entry.getKey(); List<Long> users = new ArrayList<>(entry.getValue());
      try {
        TenantContext.runWithTenant(org, () -> {
          for (int offset = 0; offset < users.size(); offset += 200) {
            for (var count : mapper.counts(org, users.subList(offset, Math.min(offset + 200, users.size()))))
              totals.put(org + ":" + count.userId(), count.total());
          }
        });
      } catch (RuntimeException e) {
        for (Connection c : active) if (c.org.equals(org)) { connections.remove(c); c.emitter.complete(); }
        log.error("学员消息实时读取失败，机构={}", org, e);
        active.removeIf(c -> c.org.equals(org));
      }
    }
    for (Connection c : active) {
      try {
        String key = c.org + ":" + c.user;
        long total = totals.getOrDefault(key, 0L);
        if (total != c.total) {
          c.emitter.send(SseEmitter.event().name("messages").data(Map.of("total", total)));
          c.total = total;
        } else {
          c.emitter.send(SseEmitter.event().comment("heartbeat"));
        }
      } catch (Exception e) {
        connections.remove(c); c.emitter.complete();
        log.debug("学员消息连接关闭，机构={}，用户={}", c.org, c.user);
      }
    }
  }
}
