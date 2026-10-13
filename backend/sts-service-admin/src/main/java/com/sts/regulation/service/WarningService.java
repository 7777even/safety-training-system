package com.sts.regulation.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.regulation.dto.WarningQuery;
import com.sts.regulation.vo.WarningVO;
import org.springframework.http.ResponseEntity;

public interface WarningService {
  long unreadCount();

  PageResult<WarningVO> page(PageRequest<WarningQuery> request);

  WarningVO get(Long warningId);

  void markRead(Long warningId, Long viewerUserId);

  ResponseEntity<byte[]> export(WarningQuery query);
}
