package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum RiskAlertStatusEnum {

    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    RESOLVED("RESOLVED", "已处理"),
    FALSE_POSITIVE("FALSE_POSITIVE", "误报"),
    ESCALATED("ESCALATED", "转人工复核");

    private final String code;
    private final String desc;

    RiskAlertStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
