package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EarlyRepaymentDTO {

    private Long loanAccountId;

    private BigDecimal repaymentAmount;

    private String repaymentType;

    private Integer reduceTermMonths;
}
