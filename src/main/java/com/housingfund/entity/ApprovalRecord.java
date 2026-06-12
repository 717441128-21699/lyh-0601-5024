package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_record")
public class ApprovalRecord extends BaseEntity {

    private String approvalNo;

    private Long businessId;

    private String businessType;

    private String businessNo;

    private Long applicantId;

    private String applicantName;

    private Long approverId;

    private String approverName;

    private Long approverRoleId;

    private String approverRoleName;

    private Integer approvalLevel;

    private Integer totalLevels;

    private Integer approvalAction;

    private Integer approvalResult;

    private String approvalComment;

    private LocalDateTime submitTime;

    private LocalDateTime approvalTime;

    private LocalDateTime deadlineTime;

    private Boolean timeoutEscalated;

    private Long escalatedToId;

    private Long branchId;

    private Integer status;
}
