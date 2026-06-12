package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_employee")
public class Employee extends BaseEntity {

    private String employeeNo;

    private String name;

    private String idCard;

    private String phone;

    private String gender;

    private LocalDate birthDate;

    private Long companyId;

    private String companyCode;

    private Long branchId;

    private BigDecimal contributionBase;

    private BigDecimal companyRatio;

    private BigDecimal personalRatio;

    private Integer contributionMonths;

    private Integer continuousMonths;

    private LocalDate firstContributionDate;

    private LocalDate lastContributionDate;

    private Integer creditScore;

    private Integer status;

    private String remark;
}
