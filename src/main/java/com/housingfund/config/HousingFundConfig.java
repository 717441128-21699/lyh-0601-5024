package com.housingfund.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "housing-fund")
public class HousingFundConfig {

    private ContributionConfig contribution = new ContributionConfig();
    private WithdrawalConfig withdrawal = new WithdrawalConfig();
    private LoanConfig loan = new LoanConfig();
    private ApprovalConfig approval = new ApprovalConfig();
    private RepaymentConfig repayment = new RepaymentConfig();
    private NotificationConfig notification = new NotificationConfig();

    @Data
    public static class ContributionConfig {
        private Double minBase = 2360.0;
        private Double maxBase = 36549.0;
        private Double minRatio = 0.05;
        private Double maxRatio = 0.12;
    }

    @Data
    public static class WithdrawalConfig {
        private Map<String, WithdrawalCondition> conditions = new HashMap<>();
    }

    @Data
    public static class WithdrawalCondition {
        private Integer minMonths;
        private Double maxRatio;
        private Double maxMonthly;
    }

    @Data
    public static class LoanConfig {
        private Integer minContinuousMonths = 12;
        private Double maxLoanAmount = 800000.0;
        private Double baseRate = 0.031;
        private Map<String, Double> creditMultipliers = new HashMap<>();
        private Double maxLoanToValue = 0.7;
    }

    @Data
    public static class ApprovalConfig {
        private Integer timeoutHours = 4;
        private Integer levels = 3;
    }

    @Data
    public static class RepaymentConfig {
        private Integer reminderDaysBefore = 3;
        private Double penaltyRate = 0.0005;
    }

    @Data
    public static class NotificationConfig {
        private Boolean enabled = true;
    }
}
