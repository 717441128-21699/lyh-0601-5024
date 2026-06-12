package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_company")
public class Company extends BaseEntity {

    private String companyCode;

    private String companyName;

    private String unifiedSocialCreditCode;

    private String legalPerson;

    private String legalPersonIdCard;

    private String contactPerson;

    private String contactPhone;

    private String address;

    private Long branchId;

    private Integer employeeCount;

    private BigDecimal contributionRatio;

    private Integer status;

    private String remark;
}
