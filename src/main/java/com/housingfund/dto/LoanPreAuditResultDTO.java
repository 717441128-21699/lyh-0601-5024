package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class LoanPreAuditResultDTO {

    private Boolean eligible;
    private Long employeeId;
    private String employeeName;
    private Integer continuousMonths;
    private Integer requiredMonths;
    private Integer creditScore;
    private String creditLevel;
    private BigDecimal maxLoanableAmount;
    private BigDecimal applicationAmount;
    private BigDecimal houseAppraisalValue;
    private BigDecimal maxLoanToValueAmount;
    private BigDecimal interestRate;
    private Integer loanTerm;
    private String preAuditReport;
    private List<String> reasons = new ArrayList<>();

    private BigDecimal riskTotalScore;
    private List<RiskScoreDimensionDTO> riskScoreDetails = new ArrayList<>();
    private BigDecimal balanceBasedMax;
    private BigDecimal ltvBasedMax;
    private BigDecimal statutoryMax;
    private BigDecimal creditMultiplier;
    private BigDecimal ageAdjustment;
    private BigDecimal baseRate;
    private String rateTrace;
}
