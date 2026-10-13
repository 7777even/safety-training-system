package com.sts.admin.system.vo;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Translatable
public class RoleVO {
    private Long roleId;
    private Long organizationId;

    /**
     * 所属租户名称，由展示翻译按 organizationId 回填；租户主数据即机构。
     */
    @TranslateField(source = "organizationId", type = "ORG_SERVICE")
    private String orgName;

    private String roleName;
    private String roleCode;
    private String roleLevel;
    private Integer isBuiltin;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private Integer version;
    private List<Long> menuIds;
}
