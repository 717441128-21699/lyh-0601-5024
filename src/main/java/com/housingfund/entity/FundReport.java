package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_report")
public class FundReport extends BaseEntity {

    private String reportNo;

    private LocalDate reportDate;

    private String reportType;

    private Long branchId;

    private String branchName;

    private Integer newCompanyCount;

    private Integer newEmployeeCount;

    private Integer activeCompanyCount;

    private Integer activeEmployeeCount;

    private BigDecimal monthlyContribution;

    private BigDecimal monthlyCompanyContribution;

    private BigDecimal monthlyPersonalContribution;

    private BigDecimal totalContribution;

    private BigDecimal monthlyWithdrawal;

    private BigDecimal totalWithdrawal;

    private BigDecimal monthlyLoanIssue;

    private Integer monthlyLoanCount;

    private BigDecimal totalLoanBalance;

    private Integer overdueLoanCount;

    private BigDecimal overdueLoanAmount;

    private BigDecimal overdueRate;

    private BigDecimal monthlyRepayment;

    private BigDecimal monthlyPenalty;

    private BigDecimal fundBalance;

    private Integer status;

    private String remark;
}
