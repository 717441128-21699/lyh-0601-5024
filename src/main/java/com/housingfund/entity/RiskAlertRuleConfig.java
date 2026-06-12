package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_alert_rule_config")
public class RiskAlertRuleConfig extends BaseEntity {

    private String alertType;
    private String alertTypeName;
    private String ruleCode;
    private String ruleName;
    private BigDecimal thresholdValue;
    private BigDecimal secondaryThreshold;
    private String comparisonOperator;
    private String alertLevel;
    private String ruleDescription;
    private LocalDateTime effectiveTime;
    private LocalDateTime expiryTime;
    private String ruleVersion;
    private Integer sortOrder;
    private Integer status;
    private String publishStatus;
    private Long publisherId;
    private String publisherName;
    private LocalDateTime publishTime;
    private LocalDateTime draftTime;
    private String changeLog;
}
