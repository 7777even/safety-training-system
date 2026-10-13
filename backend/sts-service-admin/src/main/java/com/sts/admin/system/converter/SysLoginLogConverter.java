package com.sts.admin.system.converter;

import com.sts.admin.entity.SysLoginLog;
import com.sts.admin.system.vo.LoginLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysLoginLogConverter {
    @Mapping(target = "userName", ignore = true)
    LoginLogVO toVO(SysLoginLog entity);
}
