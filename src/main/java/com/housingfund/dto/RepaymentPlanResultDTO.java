package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class RepaymentPlanResultDTO {

    private Long loanAccountId;
    private String loanAccountNo;
    private BigDecimal loanAmount;
    private BigDecimal interestRate;
    private Integer loanTerm;
    private String repaymentMethod;
    private BigDecimal totalRepayment;
    private BigDecimal totalInterest;
    private LocalDate firstRepaymentDate;
    private LocalDate maturityDate;
    private List<PlanItem> planItems;

    @Data
    public static class PlanItem {
        private Integer termNo;
        private LocalDate dueDate;
        private BigDecimal principalAmount;
        private BigDecimal interestAmount;
        private BigDecimal totalAmount;
        private BigDecimal remainingPrincipal;
    }
}
