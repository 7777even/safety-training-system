package com.sts.admin.system.service;

import com.sts.admin.system.dto.DictTypeCreateRequest;
import com.sts.admin.system.dto.DictTypeQuery;
import com.sts.admin.system.dto.DictTypeUpdateRequest;
import com.sts.admin.system.vo.DictTypeVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

public interface SysDictTypeService {

    PageResult<DictTypeVO> pageDictTypes(PageRequest<DictTypeQuery> request);

    Long createDictType(DictTypeCreateRequest request);

    void updateDictType(Long dictTypeId, DictTypeUpdateRequest request);

    void deleteDictType(Long dictTypeId);
}
