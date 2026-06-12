package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EarlyRepaymentResultDTO {

    private Long loanAccountId;

    private BigDecimal earlyRepaymentAmount;

    private BigDecimal remainingPrincipalBefore;

    private BigDecimal remainingPrincipalAfter;

    private Integer remainingTermBefore;

    private Integer remainingTermAfter;

    private BigDecimal newMonthlyPayment;

    private BigDecimal oldMonthlyPayment;

    private BigDecimal savedInterest;

    private String repaymentType;

    private List<RepaymentPlanResultDTO.PlanItem> newPlanItems;
}
