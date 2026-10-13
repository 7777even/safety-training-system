package com.sts.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.question.dto.KnowledgePointCreateRequest;
import com.sts.question.dto.KnowledgePointUpdateRequest;
import com.sts.question.entity.KnowledgePoint;
import com.sts.question.mapper.KnowledgePointMapper;
import com.sts.question.vo.KnowledgePointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgePointServiceImpl
  extends BaseServiceImpl<KnowledgePointMapper, KnowledgePoint>
  implements KnowledgePointService {

  private final KnowledgePointMapper knowledgePointMapper;

  @Override
  public List<KnowledgePointVO> tree() {
    List<KnowledgePoint> all = knowledgePointMapper.selectList(
      new LambdaQueryWrapper<KnowledgePoint>()
        .orderByAsc(KnowledgePoint::getSortOrder)
        .orderByAsc(KnowledgePoint::getKnowledgePointId));
    List<KnowledgePointVO> vos = all.stream().map(this::toVO).toList();
    Map<Long, KnowledgePointVO> byId = vos.stream()
      .collect(Collectors.toMap(KnowledgePointVO::getKnowledgePointId, v -> v));
    List<KnowledgePointVO> roots = new ArrayList<>();
    for (KnowledgePointVO vo : vos) {
      if (vo.getParentId() == null || vo.getParentId() == 0L) {
        roots.add(vo);
      } else {
        KnowledgePointVO parent = byId.get(vo.getParentId());
        if (parent != null) {
          parent.getChildren().add(vo);
        } else {
          roots.add(vo);
        }
      }
    }
    return roots;
  }

  @Override
  public KnowledgePointVO get(Long knowledgePointId) {
    return toVO(require(knowledgePointId));
  }

  @Override
  @Transactional
  public Long create(KnowledgePointCreateRequest request) {
    KnowledgePoint entity = new KnowledgePoint();
    entity.setParentId(request.getParentId() == null ? 0L : request.getParentId());
    entity.setKnowledgeCode(request.getKnowledgeCode().trim());
    entity.setKnowledgeName(request.getKnowledgeName().trim());
    entity.setSortOrder(request.getSortOrder());
    entity.setStatus(request.getStatus());
    entity.setAncestors(resolveAncestors(entity.getParentId()));
    try {
      knowledgePointMapper.insert(entity);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 6002, "同机构下知识点编码已存在");
    }
    return entity.getKnowledgePointId();
  }

  @Override
  @Transactional
  public void update(Long knowledgePointId, KnowledgePointUpdateRequest request) {
    KnowledgePoint entity = require(knowledgePointId);
    entity.setVersion(request.getVersion());
    entity.setKnowledgeName(request.getKnowledgeName().trim());
    entity.setSortOrder(request.getSortOrder());
    entity.setStatus(request.getStatus());
    knowledgePointMapper.updateById(entity);
  }

  @Override
  @Transactional
  public void delete(Long knowledgePointId) {
    KnowledgePoint entity = require(knowledgePointId);
    Long childCount = knowledgePointMapper.selectCount(
      new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getParentId, knowledgePointId));
    if (childCount != null && childCount > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 6003, "存在子知识点，不能删除");
    }
    this.removeById(entity.getKnowledgePointId());
  }

  private String resolveAncestors(Long parentId) {
    if (parentId == null || parentId == 0L) {
      return "0";
    }
    KnowledgePoint parent = require(parentId);
    return parent.getAncestors() + "," + parent.getKnowledgePointId();
  }

  private KnowledgePoint require(Long id) {
    KnowledgePoint entity = knowledgePointMapper.selectById(id);
    if (entity == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 6001, "知识点不存在");
    }
    return entity;
  }

  private KnowledgePointVO toVO(KnowledgePoint entity) {
    KnowledgePointVO vo = new KnowledgePointVO();
    vo.setKnowledgePointId(entity.getKnowledgePointId());
    vo.setOrganizationId(entity.getOrganizationId());
    vo.setParentId(entity.getParentId());
    vo.setKnowledgeCode(entity.getKnowledgeCode());
    vo.setKnowledgeName(entity.getKnowledgeName());
    vo.setAncestors(entity.getAncestors());
    vo.setSortOrder(entity.getSortOrder());
    vo.setStatus(entity.getStatus());
    vo.setVersion(entity.getVersion());
    vo.setCreateTime(entity.getCreateTime());
    return vo;
  }
}
