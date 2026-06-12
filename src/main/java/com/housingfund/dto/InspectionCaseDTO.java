package com.housingfund.dto;

import lombok.Data;

@Data
public class InspectionCaseDTO {

    private Long caseId;
    private String caseStatus;
    private String processingProgress;
    private Long assigneeId;
    private String assigneeName;
    private String finalConclusion;
    private String finalResult;
    private String remark;
}
