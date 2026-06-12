package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_account")
public class FundAccount extends BaseEntity {

    private String accountNo;

    private String accountType;

    private Long employeeId;

    private Long companyId;

    private BigDecimal balance;

    private BigDecimal frozenAmount;

    private BigDecimal totalContribution;

    private BigDecimal totalWithdrawal;

    private BigDecimal companyContribution;

    private BigDecimal personalContribution;

    private BigDecimal interestIncome;

    private Integer status;

    private String remark;
}
