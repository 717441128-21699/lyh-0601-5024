package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardDTO {

    private List<BranchComparison> branchComparisons;

    private List<TrendItem> contributionTrend;

    private List<TrendItem> withdrawalTrend;

    private List<TrendItem> loanTrend;

    private List<TrendItem> overdueTrend;

    private OverdueSummary overdueSummary;

    @Data
    public static class BranchComparison {
        private Long branchId;
        private String branchName;
        private BigDecimal monthlyContribution;
        private BigDecimal monthlyWithdrawal;
        private BigDecimal monthlyLoanIssue;
        private BigDecimal overdueRate;
        private BigDecimal fundBalance;
        private BigDecimal contributionYoY;
        private BigDecimal contributionMoM;
        private BigDecimal withdrawalYoY;
        private BigDecimal withdrawalMoM;
        private BigDecimal loanYoY;
        private BigDecimal loanMoM;
        private BigDecimal overdueRateYoY;
        private BigDecimal overdueRateMoM;
    }

    @Data
    public static class TrendItem {
        private String date;
        private BigDecimal contribution;
        private BigDecimal withdrawal;
        private BigDecimal loan;
        private BigDecimal overdueRate;
        private BigDecimal contributionYoY;
        private BigDecimal contributionMoM;
        private BigDecimal withdrawalYoY;
        private BigDecimal withdrawalMoM;
        private BigDecimal loanYoY;
        private BigDecimal loanMoM;
        private BigDecimal overdueRateYoY;
        private BigDecimal overdueRateMoM;
    }

    @Data
    public static class OverdueSummary {
        private Integer totalLoanCount;
        private Integer overdueLoanCount;
        private BigDecimal totalLoanBalance;
        private BigDecimal overdueLoanAmount;
        private BigDecimal overdueRate;
        private BigDecimal overdueRateYoY;
        private BigDecimal overdueRateMoM;
    }
}
