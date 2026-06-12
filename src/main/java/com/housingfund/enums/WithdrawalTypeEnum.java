package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum WithdrawalTypeEnum {

    PURCHASE("purchase", "购房提取", 12, 1.0),
    RENT("rent", "租房提取", 3, 1500.0),
    RETIRE("retire", "退休提取", 0, 1.0),
    SERIOUS_ILLNESS("serious_illness", "大病提取", 6, 0.9),
    DECORATION("decoration", "装修提取", 12, 0.3),
    UNEMPLOYMENT("unemployment", "失业提取", 24, 1.0);

    private final String code;
    private final String desc;
    private final Integer minMonths;
    private final Double limitValue;

    WithdrawalTypeEnum(String code, String desc, Integer minMonths, Double limitValue) {
        this.code = code;
        this.desc = desc;
        this.minMonths = minMonths;
        this.limitValue = limitValue;
    }

    public static WithdrawalTypeEnum getByCode(String code) {
        for (WithdrawalTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public boolean isRatioLimit() {
        return this == PURCHASE || this == RETIRE || this == SERIOUS_ILLNESS
                || this == DECORATION || this == UNEMPLOYMENT;
    }
}
