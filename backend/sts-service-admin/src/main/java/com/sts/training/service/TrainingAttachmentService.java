package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.vo.TrainingAttachmentVO;

public interface TrainingAttachmentService {
  PageResult<TrainingAttachmentVO> page(PageRequest<TrainingAttachmentQuery> request);
  TrainingAttachmentVO get(Long trainingAttachmentId);
  /** 按资料记录读取受控原始文件；记录不存在或文件不可读时拒绝，不暴露任意文件 ID。 */
  TrainingAttachmentFile openFile(Long trainingAttachmentId);
  Long create(TrainingAttachmentCreateRequest request);
  void update(Long trainingAttachmentId, TrainingAttachmentUpdateRequest request);
  void delete(Long trainingAttachmentId);
}
