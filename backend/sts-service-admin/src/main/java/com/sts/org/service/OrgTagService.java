package com.sts.org.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.dto.OrgTagCreateRequest;
import com.sts.org.dto.OrgTagQuery;
import com.sts.org.dto.OrgTagUpdateRequest;
import com.sts.org.vo.OrgTagVO;

public interface OrgTagService {
  PageResult<OrgTagVO> pageTags(PageRequest<OrgTagQuery> request);

  OrgTagVO getTag(Long tagId);

  Long createTag(OrgTagCreateRequest request);

  void updateTag(Long tagId, OrgTagUpdateRequest request);

  void deleteTag(Long tagId);
}
