package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("withdrawal_application")
public class WithdrawalApplication extends BaseEntity {

    private String applicationNo;

    private Long employeeId;

    private String employeeNo;

    private String employeeName;

    private String idCard;

    private String phone;

    private Long companyId;

    private Long branchId;

    private String withdrawalType;

    private String withdrawalTypeDesc;

    private BigDecimal applicationAmount;

    private BigDecimal approvedAmount;

    private BigDecimal maxWithdrawableAmount;

    private Integer contributionMonths;

    private BigDecimal accountBalance;

    private Integer approvalStatus;

    private Integer currentApprovalLevel;

    private LocalDateTime submitTime;

    private LocalDateTime approvalTime;

    private String rejectReason;

    private LocalDate arrivalDate;

    private String bankAccount;

    private String bankName;

    private String supportMaterials;

    private Integer status;

    private String remark;
}
