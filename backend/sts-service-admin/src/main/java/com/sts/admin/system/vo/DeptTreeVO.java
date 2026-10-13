package com.sts.admin.system.vo;

import io.github.easytrans.core.annotation.Translatable;
import io.github.easytrans.core.annotation.TranslateField;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Translatable
public class DeptTreeVO {
    private Long deptId;
    private Long organizationId;

    /**
     * 所属机构名称，由展示翻译按 organizationId 回填。
     */
    @TranslateField(source = "organizationId", type = "ORG_SERVICE")
    private String orgName;

    private Long parentId;
    private String deptCode;
    private String deptName;
    private String deptType;
    private Integer sortOrder;
    private String leader;
    private String phone;
    private String description;
    private Integer status;
    private Integer version;
    private List<DeptTreeVO> children = new ArrayList<>();
}
