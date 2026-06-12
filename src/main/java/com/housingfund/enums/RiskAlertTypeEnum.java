package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum RiskAlertTypeEnum {

    LOW_RISK_SCORE("LOW_RISK_SCORE", "预审评分过低", "HIGH"),
    DEBT_ABNORMAL("DEBT_ABNORMAL", "负债异常", "HIGH"),
    OVERDUE_RISING("OVERDUE_RISING", "逾期率上升", "MEDIUM"),
    EARLY_REPAYMENT_ABNORMAL("EARLY_REPAYMENT_ABNORMAL", "提前还款异常", "MEDIUM");

    private final String code;
    private final String desc;
    private final String defaultLevel;

    RiskAlertTypeEnum(String code, String desc, String defaultLevel) {
        this.code = code;
        this.desc = desc;
        this.defaultLevel = defaultLevel;
    }
}
