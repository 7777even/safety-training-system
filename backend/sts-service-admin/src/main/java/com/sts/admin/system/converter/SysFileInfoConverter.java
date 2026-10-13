package com.sts.admin.system.converter;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.system.vo.FileInfoVO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysFileInfoConverter {

    FileInfoVO toVO(SysFileInfo entity);
}
