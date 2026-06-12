package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inspection_evidence")
public class InspectionEvidence extends BaseEntity {

    private Long caseId;
    private String caseNo;
    private String evidenceType;
    private String evidenceTypeName;
    private String evidenceSource;
    private String businessNo;
    private String businessType;
    private Long businessId;
    private String alertNo;
    private String diffId;
    private String evidenceTitle;
    private String evidenceContent;
    private BigDecimal amountInvolved;
    private LocalDateTime eventTime;
    private String operatorName;
    private Integer sortOrder;
    private Integer status;
}
