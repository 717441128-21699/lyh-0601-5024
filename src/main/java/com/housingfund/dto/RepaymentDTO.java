package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RepaymentDTO {

    private Long loanAccountId;

    private Long repaymentPlanId;

    private BigDecimal repaymentAmount;

    private String repaymentMethod;

    private LocalDate repaymentDate;
}
