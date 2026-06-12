package com.housingfund.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalActionDTO {

    @NotNull(message = "业务ID不能为空")
    private Long businessId;

    @NotNull(message = "业务类型不能为空")
    private String businessType;

    @NotNull(message = "审批层级不能为空")
    private Integer approvalLevel;

    @NotNull(message = "审批结果不能为空")
    private Integer approvalResult;

    private String approvalComment;

    private Long approverId;

    private String approverName;
}
