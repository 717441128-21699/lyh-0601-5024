package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ContributionValidateResultDTO {

    private Boolean valid;
    private Long employeeId;
    private String employeeName;
    private BigDecimal contributionBase;
    private BigDecimal companyRatio;
    private BigDecimal personalRatio;
    private BigDecimal companyAmount;
    private BigDecimal personalAmount;
    private BigDecimal expectedCompanyAmount;
    private BigDecimal expectedPersonalAmount;
    private String errorMessage;
}
