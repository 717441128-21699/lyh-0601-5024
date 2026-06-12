package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RiskScoreDimensionDTO {

    private String dimensionCode;

    private String dimensionName;

    private BigDecimal fullScore;

    private BigDecimal actualScore;

    private BigDecimal deduction;

    private String deductionReason;

    private BigDecimal weight;

    private BigDecimal weightedScore;
}
