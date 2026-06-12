package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collection_task")
public class CollectionTask extends BaseEntity {

    private String taskNo;

    private Long loanAccountId;

    private String loanAccountNo;

    private Long employeeId;

    private String employeeName;

    private String employeePhone;

    private Long assigneeId;

    private String assigneeName;

    private Long branchId;

    private Integer overdueDays;

    private BigDecimal overdueAmount;

    private BigDecimal overduePrincipal;

    private BigDecimal overdueInterest;

    private BigDecimal penaltyAmount;

    private Integer taskLevel;

    private Integer taskStatus;

    private LocalDateTime assignTime;

    private LocalDateTime deadlineTime;

    private LocalDateTime finishTime;

    private String collectionMethod;

    private String collectionResult;

    private Integer status;

    private String remark;
}
