package com.sts.admin.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sts.admin.cache.CacheEvictService;
import com.sts.admin.entity.*;
import com.sts.admin.mapper.*;
import com.sts.admin.system.dto.*;
import com.sts.admin.system.vo.MyProfileVO;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.cache.CacheDomain;
import com.sts.common.cache.ClearScope;
import com.sts.common.exception.BusinessException;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;

/** 本人操作与管理员用户编辑分离，避免授权字段进入更新路径。 */
@Service @RequiredArgsConstructor
public class MyProfileService {
  /** 用户与版本化写入。 */
  private final SysUserMapper users;
  /** 仅查询本人的local凭证。 */
  private final SysAuthIdentityMapper identities;
  /** 只读部门名称。 */
  private final SysDeptMapper departments;
  /** 只读机构名称。 */
  private final OrganizationMapper organizations;
  /** 只读角色编码。 */
  private final SysRoleMapper roles;
  /** 用户实体及权限缓存失效。 */
  private final SysUserCacheService cache;
  /** 提交后广播既有缓存失效事件。 */
  private final CacheEvictService evictions;
  /** 既有BCrypt编码器，校验和保存传输摘要。 */
  private final PasswordEncoder passwords;

  /** 从真实用户行读取资料，禁止把脱敏手机号提交回数据库。 */
  public MyProfileVO read() {
    var user = requireSelf();
    var org = TenantContext.callWithBypass(() -> organizations.selectById(user.getOrganizationId()));
    var dept = user.getDeptId() == null ? null : TenantContext.callWithBypass(() -> departments.selectById(user.getDeptId()));
    boolean local = identity(user.getUserId(), false) != null;
    return new MyProfileVO(user.getUserCode(), user.getUserName(), user.getPhone(), user.getEmail(),
      user.getOrganizationId(), org == null ? null : org.getOrgName(), user.getDeptId(),
      dept == null ? null : dept.getDeptName(), user.getMemberType(), roles.selectRoleCodesByUserId(user.getUserId()),
      user.getVersion(), local, local && !"superAdmin".equals(user.getUserCode()));
  }

  /** 只更新允许字段，显式set空值支持清空邮箱和手机号。 */
  @Transactional
  public void update(MyProfileRequest request) {
    var user = requireSelf();
    if ("superAdmin".equals(user.getUserCode()))
      throw new BusinessException(HttpStatus.FORBIDDEN, 2017, "系统根基特权，禁止修改资料");
    var update = new SysUser(); update.setUserId(user.getUserId()); update.setVersion(request.getVersion());
    users.updateWithVersionCheck(update, new LambdaUpdateWrapper<SysUser>().eq(SysUser::getUserId, user.getUserId())
      .set(SysUser::getUserName, request.getUserName()).set(SysUser::getPhone, request.getPhone())
      .set(SysUser::getEmail, request.getEmail()));
    evictions.scheduleEvict(CacheDomain.USER, ClearScope.ENTRY, user.getUserId().toString());
    afterCommit(() -> cache.evict(user.getUserId()));
  }

  /** 凭证行锁防止并发旧密码重复通过；提交后撤销该账号全部会话。 */
  @Transactional
  public void changePassword(MyPasswordRequest request) {
    var user = requireSelf();
    if ("superAdmin".equals(user.getUserCode()))
      throw new BusinessException(HttpStatus.FORBIDDEN, 2019, "系统根基特权，禁止修改密码");
    var local = identity(user.getUserId(), true);
    if (local == null) throw new BusinessException(HttpStatus.BAD_REQUEST, 2002, "用户未绑定本地账密凭证");
    if (!passwords.matches(request.getOldPassword(), local.getCredential()))
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "旧密码不正确");
    local.setCredential(passwords.encode(request.getNewPassword()));
    identities.updateByIdWithVersionCheck(local);
    afterCommit(() -> StpUtil.logout(user.getUserId()));
  }

  /** 身份只从会话获取；根账号仅旁路读取自身，不提供任意用户参数。 */
  private SysUser requireSelf() {
    var user = TenantContext.callWithBypass(() -> users.selectById(StpUtil.getLoginIdAsLong()));
    if (user == null || (!"superAdmin".equals(user.getUserCode())
      && !Objects.equals(user.getOrganizationId(), TenantContext.requireTenantId())))
      throw new BusinessException(HttpStatus.NOT_FOUND, 2004, "用户不存在");
    return user;
  }

  /** local凭证不属于租户表，但始终限定当前本人主键。 */
  private SysAuthIdentity identity(Long userId, boolean lock) {
    var query = new LambdaQueryWrapper<SysAuthIdentity>().eq(SysAuthIdentity::getUserId, userId)
      .eq(SysAuthIdentity::getIdentityType, "local");
    if (lock) query.last("FOR UPDATE");
    return identities.selectOne(query);
  }

  /** 回滚不会失效会话；无事务的公开服务单测立即执行回调。 */
  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { action.run(); }
    });
  }
}
