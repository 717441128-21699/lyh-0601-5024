package com.housingfund.enums;

import lombok.Getter;

@Getter
public enum ApprovalStatusEnum {

    PENDING(0, "待审批"),
    APPROVING(1, "审批中"),
    APPROVED(2, "审批通过"),
    REJECTED(3, "审批驳回"),
    AUTO_ESCALATED(4, "超时转办"),
    CANCELLED(5, "已撤销");

    private final Integer code;
    private final String desc;

    ApprovalStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
