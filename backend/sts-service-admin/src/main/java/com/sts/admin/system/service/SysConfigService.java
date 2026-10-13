package com.sts.admin.system.service;

import com.sts.admin.system.dto.ConfigCreateRequest;
import com.sts.admin.system.dto.ConfigQuery;
import com.sts.admin.system.dto.ConfigUpdateRequest;
import com.sts.admin.system.vo.ConfigVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

public interface SysConfigService {

    PageResult<ConfigVO> pageConfigs(PageRequest<ConfigQuery> request);

    Long createConfig(ConfigCreateRequest request);

    void updateConfig(Long configId, ConfigUpdateRequest request);

    void deleteConfig(Long configId);

    /**
     * 刷新缓存
     */
    void clearCache();
}
