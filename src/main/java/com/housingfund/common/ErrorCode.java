package com.housingfund.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    CONTRIBUTION_BASE_INVALID(1001, "缴存基数不在合规范围内"),
    CONTRIBUTION_RATIO_INVALID(1002, "缴存比例不在合规范围内"),
    CONTRIBUTION_AMOUNT_MISMATCH(1003, "缴存金额与基数比例计算结果不符"),

    WITHDRAWAL_MIN_MONTHS_NOT_MET(2001, "缴存时长未达到提取条件要求"),
    WITHDRAWAL_INSUFFICIENT_BALANCE(2002, "账户余额不足"),
    WITHDRAWAL_AMOUNT_EXCEEDED(2003, "提取金额超过可提额度"),
    WITHDRAWAL_TYPE_INVALID(2004, "无效的提取类型"),

    LOAN_CONTINUOUS_MONTHS_NOT_MET(3001, "连续缴存时长未达到贷款要求"),
    LOAN_CREDIT_SCORE_LOW(3002, "信用评分不足，不符合贷款条件"),
    LOAN_AMOUNT_EXCEEDED(3003, "贷款申请金额超过最高可贷额度"),
    LOAN_NOT_APPROVED(3004, "贷款未通过审批"),

    APPROVAL_TIMEOUT(4001, "审批超时已转交上级"),
    APPROVAL_ALREADY_PROCESSED(4002, "该申请已处理"),
    APPROVAL_PERMISSION_DENIED(4003, "无审批权限"),

    REPAYMENT_OVERDUE(5001, "还款已逾期"),
    REPAYMENT_AMOUNT_ERROR(5002, "还款金额错误"),

    ENTITY_NOT_FOUND(6001, "数据记录不存在"),
    BUSINESS_VALIDATION_FAILED(6002, "业务校验失败");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
