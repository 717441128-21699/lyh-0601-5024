package com.housingfund.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawalApplyDTO {

    @NotNull(message = "员工ID不能为空")
    private Long employeeId;

    @NotBlank(message = "提取类型不能为空")
    private String withdrawalType;

    @NotNull(message = "提取金额不能为空")
    @DecimalMin(value = "0.01", message = "提取金额必须大于0")
    private BigDecimal applicationAmount;

    private String bankAccount;

    private String bankName;

    private String supportMaterials;

    private String remark;
}
