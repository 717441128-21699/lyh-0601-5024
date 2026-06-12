package com.housingfund.dto;

import lombok.Data;

@Data
public class NotificationQueryDTO {

    private Long receiverId;

    private String receiverType;

    private Integer notificationType;

    private Integer readStatus;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
