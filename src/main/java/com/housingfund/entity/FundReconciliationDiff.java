package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_reconciliation_diff")
public class FundReconciliationDiff extends BaseEntity {

    private Long reconId;
    private String reconNo;
    private String reconMonth;
    private Long branchId;
    private String diffType;
    private String businessNo;
    private String businessType;
    private Long businessId;
    private BigDecimal summaryAmount;
    private BigDecimal detailAmount;
    private BigDecimal diffAmount;
    private String diffReason;
    private String handleStatus;
    private String handleRemark;
    private LocalDate businessDate;
    private Integer status;
}
