package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("loan_risk_score_detail")
public class LoanRiskScoreDetail extends BaseEntity {

    private Long loanApplicationId;

    private String applicationNo;

    private Long employeeId;

    private String dimensionCode;

    private String dimensionName;

    private BigDecimal fullScore;

    private BigDecimal actualScore;

    private BigDecimal deduction;

    private String scoreRule;

    private String deductionReason;

    private Integer sortOrder;

    private BigDecimal weight;

    private BigDecimal weightedScore;
}
