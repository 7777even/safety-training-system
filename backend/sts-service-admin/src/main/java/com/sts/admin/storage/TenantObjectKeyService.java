package com.sts.admin.storage;

import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 为新写入对象增加当前机构编码根目录，并识别需要兼容的历史无前缀对象键。
 */
@Component
@RequiredArgsConstructor
public class TenantObjectKeyService {
  private static final Pattern ORG_CODE = Pattern.compile("[A-Z0-9_-]{2,64}");

  private final OrganizationMapper organizationMapper;

  public String forCurrentTenant(String relativeKey) {
    if (!isSafeRelativeKey(relativeKey)) {
      throw invalidTenantObjectKey();
    }
    return currentOrganizationCode() + "/" + relativeKey;
  }

  public boolean isLegacyOrCurrentTenantKey(String objectKey, String legacyPrefix) {
    if (objectKey == null || legacyPrefix == null || legacyPrefix.isBlank()) {
      return false;
    }
    return objectKey.startsWith(legacyPrefix)
      || objectKey.startsWith(currentOrganizationCode() + "/" + legacyPrefix);
  }

  private String currentOrganizationCode() {
    Organization organization = organizationMapper.selectById(TenantContext.requireTenantId());
    String code = organization == null ? null : organization.getOrgCode();
    if (code == null || !ORG_CODE.matcher(code).matches()) {
      throw invalidTenantObjectKey();
    }
    return code;
  }

  private static boolean isSafeRelativeKey(String key) {
    return key != null && !key.isBlank() && !key.startsWith("/") && !key.endsWith("/")
      && !key.contains("\\") && !key.contains("../") && !key.contains("/..")
      && !key.contains("//");
  }

  private static BusinessException invalidTenantObjectKey() {
    return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 4051,
                                 "机构文件目录不可用，拒绝写入文件");
  }
}
