package com.sts.question.service;

import com.sts.common.exception.BusinessException;
import com.sts.question.dto.KnowledgePointCreateRequest;
import com.sts.question.entity.KnowledgePoint;
import com.sts.question.mapper.KnowledgePointMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgePointServiceImplTest {

  private KnowledgePointMapper knowledgePointMapper;
  private KnowledgePointServiceImpl service;

  @BeforeEach
  void setUp() {
    knowledgePointMapper = mock(KnowledgePointMapper.class);
    service = new KnowledgePointServiceImpl(knowledgePointMapper);
  }

  @Test
  @DisplayName("create：根节点 ancestors=0")
  void create_rootAncestors() {
    when(knowledgePointMapper.insert(any(KnowledgePoint.class))).thenAnswer(inv -> {
      KnowledgePoint kp = inv.getArgument(0);
      kp.setKnowledgePointId(1L);
      return 1;
    });
    KnowledgePointCreateRequest req = new KnowledgePointCreateRequest();
    req.setParentId(0L);
    req.setKnowledgeCode("KP1");
    req.setKnowledgeName("安全");
    req.setSortOrder(1);
    req.setStatus(1);
    assertThat(service.create(req)).isEqualTo(1L);
    verify(knowledgePointMapper).insert(argThat((KnowledgePoint kp) -> "0".equals(kp.getAncestors())));
  }

  @Test
  @DisplayName("delete：有子节点冲突")
  void delete_withChildren_throws() {
    KnowledgePoint kp = new KnowledgePoint();
    kp.setKnowledgePointId(1L);
    when(knowledgePointMapper.selectById(1L)).thenReturn(kp);
    when(knowledgePointMapper.selectCount(any())).thenReturn(2L);
    assertThatThrownBy(() -> service.delete(1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(6003));
  }
}
