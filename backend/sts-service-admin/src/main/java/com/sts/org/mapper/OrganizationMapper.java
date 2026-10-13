package com.sts.org.mapper;

import com.sts.admin.mapper.AppBaseMapper;
import com.sts.org.entity.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrganizationMapper extends AppBaseMapper<Organization> {

  @Select("SELECT * FROM sts_org_organization WHERE org_code = #{orgCode} AND deleted = 0")
  Organization selectByOrgCode(@Param("orgCode") String orgCode);
}
