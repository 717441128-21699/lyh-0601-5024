package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("business_audit_log")
public class BusinessAuditLog extends BaseEntity {

    private String traceNo;
    private String businessNo;
    private String businessType;
    private Long businessId;
    private String actionCode;
    private String actionName;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private BigDecimal amountBefore;
    private BigDecimal amountAfter;
    private BigDecimal amountChanged;
    private String changeDescription;
    private String notificationTriggered;
    private Long notificationId;
    private String remark;
    private Long branchId;
    private LocalDateTime actionTime;
    private Integer status;
}
