package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_alert_event")
public class RiskAlertEvent extends BaseEntity {

    private String alertNo;
    private String alertType;
    private String alertTypeName;
    private String alertLevel;
    private String alertSource;
    private String businessNo;
    private String businessType;
    private Long businessId;
    private Long employeeId;
    private String employeeName;
    private Long branchId;
    private String alertTitle;
    private String alertContent;
    private BigDecimal alertValue;
    private BigDecimal thresholdValue;
    private String alertStatus;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    private String handleResult;
    private String handleRemark;
    private LocalDateTime resolveDeadline;
    private Integer status;
}
