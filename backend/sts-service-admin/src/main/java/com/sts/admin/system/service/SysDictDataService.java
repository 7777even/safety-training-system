package com.sts.admin.system.service;

import com.sts.admin.system.dto.DictDataCreateRequest;
import com.sts.admin.system.dto.DictDataQuery;
import com.sts.admin.system.dto.DictDataUpdateRequest;
import com.sts.admin.system.vo.DictDataVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

import java.util.List;

public interface SysDictDataService {

    PageResult<DictDataVO> pageDictData(PageRequest<DictDataQuery> request);

    List<DictDataVO> listByDictType(String dictType);

    Long createDictData(DictDataCreateRequest request);

    void updateDictData(Long dictDataId, DictDataUpdateRequest request);

    void deleteDictData(Long dictDataId);
}
