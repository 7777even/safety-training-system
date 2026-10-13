package com.sts.learner.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.*;
import com.sts.learner.entity.LearnerMessage;
import com.sts.learner.mapper.LearnerMessageMapper;
import com.sts.learner.vo.LearnerMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** 持久化站内消息是投递依据；SSE失败不影响离线查询。 */
@Service @RequiredArgsConstructor
public class LearnerMessageService {
  /** 消息与接收人同事务写入。 */
  private final LearnerMessageMapper mapper;

  /** 业务事务中调用；事件唯一键确保重试不覆盖正文或重复投递。 */
  @Transactional
  public void send(String eventKey, String kind, String title, String content, Long resourceId,
                   Long parentId, Long itemId, Collection<Long> users) {
    var recipients = users.stream().filter(Objects::nonNull).distinct().toList();
    if (recipients.isEmpty()) return;
    Long org = TenantContext.requireTenantId();
    var message = new LearnerMessage();
    message.setOrganizationId(org); message.setEventKey(eventKey); message.setKind(kind);
    message.setTitle(title); message.setContent(content); message.setResourceId(resourceId);
    message.setParentId(parentId); message.setItemId(itemId);
    mapper.save(message);
    for (Long user : recipients) mapper.addRecipient(org, message.getMessageId(), user);
  }

  /** 读取登录用户全部站内消息，资源下架后仍保留通知。 */
  public PageResult<LearnerMessageVO> page(PageRequest<Void> request) {
    Long org = TenantContext.requireTenantId(), user = StpUtil.getLoginIdAsLong();
    return new PageResult<>(mapper.page(org, user, request.getPageSize(),
      (long) (request.getPageNum() - 1) * request.getPageSize()), mapper.count(org, user));
  }

  /** 首次进入24小时窗口也提醒，截止及完成后不再提醒。 */
  public boolean shouldRemind(LocalDateTime now, LocalDateTime deadline, String status) {
    return deadline != null && deadline.isAfter(now) && !deadline.isAfter(now.plusHours(24))
      && isPending(status);
  }

  /** 已交卷等待发布也不再是需要催办的任务。 */
  public boolean isPending(String status) {
    return !Set.of("COMPLETED", "PASSED", "FAILED", "EXPIRED", "CANCELLED", "WITHDRAWN", "ENDED", "RESULT_PUBLISHED", "SUBMITTED", "WAIT_REVIEW", "PENDING_PUBLISH", "PENDING_REVIEW")
      .contains(status == null ? "" : status);
  }
}
