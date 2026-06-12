package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.entity.BusinessAuditLog;
import com.housingfund.mapper.BusinessAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessAuditLogService {

    private final BusinessAuditLogMapper auditLogMapper;

    public BusinessAuditLog log(String businessNo, String businessType, Long businessId,
                                 String actionCode, String actionName,
                                 Long operatorId, String operatorName, String operatorRole,
                                 BigDecimal amountBefore, BigDecimal amountAfter, BigDecimal amountChanged,
                                 String changeDescription, String notificationTriggered, Long notificationId,
                                 Long branchId, String remark) {
        BusinessAuditLog auditLog = new BusinessAuditLog();
        auditLog.setTraceNo("AL" + IdUtil.getSnowflakeNextIdStr());
        auditLog.setBusinessNo(businessNo);
        auditLog.setBusinessType(businessType);
        auditLog.setBusinessId(businessId);
        auditLog.setActionCode(actionCode);
        auditLog.setActionName(actionName);
        auditLog.setOperatorId(operatorId);
        auditLog.setOperatorName(operatorName);
        auditLog.setOperatorRole(operatorRole);
        auditLog.setAmountBefore(amountBefore);
        auditLog.setAmountAfter(amountAfter);
        auditLog.setAmountChanged(amountChanged);
        auditLog.setChangeDescription(changeDescription);
        auditLog.setNotificationTriggered(notificationTriggered);
        auditLog.setNotificationId(notificationId);
        auditLog.setRemark(remark);
        auditLog.setBranchId(branchId);
        auditLog.setActionTime(LocalDateTime.now());
        auditLog.setStatus(1);
        auditLogMapper.insert(auditLog);
        return auditLog;
    }

    public List<BusinessAuditLog> getTimelineByBusinessNo(String businessNo) {
        return auditLogMapper.findByBusinessNo(businessNo);
    }

    public List<BusinessAuditLog> getTimelineByBusiness(String businessType, Long businessId) {
        return auditLogMapper.findByBusiness(businessType, businessId);
    }

    public Page<BusinessAuditLog> queryAuditLogs(String businessNo, String businessType,
                                                   Long businessId, Long operatorId,
                                                   String actionCode,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   int pageNum, int pageSize) {
        LambdaQueryWrapper<BusinessAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (businessNo != null) wrapper.eq(BusinessAuditLog::getBusinessNo, businessNo);
        if (businessType != null) wrapper.eq(BusinessAuditLog::getBusinessType, businessType);
        if (businessId != null) wrapper.eq(BusinessAuditLog::getBusinessId, businessId);
        if (operatorId != null) wrapper.eq(BusinessAuditLog::getOperatorId, operatorId);
        if (actionCode != null) wrapper.eq(BusinessAuditLog::getActionCode, actionCode);
        if (startTime != null) wrapper.ge(BusinessAuditLog::getActionTime, startTime);
        if (endTime != null) wrapper.le(BusinessAuditLog::getActionTime, endTime);
        wrapper.orderByDesc(BusinessAuditLog::getActionTime);
        return auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
