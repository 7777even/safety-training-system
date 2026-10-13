package com.sts.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.dto.OrgPositionCreateRequest;
import com.sts.org.dto.OrgPositionQuery;
import com.sts.org.dto.OrgPositionUpdateRequest;
import com.sts.org.entity.OrgUserPosition;
import com.sts.org.entity.OrgPosition;
import com.sts.org.mapper.OrgUserPositionMapper;
import com.sts.org.mapper.OrgPositionMapper;
import com.sts.org.vo.OrgPositionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrgPositionServiceImpl extends BaseServiceImpl<OrgPositionMapper, OrgPosition>
  implements OrgPositionService {

  private final OrgPositionMapper positionMapper;
  private final OrgUserPositionMapper userPositionMapper;
  private final OrganizationMapper organizationMapper;

  @Override
  public PageResult<OrgPositionVO> pagePositions(PageRequest<OrgPositionQuery> request) {
    OrgPositionQuery query = request.getQuery();
    LambdaQueryWrapper<OrgPosition> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getOrganizationId() != null, OrgPosition::getOrganizationId, query.getOrganizationId())
        .like(StringUtils.hasText(query.getPositionCode()), OrgPosition::getPositionCode, query.getPositionCode())
        .like(StringUtils.hasText(query.getPositionName()), OrgPosition::getPositionName, query.getPositionName())
        .eq(query.getStatus() != null, OrgPosition::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(OrgPosition::getCreateTime);
    Page<OrgPosition> page = positionMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public OrgPositionVO getPosition(Long positionId) {
    return toVO(requirePosition(positionId));
  }

  @Override
  @Transactional
  public Long createPosition(OrgPositionCreateRequest request) {
    requireOrganization(request.getOrganizationId());
    assertCodeUnique(request.getOrganizationId(), request.getPositionCode(), null);
    OrgPosition position = new OrgPosition();
    position.setOrganizationId(request.getOrganizationId());
    position.setPositionCode(request.getPositionCode().trim());
    position.setPositionName(request.getPositionName().trim());
    position.setDescription(request.getDescription() == null ? "" : request.getDescription());
    position.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    try {
      positionMapper.insert(position);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 4011, "岗位编码已存在");
    }
    return position.getPositionId();
  }

  @Override
  @Transactional
  public void updatePosition(Long positionId, OrgPositionUpdateRequest request) {
    OrgPosition position = requirePosition(positionId);
    assertCodeUnique(position.getOrganizationId(), request.getPositionCode(), positionId);
    position.setVersion(request.getVersion());
    position.setPositionCode(request.getPositionCode().trim());
    position.setPositionName(request.getPositionName().trim());
    position.setDescription(request.getDescription() == null ? "" : request.getDescription());
    position.setStatus(request.getStatus());
    try {
      positionMapper.updateByIdWithVersionCheck(position);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 4011, "岗位编码已存在");
    }
  }

  @Override
  @Transactional
  public void deletePosition(Long positionId) {
    requirePosition(positionId);
    Long bound = userPositionMapper.selectCount(
      new LambdaQueryWrapper<OrgUserPosition>().eq(OrgUserPosition::getPositionId, positionId));
    if (bound != null && bound > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 4012, "岗位仍被用户引用，无法删除");
    }
    this.removeById(positionId);
  }

  private void assertCodeUnique(Long organizationId, String positionCode, Long excludeId) {
    LambdaQueryWrapper<OrgPosition> wrapper = new LambdaQueryWrapper<OrgPosition>()
      .eq(OrgPosition::getOrganizationId, organizationId)
      .eq(OrgPosition::getPositionCode, positionCode.trim());
    if (excludeId != null) {
      wrapper.ne(OrgPosition::getPositionId, excludeId);
    }
    Long count = positionMapper.selectCount(wrapper);
    if (count != null && count > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 4011, "岗位编码已存在");
    }
  }

  private void requireOrganization(Long organizationId) {
    Organization org = organizationMapper.selectById(organizationId);
    if (org == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4003, "机构不存在");
    }
  }

  private OrgPosition requirePosition(Long positionId) {
    OrgPosition position = positionMapper.selectById(positionId);
    if (position == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 4013, "岗位不存在");
    }
    return position;
  }

  private OrgPositionVO toVO(OrgPosition position) {
    OrgPositionVO vo = new OrgPositionVO();
    vo.setPositionId(position.getPositionId());
    vo.setOrganizationId(position.getOrganizationId());
    vo.setPositionCode(position.getPositionCode());
    vo.setPositionName(position.getPositionName());
    vo.setDescription(position.getDescription());
    vo.setStatus(position.getStatus());
    vo.setVersion(position.getVersion());
    vo.setCreateTime(position.getCreateTime());
    return vo;
  }
}
