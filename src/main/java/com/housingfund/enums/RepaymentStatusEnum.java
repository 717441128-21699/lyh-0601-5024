package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum RepaymentStatusEnum {

    PENDING(0, "待还款"),
    NORMAL(1, "正常"),
    PAID(2, "已结清"),
    OVERDUE(3, "逾期"),
    PARTIAL(4, "部分还款");

    private final Integer code;
    private final String desc;

    RepaymentStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
