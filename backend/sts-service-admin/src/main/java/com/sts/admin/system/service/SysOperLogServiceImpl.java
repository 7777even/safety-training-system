package com.sts.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysOperLog;
import com.sts.admin.mapper.SysOperLogMapper;
import com.sts.admin.system.dto.OperLogQuery;
import com.sts.admin.system.vo.OperLogVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl
    extends BaseServiceImpl<SysOperLogMapper, SysOperLog>
    implements SysOperLogService {

    @Override
    public PageResult<OperLogVO> pageOperLogs(PageRequest<OperLogQuery> request) {
        OperLogQuery q = request.getQuery();
        LambdaQueryWrapper<SysOperLog> wrapper = Wrappers.lambdaQuery();
        if (q != null) {
            wrapper.like(StringUtils.hasText(q.getTitle()), SysOperLog::getTitle, q.getTitle())
                .like(StringUtils.hasText(q.getOperatorCode()), SysOperLog::getOperatorCode, q.getOperatorCode())
                .eq(q.getBusinessType() != null, SysOperLog::getBusinessType, q.getBusinessType())
                .eq(q.getStatus() != null, SysOperLog::getStatus, q.getStatus())
                .ge(q.getBeginTime() != null, SysOperLog::getCreateTime, q.getBeginTime())
                .le(q.getEndTime() != null, SysOperLog::getCreateTime, q.getEndTime());
        }
        wrapper.orderByDesc(SysOperLog::getCreateTime);
        Page<SysOperLog> page = this.page(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        List<OperLogVO> list = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public OperLogVO detail(Long operLogId) {
        SysOperLog entity = this.getById(operLogId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 2050, "操作日志不存在");
        }
        return toVO(entity);
    }

    @Override
    public void batchDelete(List<Long> ids) {
        this.removeByIds(ids);
    }

    @Override
    public void cleanAll() {
        this.remove(Wrappers.emptyWrapper());
    }

    private OperLogVO toVO(SysOperLog entity) {
        OperLogVO vo = new OperLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
