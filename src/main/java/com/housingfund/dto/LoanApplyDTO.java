package com.housingfund.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApplyDTO {

    @NotNull(message = "员工ID不能为空")
    private Long employeeId;

    @NotBlank(message = "贷款类型不能为空")
    private String loanType;

    @NotNull(message = "申请金额不能为空")
    @DecimalMin(value = "10000", message = "贷款金额最低1万元")
    private BigDecimal applicationAmount;

    @NotNull(message = "贷款期限不能为空")
    @Min(value = 1, message = "贷款期限至少1年")
    @Max(value = 30, message = "贷款期限最长30年")
    private Integer loanTerm;

    @NotNull(message = "房屋评估价不能为空")
    @DecimalMin(value = "0.01", message = "房屋评估价必须大于0")
    private BigDecimal houseAppraisalValue;

    private BigDecimal downPayment;

    private String repaymentMethod;

    private String houseAddress;

    private String houseType;

    private String guaranteeType;

    private String remark;
}
