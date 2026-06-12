package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.config.HousingFundConfig;
import com.housingfund.dto.NotificationQueryDTO;
import com.housingfund.entity.NotificationRecord;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.mapper.NotificationRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRecordMapper notificationRecordMapper;
    private final HousingFundConfig housingFundConfig;
    private final WebSocketPushService webSocketPushService;

    @Transactional(rollbackFor = Exception.class)
    public NotificationRecord pushNotification(Long receiverId, String receiverType, String receiverName,
                                               String receiverPhone, NotificationTypeEnum type,
                                               String title, String content, Long businessId,
                                               String businessType, String businessNo,
                                               Long companyId, Long branchId) {
        if (!housingFundConfig.getNotification().getEnabled()) {
            log.info("通知推送已关闭，跳过: receiverId={}, type={}", receiverId, type.getDesc());
            return null;
        }

        NotificationRecord record = new NotificationRecord();
        record.setNotificationNo("NOT" + IdUtil.getSnowflakeNextIdStr());
        record.setNotificationType(type.getCode());
        record.setNotificationTypeDesc(type.getDesc());
        record.setReceiverId(receiverId);
        record.setReceiverType(receiverType);
        record.setReceiverName(receiverName);
        record.setReceiverPhone(receiverPhone);
        record.setCompanyId(companyId);
        record.setBranchId(branchId);
        record.setTitle(title);
        record.setContent(content);
        record.setBusinessId(businessId);
        record.setBusinessType(businessType);
        record.setBusinessNo(businessNo);
        record.setPushChannel("WEBSOCKET,SMS,APP");
        record.setPushTime(LocalDateTime.now());
        record.setPushStatus(1);
        record.setReadStatus(0);
        record.setRetryCount(0);
        record.setStatus(1);

        notificationRecordMapper.insert(record);

        try {
            webSocketPushService.pushToUser(receiverId, receiverType, record);
            log.info("通知推送成功: receiverId={}, type={}, title={}", receiverId, type.getDesc(), title);
        } catch (Exception e) {
            log.error("WebSocket推送失败，记录保留: {}", e.getMessage());
            record.setPushStatus(2);
            record.setFailReason(e.getMessage());
            notificationRecordMapper.updateById(record);
        }

        return record;
    }

    public Page<NotificationRecord> queryNotifications(NotificationQueryDTO query) {
        LambdaQueryWrapper<NotificationRecord> wrapper = new LambdaQueryWrapper<>();
        if (query.getReceiverId() != null) {
            wrapper.eq(NotificationRecord::getReceiverId, query.getReceiverId());
        }
        if (query.getReceiverType() != null) {
            wrapper.eq(NotificationRecord::getReceiverType, query.getReceiverType());
        }
        if (query.getNotificationType() != null) {
            wrapper.eq(NotificationRecord::getNotificationType, query.getNotificationType());
        }
        if (query.getReadStatus() != null) {
            wrapper.eq(NotificationRecord::getReadStatus, query.getReadStatus());
        }
        wrapper.orderByDesc(NotificationRecord::getCreateTime);

        Page<NotificationRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        return notificationRecordMapper.selectPage(page, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(Long notificationId) {
        NotificationRecord record = notificationRecordMapper.selectById(notificationId);
        if (record == null) return false;
        record.setReadStatus(1);
        record.setReadTime(LocalDateTime.now());
        return notificationRecordMapper.updateById(record) > 0;
    }

    public long countUnread(Long receiverId, String receiverType) {
        LambdaQueryWrapper<NotificationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationRecord::getReceiverId, receiverId)
                .eq(NotificationRecord::getReceiverType, receiverType)
                .eq(NotificationRecord::getReadStatus, 0);
        return notificationRecordMapper.selectCount(wrapper);
    }
}
