package com.sts.learner.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.learner.vo.LearnerMessageVO;
import com.sts.learner.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 登录学员本人首页与消息；身份只来自会话，不接受userId。 */
@RestController @RequestMapping("/learner") @SaCheckLogin @RequiredArgsConstructor
public class LearnerController {
  /** 首页本人领域聚合。 */
  private final LearnerHomeService home;
  /** 本人持久化消息。 */
  private final LearnerMessageService messages;
  /** 在线实时变化推送。 */
  private final LearnerMessageStream stream;

  /** 分页读取课程、考试/测试和培训任务。 */
  @GetMapping("/home/tasks")
  public PageResult<LearnerHomeService.Task> tasks(PageRequest<LearnerHomeService.Query> request) {
    validatePage(request); return home.tasks(request);
  }

  /** 查询本人消息，业务失效后仍可查看通知。 */
  @GetMapping("/messages")
  public PageResult<LearnerMessageVO> messages(PageRequest<Void> request) {
    validatePage(request); return messages.page(request);
  }

  /** 消息SQL与首页内存分页都拒绝负偏移和过大的单次读取。 */
  private void validatePage(PageRequest<?> request) {
    if (request.getPageNum() < 1 || request.getPageSize() < 1 || request.getPageSize() > 200)
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "页码必须大于0，每页条数须为1至200");
  }

  /** SSE不包装Result；代理关闭缓冲，客户端用Authorization而不是URL携带令牌。 */
  @GetMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> stream() {
    return ResponseEntity.ok().header("X-Accel-Buffering", "no").cacheControl(CacheControl.noStore()).body(stream.connect());
  }
}
