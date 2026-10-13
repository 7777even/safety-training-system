package com.sts.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.dto.OrgTagCreateRequest;
import com.sts.org.dto.OrgTagQuery;
import com.sts.org.dto.OrgTagUpdateRequest;
import com.sts.org.entity.OrgUserTag;
import com.sts.org.entity.OrgTag;
import com.sts.org.mapper.OrgUserTagMapper;
import com.sts.org.mapper.OrgTagMapper;
import com.sts.org.vo.OrgTagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OrgTagServiceImpl extends BaseServiceImpl<OrgTagMapper, OrgTag> implements OrgTagService {

  private final OrgTagMapper tagMapper;
  private final OrgUserTagMapper userTagMapper;
  private final OrganizationMapper organizationMapper;

  @Override
  public PageResult<OrgTagVO> pageTags(PageRequest<OrgTagQuery> request) {
    OrgTagQuery query = request.getQuery();
    LambdaQueryWrapper<OrgTag> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getOrganizationId() != null, OrgTag::getOrganizationId, query.getOrganizationId())
        .like(StringUtils.hasText(query.getTagName()), OrgTag::getTagName, query.getTagName())
        .eq(query.getStatus() != null, OrgTag::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(OrgTag::getCreateTime);
    Page<OrgTag> page = tagMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public OrgTagVO getTag(Long tagId) {
    return toVO(requireTag(tagId));
  }

  @Override
  @Transactional
  public Long createTag(OrgTagCreateRequest request) {
    requireOrganization(request.getOrganizationId());
    assertTagNameUnique(request.getOrganizationId(), request.getTagName(), null);
    OrgTag tag = new OrgTag();
    tag.setOrganizationId(request.getOrganizationId());
    tag.setTagName(request.getTagName().trim());
    tag.setDescription(request.getDescription() == null ? "" : request.getDescription());
    tag.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    try {
      tagMapper.insert(tag);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 4001, "标签名称已存在");
    }
    return tag.getTagId();
  }

  @Override
  @Transactional
  public void updateTag(Long tagId, OrgTagUpdateRequest request) {
    OrgTag tag = requireTag(tagId);
    assertTagNameUnique(tag.getOrganizationId(), request.getTagName(), tagId);
    tag.setVersion(request.getVersion());
    tag.setTagName(request.getTagName().trim());
    tag.setDescription(request.getDescription() == null ? "" : request.getDescription());
    tag.setStatus(request.getStatus());
    try {
      tagMapper.updateByIdWithVersionCheck(tag);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 4001, "标签名称已存在");
    }
  }

  @Override
  @Transactional
  public void deleteTag(Long tagId) {
    requireTag(tagId);
    Long bound = userTagMapper.selectCount(
      new LambdaQueryWrapper<OrgUserTag>().eq(OrgUserTag::getTagId, tagId));
    if (bound != null && bound > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 4002, "标签仍被用户引用，无法删除");
    }
    this.removeById(tagId);
  }

  private void assertTagNameUnique(Long organizationId, String tagName, Long excludeId) {
    LambdaQueryWrapper<OrgTag> wrapper = new LambdaQueryWrapper<OrgTag>()
      .eq(OrgTag::getOrganizationId, organizationId)
      .eq(OrgTag::getTagName, tagName.trim());
    if (excludeId != null) {
      wrapper.ne(OrgTag::getTagId, excludeId);
    }
    Long count = tagMapper.selectCount(wrapper);
    if (count != null && count > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 4001, "标签名称已存在");
    }
  }

  private void requireOrganization(Long organizationId) {
    Organization org = organizationMapper.selectById(organizationId);
    if (org == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 4003, "机构不存在");
    }
  }

  private OrgTag requireTag(Long tagId) {
    OrgTag tag = tagMapper.selectById(tagId);
    if (tag == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 4004, "标签不存在");
    }
    return tag;
  }

  private OrgTagVO toVO(OrgTag tag) {
    OrgTagVO vo = new OrgTagVO();
    vo.setTagId(tag.getTagId());
    vo.setOrganizationId(tag.getOrganizationId());
    vo.setTagName(tag.getTagName());
    vo.setDescription(tag.getDescription());
    vo.setStatus(tag.getStatus());
    vo.setVersion(tag.getVersion());
    vo.setCreateTime(tag.getCreateTime());
    return vo;
  }
}
