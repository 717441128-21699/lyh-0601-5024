package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_rule_config")
public class ApprovalRuleConfig extends BaseEntity {

    private String businessType;

    private String businessTypeName;

    private Integer approvalLevel;

    private String levelName;

    private Long approverRoleId;

    private String approverRoleName;

    private BigDecimal amountThreshold;

    private Boolean autoEscalation;

    private BigDecimal escalationThreshold;

    private Integer timeoutHours;

    private String escalationType;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime effectiveTime;

    private LocalDateTime expiryTime;

    private String ruleVersion;
}
