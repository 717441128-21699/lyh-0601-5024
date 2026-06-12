package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_record")
public class NotificationRecord extends BaseEntity {

    private String notificationNo;

    private Integer notificationType;

    private String notificationTypeDesc;

    private Long receiverId;

    private String receiverType;

    private String receiverName;

    private String receiverPhone;

    private Long companyId;

    private Long branchId;

    private String title;

    private String content;

    private Long businessId;

    private String businessType;

    private String businessNo;

    private String pushChannel;

    private LocalDateTime pushTime;

    private Integer readStatus;

    private LocalDateTime readTime;

    private Integer pushStatus;

    private String failReason;

    private Integer retryCount;

    private Integer status;

    private String remark;
}
