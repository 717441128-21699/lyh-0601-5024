package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contribution_detail")
public class ContributionDetail extends BaseEntity {

    private Long declarationId;

    private String declarationNo;

    private Long employeeId;

    private String employeeNo;

    private String employeeName;

    private Long companyId;

    private Long branchId;

    private LocalDate contributionMonth;

    private BigDecimal contributionBase;

    private BigDecimal companyRatio;

    private BigDecimal personalRatio;

    private BigDecimal companyAmount;

    private BigDecimal personalAmount;

    private BigDecimal totalAmount;

    private String businessType;

    private Integer status;

    private String remark;
}
