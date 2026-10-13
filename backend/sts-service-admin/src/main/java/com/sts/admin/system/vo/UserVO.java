package com.sts.admin.system.vo;

import com.sts.admin.aop.sensitive.Sensitive;
import com.sts.admin.aop.sensitive.SensitiveStrategy;
import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Translatable
public class UserVO {
    private Long userId;
    private String userCode;
    private String userName;

    @Sensitive(strategy = SensitiveStrategy.PHONE)
    private String phone;
    private Integer status;
  /**
   * 成员类型：INTERNAL 内部员工 / EXTERNAL 外部人员 / REGULATOR 监管人员。
   */
  private String memberType;
  private String email;
  private String jobTitle;
  private java.time.LocalDate entryDate;
  /**
   * 所属租户 ID。
   */
  private Long organizationId;

  /**
   * 会话租户类型（REGULATOR / ENTERPRISE / TRAINING_INSTITUTION）。
   * <p>
   * 仅用户资料接口按当前登录会话回填，供前端按渠道收敛导航；用户列表不填（列表按会话租户过滤，类型恒定）。
   */
  private String orgType;

  /**
   * 所属租户名称，由展示翻译按 organizationId 回填；租户主数据即机构。
   */
  @TranslateField(source = "organizationId", type = "ORG_SERVICE")
  private String orgName;

    private Long deptId;

    @TranslateField(source = "deptId", type = "DEPT_SERVICE")
    private String deptName;

    private Long createBy;

    @TranslateField(source = "createBy", type = "USER_SERVICE")
    private String createByName;

    private LocalDateTime createTime;
    private Integer version;
    private List<Long> roleIds;
  private List<Long> tagIds;
  private List<Long> positionIds;
  private Long primaryPositionId;
    private List<String> roles;
    private List<String> perms;
    private List<MenuTreeVO> menus;
}
