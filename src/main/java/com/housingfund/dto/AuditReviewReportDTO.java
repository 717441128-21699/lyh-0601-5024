package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AuditReviewReportDTO {

    private String reportNo;
    private String businessNo;
    private String businessType;
    private String businessTypeName;
    private String employeeName;
    private String employeeIdCard;
    private String branchName;
    private String companyName;
    private BigDecimal applicationAmount;
    private String approvalStatus;
    private LocalDateTime reportGenerateTime;

    private PreAuditSection preAudit;
    private ApprovalSection approval;
    private RepaymentSection repayment;
    private NotificationSection notification;
    private RiskSection risk;
    private String finalConclusion;

    @Data
    public static class PreAuditSection {
        private BigDecimal riskTotalScore;
        private List<RiskScoreItem> riskScores;
        private BigDecimal balanceBasedMax;
        private BigDecimal ltvBasedMax;
        private BigDecimal statutoryMax;
        private BigDecimal maxLoanableAmount;
        private String rateTrace;
        private String preAuditReport;
    }

    @Data
    public static class RiskScoreItem {
        private String dimensionCode;
        private String dimensionName;
        private BigDecimal fullScore;
        private BigDecimal actualScore;
        private BigDecimal deduction;
        private String deductionReason;
    }

    @Data
    public static class ApprovalSection {
        private Integer totalLevels;
        private List<ApprovalStep> steps;
        private String ruleVersion;
        private String ruleSnapshot;
        private Boolean hasTimeoutEscalation;
    }

    @Data
    public static class ApprovalStep {
        private Integer level;
        private String levelName;
        private String approverName;
        private String approverRoleName;
        private String result;
        private String comment;
        private LocalDateTime actionTime;
        private Boolean isTimeoutEscalated;
    }

    @Data
    public static class RepaymentSection {
        private BigDecimal approvedAmount;
        private BigDecimal interestRate;
        private Integer loanTermMonths;
        private String repaymentMethod;
        private BigDecimal remainingPrincipal;
        private BigDecimal totalPaid;
        private Integer paidTerms;
        private Integer remainingTerms;
        private List<RepaymentPlanItem> recentPlans;
        private List<String> exceptions;
    }

    @Data
    public static class RepaymentPlanItem {
        private Integer termNo;
        private LocalDate dueDate;
        private BigDecimal principalAmount;
        private BigDecimal interestAmount;
        private BigDecimal penaltyAmount;
        private String status;
    }

    @Data
    public static class NotificationSection {
        private Integer totalSent;
        private List<NotificationItem> items;
    }

    @Data
    public static class NotificationItem {
        private String type;
        private String typeName;
        private String title;
        private String content;
        private LocalDateTime sentTime;
        private String receiver;
    }

    @Data
    public static class RiskSection {
        private Integer alertCount;
        private List<AlertItem> alerts;
    }

    @Data
    public static class AlertItem {
        private String alertNo;
        private String alertType;
        private String alertTypeName;
        private String alertLevel;
        private String ruleVersion;
        private String ruleDescription;
        private String rulePublishInfo;
        private String status;
        private LocalDateTime createTime;
        private String handleResult;
    }
}
