package com.sts.admin.system.service;

import com.sts.admin.system.dto.CacheClearRequest;
import com.sts.admin.system.vo.CacheRegistryItemVO;

import java.util.List;

public interface CacheAdminService {

    List<CacheRegistryItemVO> listRegistry();

    void clear(CacheClearRequest request);
}
