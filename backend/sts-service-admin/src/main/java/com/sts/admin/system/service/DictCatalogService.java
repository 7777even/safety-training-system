package com.sts.admin.system.service;

import com.sts.common.api.dict.DictCatalogVO;

public interface DictCatalogService {

    DictCatalogVO getCatalog(String sinceVersion);
}
