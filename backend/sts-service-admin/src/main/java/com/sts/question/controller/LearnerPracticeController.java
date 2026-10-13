package com.sts.question.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sts.common.api.*;
import com.sts.question.dto.LearnerPracticeModels.*;
import com.sts.question.service.LearnerPracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 当前登录学员的练习与错题入口；资源归属由Service真实校验。 */
@RestController
@RequiredArgsConstructor
@SaCheckLogin
@RequestMapping("/question/learner")
public class LearnerPracticeController {
  private final LearnerPracticeService service;
  /** 本人分配练习分页。 */
  @GetMapping("/practices") public PageResult<Task> tasks(PageRequest<Query> request) { return service.tasks(request); }
  /** 本人练习会话分页。 */
  @GetMapping("/sessions") public PageResult<View> sessions(PageRequest<Query> request) { return service.sessions(request); }
  /** 幂等启动本人练习或模拟。 */
  @PostMapping("/sessions") public View start(@RequestBody @Valid Start request) { return service.start(request); }
  /** 恢复本人会话。 */
  @GetMapping("/sessions/{id}") public View get(@PathVariable Long id) { return service.get(id); }
  /** 单题保存与受控反馈。 */
  @PutMapping("/sessions/{id}/items/{itemId}/answer") public View answer(@PathVariable Long id, @PathVariable Long itemId, @RequestBody @Valid Answer answer) { return service.answer(id, itemId, answer); }
  /** 提交模拟或练习。 */
  @PostMapping("/sessions/{id}/submit") public View submit(@PathVariable Long id) { return service.submit(id); }
  /** 清除个人记录。 */
  @DeleteMapping("/sessions/{id}") public void clear(@PathVariable Long id) { service.clear(id); }
  /** 两来源可见错题分页。 */
  @GetMapping("/wrong-questions") public PageResult<Question> wrongs(PageRequest<Query> request) { return service.wrongs(request); }
  /** 本人收藏题目安全摘要分页，不开放管理端题目详情。 */
  @GetMapping("/favorite-questions") public PageResult<FavoriteQuestion> favorites(PageRequest<Query> request) { return service.favorites(request); }
  /** 删除或标记已掌握。 */
  @PutMapping("/wrong-questions/{questionId}/disposition") public void disposition(@PathVariable Long questionId, @RequestBody @Valid Disposition request) { service.disposition(questionId, request.disposition()); }
  /** 收藏/取消本人题目。 */
  @PutMapping("/questions/{questionId}/favorite") public void favorite(@PathVariable Long questionId, @RequestBody @Valid Favorite request) { service.favorite(questionId, request.favorite()); }
}
