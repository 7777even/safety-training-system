package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.course.dto.*;
import com.sts.course.service.CourseItemService;
import com.sts.course.vo.CourseItemVO;
import com.sts.course.vo.LiveSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程内容项及直播信息管理接口。
 */
@RestController
@RequestMapping("/course/items")
@RequiredArgsConstructor
public class CourseItemController {

  private final CourseItemService itemService;

  /**
   * 查询章节下的内容项列表。
   */
  @GetMapping
  @SaCheckPermission("course:item:list")
  public List<CourseItemVO> listByChapter(@RequestParam Long chapterId) {
    return itemService.listByChapter(chapterId);
  }

  /**
   * 创建课程内容项。
   */
  @PostMapping
  @SaCheckPermission("course:item:add")
  public Long create(@RequestBody @Valid ItemCreateRequest request) {
    return itemService.create(request);
  }

  /**
   * 更新课程内容项。
   */
  @PutMapping("/{itemId}")
  @SaCheckPermission("course:item:edit")
  public void update(@PathVariable Long itemId, @RequestBody @Valid ItemUpdateRequest request) {
    itemService.update(itemId, request);
  }

  /**
   * 调整课程内容项的位置。
   */
  @PutMapping("/{itemId}/move")
  @SaCheckPermission("course:item:edit")
  public void move(@PathVariable Long itemId, @RequestBody @Valid ItemMoveRequest request) {
    itemService.move(itemId, request);
  }

  /**
   * 删除课程内容项。
   */
  @DeleteMapping("/{itemId}")
  @SaCheckPermission("course:item:delete")
  public void delete(@PathVariable Long itemId) {
    itemService.delete(itemId);
  }

  /**
   * 查询直播信息。
   */
  @GetMapping("/{itemId}/live-session")
  @SaCheckPermission("course:item:list")
  public LiveSessionVO getLiveSession(@PathVariable Long itemId) {
    return itemService.getLiveSession(itemId);
  }

  /**
   * 更新直播信息。
   */
  @PutMapping("/{itemId}/live-session")
  @SaCheckPermission("course:item:edit")
  public void updateLiveSession(@PathVariable Long itemId,
                                @RequestBody @Valid LiveSessionUpdateRequest request) {
    itemService.updateLiveSession(itemId, request);
  }
}
