package com.sts.admin.aop.event;

import com.sts.admin.entity.SysOperLog;
import org.springframework.context.ApplicationEvent;

public class OperLogEvent extends ApplicationEvent {
    private final SysOperLog log;

    public OperLogEvent(Object source, SysOperLog log) {
        super(source);
        this.log = log;
    }

    public SysOperLog getLog() {
        return log;
    }
}
