package com.housingfund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RiskAlertHandleDTO {

    private Long alertId;

    @NotBlank(message = "处理结果不能为空")
    private String handleResult;

    private String handleRemark;
}
