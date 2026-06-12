package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("loan_account")
public class LoanAccount extends BaseEntity {

    private String loanAccountNo;

    private Long loanApplicationId;

    private String applicationNo;

    private Long employeeId;

    private Long companyId;

    private Long branchId;

    private BigDecimal loanAmount;

    private BigDecimal paidPrincipal;

    private BigDecimal paidInterest;

    private BigDecimal remainingPrincipal;

    private BigDecimal remainingInterest;

    private BigDecimal interestRate;

    private Integer loanTerm;

    private Integer paidTerm;

    private String repaymentMethod;

    private LocalDate firstRepaymentDate;

    private LocalDate lastRepaymentDate;

    private LocalDate maturityDate;

    private BigDecimal totalPenalty;

    private BigDecimal paidPenalty;

    private Integer overdueDays;

    private Integer overdueTimes;

    private Integer repaymentStatus;

    private LocalDateTime settlementTime;

    private Integer status;
}
