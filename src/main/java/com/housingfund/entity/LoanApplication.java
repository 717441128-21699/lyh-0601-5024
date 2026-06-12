package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("loan_application")
public class LoanApplication extends BaseEntity {

    private String applicationNo;

    private Long employeeId;

    private String employeeNo;

    private String employeeName;

    private String idCard;

    private String phone;

    private Long companyId;

    private Long branchId;

    private String loanType;

    private BigDecimal applicationAmount;

    private Integer loanTerm;

    private BigDecimal houseAppraisalValue;

    private BigDecimal downPayment;

    private BigDecimal maxLoanableAmount;

    private BigDecimal approvedAmount;

    private BigDecimal interestRate;

    private String repaymentMethod;

    private Integer continuousMonths;

    private Integer creditScore;

    private String creditLevel;

    private String preAuditReport;

    private Integer approvalStatus;

    private Integer currentApprovalLevel;

    private LocalDateTime submitTime;

    private LocalDateTime approvalTime;

    private String rejectReason;

    private String houseAddress;

    private String houseType;

    private String guaranteeType;

    private Integer status;

    private String remark;
}
