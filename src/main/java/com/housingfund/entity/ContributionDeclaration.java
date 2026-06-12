package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contribution_declaration")
public class ContributionDeclaration extends BaseEntity {

    private String declarationNo;

    private Long companyId;

    private String companyCode;

    private String companyName;

    private Long branchId;

    private LocalDate contributionMonth;

    private Integer employeeCount;

    private BigDecimal totalCompanyAmount;

    private BigDecimal totalPersonalAmount;

    private BigDecimal totalAmount;

    private Integer approvalStatus;

    private Integer currentApprovalLevel;

    private LocalDateTime submitTime;

    private LocalDateTime approvalTime;

    private String rejectReason;

    private Integer status;

    private String remark;
}
