package com.sts.org.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.org.dto.OrgPositionCreateRequest;
import com.sts.org.dto.OrgPositionQuery;
import com.sts.org.dto.OrgPositionUpdateRequest;
import com.sts.org.vo.OrgPositionVO;

public interface OrgPositionService {
  PageResult<OrgPositionVO> pagePositions(PageRequest<OrgPositionQuery> request);

  OrgPositionVO getPosition(Long positionId);

  Long createPosition(OrgPositionCreateRequest request);

  void updatePosition(Long positionId, OrgPositionUpdateRequest request);

  void deletePosition(Long positionId);
}
