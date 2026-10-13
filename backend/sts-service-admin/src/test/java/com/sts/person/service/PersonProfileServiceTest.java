package com.sts.person.service;

import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.person.dto.PersonProfileRequest;
import com.sts.person.entity.PersonProfile;
import com.sts.person.mapper.*;
import com.sts.training.service.TrainingResultSyncService;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 人员特征写入的归属、版本和清空语义。 */
class PersonProfileServiceTest {
  private final SysUserMapper users = mock(SysUserMapper.class);
  private final PersonProfileMapper profiles = mock(PersonProfileMapper.class);
  private final PersonClassificationMapper facts = mock(PersonClassificationMapper.class);
  private final PersonProfileService service = new PersonProfileService(users, profiles, facts, mock(ObjectProvider.class));
  @BeforeEach void setup() { TenantContext.setTenantId(1L); }
  @AfterEach void cleanup() { TenantContext.clear(); }

  @Test void absentUserCannotReadOrWriteFeatures() {
    assertThatThrownBy(() -> service.get(99L)).isInstanceOf(BusinessException.class);
    verifyNoInteractions(profiles, facts);
  }
  @Test void staleVersionCannotOverwriteProfile() {
    when(users.selectOne(any())).thenReturn(new SysUser());
    PersonProfile profile = new PersonProfile(); profile.setVersion(2);
    when(profiles.selectOne(any())).thenReturn(profile);
    PersonProfileRequest request = new PersonProfileRequest(); request.setVersion(1);
    assertThatThrownBy(() -> service.save(7L, request)).isInstanceOf(BusinessException.class);
    verify(profiles, never()).updateById(any(PersonProfile.class));
  }
  @Test void fullUpdateRetainsExplicitClearingValues() {
    when(users.selectOne(any())).thenReturn(new SysUser());
    PersonProfile profile = new PersonProfile(); profile.setProfileId(10L); profile.setVersion(2);
    profile.setOccupation("电工"); profile.setBirthDate(java.time.LocalDate.of(1990,1,1));
    when(profiles.selectOne(any())).thenReturn(profile); when(profiles.updateById(any(PersonProfile.class))).thenReturn(1);
    PersonProfileRequest request = new PersonProfileRequest(); request.setVersion(2);
    service.save(7L, request);
    ArgumentCaptor<PersonProfile> saved = ArgumentCaptor.forClass(PersonProfile.class);
    verify(profiles).updateById(saved.capture());
    assertThat(saved.getValue().getBirthDate()).isNull(); assertThat(saved.getValue().getOccupation()).isNull();
    assertThat(saved.getValue().getProfileId()).isEqualTo(10L);
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(1L);
    verifyNoInteractions(facts);
  }
}
