package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repayment_plan")
public class RepaymentPlan extends BaseEntity {

    private Long loanAccountId;

    private String loanAccountNo;

    private Long employeeId;

    private Long branchId;

    private Integer termNo;

    private LocalDate dueDate;

    private BigDecimal principalAmount;

    private BigDecimal interestAmount;

    private BigDecimal totalAmount;

    private BigDecimal paidPrincipal;

    private BigDecimal paidInterest;

    private BigDecimal penaltyAmount;

    private BigDecimal paidPenalty;

    private LocalDateTime actualPayTime;

    private Integer overdueDays;

    private Integer repaymentStatus;

    private Integer reminderSent;

    private Integer status;

    private String remark;
}
