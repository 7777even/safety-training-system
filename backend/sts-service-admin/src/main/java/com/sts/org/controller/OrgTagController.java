package com.sts.org.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.dto.OrgTagCreateRequest;
import com.sts.org.dto.OrgTagQuery;
import com.sts.org.dto.OrgTagUpdateRequest;
import com.sts.org.service.OrgTagService;
import com.sts.org.vo.OrgTagVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 组织标签管理接口。
 */
@RestController
@RequiredArgsConstructor
public class OrgTagController {

  private final OrgTagService orgTagService;

  /**
   * 分页查询组织标签。
   */
  @GetMapping("/org/tags")
  @SaCheckPermission("org:tag:list")
  public PageResult<OrgTagVO> pageTags(PageRequest<OrgTagQuery> request) {
    return orgTagService.pageTags(request);
  }

  /**
   * 查询组织标签。
   */
  @GetMapping("/org/tags/{tagId}")
  @SaCheckPermission("org:tag:list")
  public OrgTagVO getTag(@PathVariable Long tagId) {
    return orgTagService.getTag(tagId);
  }

  /**
   * 创建组织标签。
   */
  @PostMapping("/org/tags")
  @SaCheckPermission("org:tag:add")
  public Long createTag(@RequestBody @Valid OrgTagCreateRequest request) {
    return orgTagService.createTag(request);
  }

  /**
   * 更新组织标签。
   */
  @PutMapping("/org/tags/{tagId}")
  @SaCheckPermission("org:tag:edit")
  public void updateTag(@PathVariable Long tagId, @RequestBody @Valid OrgTagUpdateRequest request) {
    orgTagService.updateTag(tagId, request);
  }

  /**
   * 删除组织标签。
   */
  @DeleteMapping("/org/tags/{tagId}")
  @SaCheckPermission("org:tag:delete")
  public void deleteTag(@PathVariable Long tagId) {
    orgTagService.deleteTag(tagId);
  }
}
