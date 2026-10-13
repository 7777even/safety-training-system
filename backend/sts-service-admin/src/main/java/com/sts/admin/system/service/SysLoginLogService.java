package com.sts.admin.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sts.admin.entity.SysLoginLog;
import com.sts.admin.system.dto.LoginLogQuery;
import com.sts.admin.system.vo.LoginLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

import java.util.List;

public interface SysLoginLogService extends IService<SysLoginLog> {
    PageResult<LoginLogVO> pageLoginLogs(PageRequest<LoginLogQuery> request);

    void deleteById(Long loginLogId);

    void batchDelete(List<Long> ids);

    void cleanAll();
}
