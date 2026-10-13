package com.sts.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.question.dto.KnowledgePointCreateRequest;
import com.sts.question.dto.KnowledgePointUpdateRequest;
import com.sts.question.service.KnowledgePointService;
import com.sts.question.vo.KnowledgePointVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识点树及知识点管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/question/knowledge-points")
public class KnowledgePointController {

  private final KnowledgePointService knowledgePointService;

  /**
   * 查询树形结构。
   */
  @GetMapping("/tree")
  @SaCheckPermission("question:knowledge:list")
  public List<KnowledgePointVO> tree() {
    return knowledgePointService.tree();
  }

  /**
   * 查询知识点。
   */
  @GetMapping("/{knowledgePointId}")
  @SaCheckPermission("question:knowledge:list")
  public KnowledgePointVO get(@PathVariable Long knowledgePointId) {
    return knowledgePointService.get(knowledgePointId);
  }

  /**
   * 创建知识点。
   */
  @PostMapping
  @SaCheckPermission("question:knowledge:add")
  public Long create(@RequestBody @Valid KnowledgePointCreateRequest request) {
    return knowledgePointService.create(request);
  }

  /**
   * 更新知识点。
   */
  @PutMapping("/{knowledgePointId}")
  @SaCheckPermission("question:knowledge:edit")
  public void update(@PathVariable Long knowledgePointId,
                     @RequestBody @Valid KnowledgePointUpdateRequest request) {
    knowledgePointService.update(knowledgePointId, request);
  }

  /**
   * 删除知识点。
   */
  @DeleteMapping("/{knowledgePointId}")
  @SaCheckPermission("question:knowledge:delete")
  public void delete(@PathVariable Long knowledgePointId) {
    knowledgePointService.delete(knowledgePointId);
  }
}
