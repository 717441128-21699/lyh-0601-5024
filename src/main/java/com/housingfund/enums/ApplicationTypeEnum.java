package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum ApplicationTypeEnum {

    CONTRIBUTION("contribution", "单位缴存申报"),
    WITHDRAWAL("withdrawal", "个人提取申请"),
    LOAN("loan", "贷款申请");

    private final String code;
    private final String desc;

    ApplicationTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
