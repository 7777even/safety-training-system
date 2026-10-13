package com.sts.question.service;

import com.sts.question.dto.KnowledgePointCreateRequest;
import com.sts.question.dto.KnowledgePointUpdateRequest;
import com.sts.question.vo.KnowledgePointVO;

import java.util.List;

public interface KnowledgePointService {
  List<KnowledgePointVO> tree();

  KnowledgePointVO get(Long knowledgePointId);

  Long create(KnowledgePointCreateRequest request);

  void update(Long knowledgePointId, KnowledgePointUpdateRequest request);

  void delete(Long knowledgePointId);
}
