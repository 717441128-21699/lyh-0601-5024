package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("account_transaction")
public class AccountTransaction extends BaseEntity {

    private String transactionNo;

    private Long fundAccountId;

    private String accountNo;

    private Long employeeId;

    private Long companyId;

    private Long branchId;

    private String transactionType;

    private String transactionDesc;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private String changeDirection;

    private Long businessId;

    private String businessType;

    private String businessNo;

    private LocalDateTime transactionTime;

    private String operator;

    private Integer status;

    private String remark;
}
