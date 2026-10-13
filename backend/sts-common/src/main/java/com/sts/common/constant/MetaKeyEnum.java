package com.sts.common.constant;

public enum MetaKeyEnum {
    IOT_RULES("IOT_RULES"),
    DEVICE_BLACKLIST("DEVICE_BLACKLIST");

    private final String key;

    MetaKeyEnum(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
