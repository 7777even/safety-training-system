package com.sts.admin.system.vo;

import java.util.List;

/**
 * 本人编辑响应不使用管理UserVO的手机号脱敏规则。
 * @param userCode 只读账号
 * @param userName 姓名
 * @param phone 本人真实手机号
 * @param email 邮箱
 * @param organizationId 只读机构标识
 * @param orgName 只读机构名称
 * @param deptId 只读部门标识
 * @param deptName 只读部门名称
 * @param memberType 只读身份标识
 * @param roles 只读角色编码
 * @param version 用户乐观锁版本
 * @param hasLocalCredential 是否绑定本地凭证
 * @param passwordChangeAllowed 是否允许本人改密
 */
public record MyProfileVO(String userCode, String userName, String phone, String email,
                          Long organizationId, String orgName, Long deptId, String deptName,
                          String memberType, List<String> roles, Integer version,
                          boolean hasLocalCredential, boolean passwordChangeAllowed) { }
