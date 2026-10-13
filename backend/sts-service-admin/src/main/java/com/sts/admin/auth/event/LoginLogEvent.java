package com.sts.admin.auth.event;

import com.sts.admin.entity.SysLoginLog;
import org.springframework.context.ApplicationEvent;

public class LoginLogEvent extends ApplicationEvent {

    private final SysLoginLog log;

    public LoginLogEvent(Object source, SysLoginLog log) {
        super(source);
        this.log = log;
    }

    public SysLoginLog getLog() {
        return log;
    }
}
