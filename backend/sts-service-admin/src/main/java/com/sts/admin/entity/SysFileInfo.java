package com.sts.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("us_sys_file_info")
public class SysFileInfo extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long fileInfoId;
    private String fileName;
    private String bucketName;
    private String objectName;
    private Long fileSize;
    private String fileSuffix;
    private String fileUrl;
}
