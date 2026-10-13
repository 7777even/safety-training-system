package com.sts.org.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.dto.OrgPositionCreateRequest;
import com.sts.org.dto.OrgPositionQuery;
import com.sts.org.dto.OrgPositionUpdateRequest;
import com.sts.org.service.OrgPositionService;
import com.sts.org.vo.OrgPositionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 组织岗位管理接口。
 */
@RestController
@RequiredArgsConstructor
public class OrgPositionController {

  private final OrgPositionService orgPositionService;

  /**
   * 分页查询组织岗位。
   */
  @GetMapping("/org/positions")
  @SaCheckPermission("org:position:list")
  public PageResult<OrgPositionVO> pagePositions(PageRequest<OrgPositionQuery> request) {
    return orgPositionService.pagePositions(request);
  }

  /**
   * 查询组织岗位。
   */
  @GetMapping("/org/positions/{positionId}")
  @SaCheckPermission("org:position:list")
  public OrgPositionVO getPosition(@PathVariable Long positionId) {
    return orgPositionService.getPosition(positionId);
  }

  /**
   * 创建组织岗位。
   */
  @PostMapping("/org/positions")
  @SaCheckPermission("org:position:add")
  public Long createPosition(@RequestBody @Valid OrgPositionCreateRequest request) {
    return orgPositionService.createPosition(request);
  }

  /**
   * 更新组织岗位。
   */
  @PutMapping("/org/positions/{positionId}")
  @SaCheckPermission("org:position:edit")
  public void updatePosition(@PathVariable Long positionId,
                             @RequestBody @Valid OrgPositionUpdateRequest request) {
    orgPositionService.updatePosition(positionId, request);
  }

  /**
   * 删除组织岗位。
   */
  @DeleteMapping("/org/positions/{positionId}")
  @SaCheckPermission("org:position:delete")
  public void deletePosition(@PathVariable Long positionId) {
    orgPositionService.deletePosition(positionId);
  }
}
