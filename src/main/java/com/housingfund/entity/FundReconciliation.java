package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_reconciliation")
public class FundReconciliation extends BaseEntity {

    private String reconNo;
    private String reconMonth;
    private Long branchId;
    private String branchName;
    private String reconType;
    private String reconTypeName;
    private BigDecimal summaryAmount;
    private BigDecimal detailAmount;
    private BigDecimal diffAmount;
    private String diffDirection;
    private Integer diffCount;
    private String autoCause;
    private String manualCause;
    private String handleStatus;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    private String handleRemark;
    private String diffDetail;
    private Integer status;
}
