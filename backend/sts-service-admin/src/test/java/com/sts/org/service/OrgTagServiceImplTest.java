package com.sts.org.service;

import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.dto.OrgTagCreateRequest;
import com.sts.org.entity.OrgTag;
import com.sts.org.mapper.OrgUserTagMapper;
import com.sts.org.mapper.OrgTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrgTagServiceImplTest {

  private OrgTagMapper tagMapper;
  private OrgUserTagMapper userTagMapper;
  private OrganizationMapper organizationMapper;
  private OrgTagServiceImpl service;

  @BeforeEach
  void setUp() {
    tagMapper = mock(OrgTagMapper.class);
    userTagMapper = mock(OrgUserTagMapper.class);
    organizationMapper = mock(OrganizationMapper.class);
    service = new OrgTagServiceImpl(tagMapper, userTagMapper, organizationMapper);

    Organization org = new Organization();
    org.setOrganizationId(1L);
    when(organizationMapper.selectById(1L)).thenReturn(org);
    when(tagMapper.selectCount(any())).thenReturn(0L);
  }

  @Test
  @DisplayName("createTag：成功写入并返回 ID")
  void createTag_success() {
    when(tagMapper.insert(any(OrgTag.class))).thenAnswer(inv -> {
      ((OrgTag) inv.getArgument(0)).setTagId(11L);
      return 1;
    });

    OrgTagCreateRequest req = new OrgTagCreateRequest();
    req.setOrganizationId(1L);
    req.setTagName("一线");
    Long id = service.createTag(req);

    assertThat(id).isEqualTo(11L);
    verify(tagMapper).insert(any(OrgTag.class));
  }

  @Test
  @DisplayName("createTag：重名 → 409/4001")
  void createTag_duplicateName() {
    when(tagMapper.selectCount(any())).thenReturn(1L);
    OrgTagCreateRequest req = new OrgTagCreateRequest();
    req.setOrganizationId(1L);
    req.setTagName("一线");

    assertThatThrownBy(() -> service.createTag(req))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(4001);
      });
    verify(tagMapper, never()).insert(any(OrgTag.class));
  }

  @Test
  @DisplayName("deleteTag：仍被引用 → 409/4002")
  void deleteTag_inUse() {
    OrgTag tag = new OrgTag();
    tag.setTagId(3L);
    when(tagMapper.selectById(3L)).thenReturn(tag);
    when(userTagMapper.selectCount(any())).thenReturn(2L);

    assertThatThrownBy(() -> service.deleteTag(3L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(4002);
      });
  }
}
