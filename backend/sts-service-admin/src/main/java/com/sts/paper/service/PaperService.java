package com.sts.paper.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.paper.dto.*;
import com.sts.paper.vo.*;

public interface PaperService {
  PageResult<PaperVO> page(PageRequest<PaperQuery> request);

  PaperVO get(Long paperId);

  Long create(PaperCreateRequest request);

  void update(Long paperId, PaperUpdateRequest request);

  void delete(Long paperId);

  PaperVersionVO compose(Long paperId, PaperComposeRequest request);

  PaperPreviewVO preview(Long paperId);

  PaperVersionVO publish(Long paperId, PaperComposeRequest request);

  java.util.List<com.sts.paper.vo.PaperVersionSummaryVO> listVersions(Long paperId);

  PaperVersionVO getVersion(Long paperVersionId);
}
