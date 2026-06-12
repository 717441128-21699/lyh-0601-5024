package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum NotificationTypeEnum {

    ACCOUNT_CHANGE(1, "账户变动"),
    APPROVAL_STATUS(2, "审批状态"),
    REPAYMENT_REMINDER(3, "还款提醒"),
    OVERDUE_ALERT(4, "逾期警示"),
    SYSTEM_NOTICE(5, "系统通知"),
    COLLECTION_TASK(6, "催收任务");

    private final Integer code;
    private final String desc;

    NotificationTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
