package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inspection_case")
public class InspectionCase extends BaseEntity {

    private String caseNo;
    private String caseTitle;
    private String caseType;
    private String caseLevel;
    private String caseStatus;
    private Long branchId;
    private String branchName;
    private Long employeeId;
    private String employeeName;
    private Long companyId;
    private String companyName;
    private String relatedBusinessNos;
    private String relatedBusinessTypes;
    private String relatedAlertNos;
    private String relatedDiffIds;
    private Integer evidenceCount;
    private BigDecimal maxAmountInvolved;
    private String caseSummary;
    private String processingProgress;
    private Long assigneeId;
    private String assigneeName;
    private LocalDateTime assignTime;
    private LocalDateTime resolveDeadline;
    private String finalConclusion;
    private String finalResult;
    private LocalDateTime closeTime;
    private String remark;
    private Integer status;
}
