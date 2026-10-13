package com.sts.admin.system.dto;

import com.sts.common.cache.CacheDomain;
import com.sts.common.cache.ClearScope;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CacheClearRequest {
    @NotNull(message = "缓存域不能为空")
    private CacheDomain domain;

    @NotNull(message = "清理范围不能为空")
    private ClearScope scope;
}
