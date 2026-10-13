package com.sts.exam.service;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.mapper.SysMenuMapper;
import com.sts.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Objects;
/** 评卷人沿用真实阅卷权限和本机构启用人员规则。 */
@Service @RequiredArgsConstructor
public class ExamReviewerEligibility {
  /** 人员归属和启用状态。 */ private final SysUserMapper users;
  /** 原有阅卷操作权限。 */ private final SysMenuMapper menus;
  /** 整份名单校验成功后才允许替换，防止无资格人员抹掉旧配置。 */
  public void requireEligible(Long organizationId,List<Long> ids) {
    for(Long id:ids) {
      var user=users.selectById(id);var perms=menus.selectPermsByUserId(id);
      if(user==null || !Objects.equals(user.getOrganizationId(),organizationId) || !Objects.equals(user.getStatus(),1)
        || perms==null || (!perms.contains("exam:review") && !perms.contains("*")))
        throw new BusinessException(HttpStatus.BAD_REQUEST,7070,"评卷人不存在、已停用、不属于当前机构或没有阅卷权限");
    }
  }
}
