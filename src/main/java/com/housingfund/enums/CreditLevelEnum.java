package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum CreditLevelEnum {

    EXCELLENT("excellent", "优秀", 1.0, 750),
    GOOD("good", "良好", 0.95, 650),
    AVERAGE("average", "一般", 0.9, 550),
    POOR("poor", "较差", 0.0, 0);

    private final String code;
    private final String desc;
    private final Double multiplier;
    private final Integer minScore;

    CreditLevelEnum(String code, String desc, Double multiplier, Integer minScore) {
        this.code = code;
        this.desc = desc;
        this.multiplier = multiplier;
        this.minScore = minScore;
    }

    public static CreditLevelEnum getByScore(Integer score) {
        if (score >= EXCELLENT.minScore) return EXCELLENT;
        if (score >= GOOD.minScore) return GOOD;
        if (score >= AVERAGE.minScore) return AVERAGE;
        return POOR;
    }
}
