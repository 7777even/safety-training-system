package com.sts.admin.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sts.admin.entity.SysOperLog;
import com.sts.admin.system.dto.OperLogQuery;
import com.sts.admin.system.vo.OperLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

import java.util.List;

public interface SysOperLogService extends IService<SysOperLog> {
    PageResult<OperLogVO> pageOperLogs(PageRequest<OperLogQuery> request);

    OperLogVO detail(Long operLogId);

    void batchDelete(List<Long> ids);

    void cleanAll();
}
