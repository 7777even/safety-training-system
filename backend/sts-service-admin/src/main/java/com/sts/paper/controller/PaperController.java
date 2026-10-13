package com.sts.paper.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.paper.dto.PaperComposeRequest;
import com.sts.paper.dto.PaperCreateRequest;
import com.sts.paper.dto.PaperQuery;
import com.sts.paper.dto.PaperUpdateRequest;
import com.sts.paper.service.PaperService;
import com.sts.paper.vo.PaperPreviewVO;
import com.sts.paper.vo.PaperVO;
import com.sts.paper.vo.PaperVersionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 试卷编排、预览、发布及版本查询接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/paper/papers")
public class PaperController {

  private final PaperService paperService;

  /**
   * 分页查询试卷。
   */
  @GetMapping
  @SaCheckPermission("paper:paper:list")
  public PageResult<PaperVO> page(PageRequest<PaperQuery> request) {
    return paperService.page(request);
  }

  /**
   * 查询试卷。
   */
  @GetMapping("/{paperId}")
  @SaCheckPermission("paper:paper:list")
  public PaperVO get(@PathVariable Long paperId) {
    return paperService.get(paperId);
  }

  /**
   * 创建试卷。
   */
  @PostMapping
  @SaCheckPermission("paper:paper:add")
  public Long create(@RequestBody @Valid PaperCreateRequest request) {
    return paperService.create(request);
  }

  /**
   * 更新试卷。
   */
  @PutMapping("/{paperId}")
  @SaCheckPermission("paper:paper:edit")
  public void update(@PathVariable Long paperId, @RequestBody @Valid PaperUpdateRequest request) {
    paperService.update(paperId, request);
  }

  /**
   * 删除试卷。
   */
  @DeleteMapping("/{paperId}")
  @SaCheckPermission("paper:paper:delete")
  public void delete(@PathVariable Long paperId) {
    paperService.delete(paperId);
  }

  /**
   * 编排试卷并生成试卷版本。
   */
  @PostMapping("/{paperId}/compose")
  @SaCheckPermission("paper:paper:edit")
  public PaperVersionVO compose(@PathVariable Long paperId,
                                @RequestBody @Valid PaperComposeRequest request) {
    return paperService.compose(paperId, request);
  }

  /**
   * 预览当前配置结果。
   */
  @GetMapping("/{paperId}/preview")
  @SaCheckPermission("paper:paper:list")
  public PaperPreviewVO preview(@PathVariable Long paperId) {
    return paperService.preview(paperId);
  }

  /**
   * 发布试卷。
   */
  @PostMapping("/{paperId}/publish")
  @SaCheckPermission("paper:publish")
  public PaperVersionVO publish(@PathVariable Long paperId,
                                @RequestBody @Valid PaperComposeRequest request) {
    return paperService.publish(paperId, request);
  }

  /**
   * 查询版本列表。
   */
  @GetMapping("/{paperId}/versions")
  @SaCheckPermission("paper:paper:list")
  public java.util.List<com.sts.paper.vo.PaperVersionSummaryVO> listVersions(@PathVariable Long paperId) {
    return paperService.listVersions(paperId);
  }

  /**
   * 查询版本详情。
   */
  @GetMapping("/versions/{paperVersionId}")
  @SaCheckPermission("paper:paper:list")
  public PaperVersionVO getVersion(@PathVariable Long paperVersionId) {
    return paperService.getVersion(paperVersionId);
  }
}
