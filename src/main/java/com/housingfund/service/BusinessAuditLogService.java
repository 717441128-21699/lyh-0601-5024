package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.entity.BusinessAuditLog;
import com.housingfund.mapper.BusinessAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public Page<BusinessAuditLog> queryAuditLogsForAudit(
            Long branchId, String businessType, String actionCode,
            Long operatorId, String operatorRole,
            BigDecimal minAmount, BigDecimal maxAmount,
            LocalDateTime startTime, LocalDateTime endTime,
            int pageNum, int pageSize) {
        LambdaQueryWrapper<BusinessAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (branchId != null) wrapper.eq(BusinessAuditLog::getBranchId, branchId);
        if (businessType != null) wrapper.eq(BusinessAuditLog::getBusinessType, businessType);
        if (actionCode != null) wrapper.eq(BusinessAuditLog::getActionCode, actionCode);
        if (operatorId != null) wrapper.eq(BusinessAuditLog::getOperatorId, operatorId);
        if (operatorRole != null) wrapper.eq(BusinessAuditLog::getOperatorRole, operatorRole);
        if (minAmount != null) wrapper.ge(BusinessAuditLog::getAmountChanged, minAmount);
        if (maxAmount != null) wrapper.le(BusinessAuditLog::getAmountChanged, maxAmount);
        if (startTime != null) wrapper.ge(BusinessAuditLog::getActionTime, startTime);
        if (endTime != null) wrapper.le(BusinessAuditLog::getActionTime, endTime);
        wrapper.orderByDesc(BusinessAuditLog::getActionTime);
        return auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public byte[] exportAuditLogsForAudit(
            Long branchId, String businessType, String actionCode,
            Long operatorId, String operatorRole,
            BigDecimal minAmount, BigDecimal maxAmount,
            LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<BusinessAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (branchId != null) wrapper.eq(BusinessAuditLog::getBranchId, branchId);
        if (businessType != null) wrapper.eq(BusinessAuditLog::getBusinessType, businessType);
        if (actionCode != null) wrapper.eq(BusinessAuditLog::getActionCode, actionCode);
        if (operatorId != null) wrapper.eq(BusinessAuditLog::getOperatorId, operatorId);
        if (operatorRole != null) wrapper.eq(BusinessAuditLog::getOperatorRole, operatorRole);
        if (minAmount != null) wrapper.ge(BusinessAuditLog::getAmountChanged, minAmount);
        if (maxAmount != null) wrapper.le(BusinessAuditLog::getAmountChanged, maxAmount);
        if (startTime != null) wrapper.ge(BusinessAuditLog::getActionTime, startTime);
        if (endTime != null) wrapper.le(BusinessAuditLog::getActionTime, endTime);
        wrapper.orderByDesc(BusinessAuditLog::getActionTime);
        wrapper.last("LIMIT 10000");

        List<BusinessAuditLog> logs = auditLogMapper.selectList(wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BusinessAuditLog l : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("追踪号", l.getTraceNo());
            row.put("业务单号", l.getBusinessNo());
            row.put("业务类型", mapBusinessType(l.getBusinessType()));
            row.put("动作名称", l.getActionName());
            row.put("操作人", l.getOperatorName());
            row.put("操作人角色", l.getOperatorRole() != null ? l.getOperatorRole() : "");
            row.put("操作前金额(元)", formatAmount(l.getAmountBefore()));
            row.put("操作后金额(元)", formatAmount(l.getAmountAfter()));
            row.put("变化金额(元)", formatAmount(l.getAmountChanged()));
            row.put("变化描述", l.getChangeDescription() != null ? l.getChangeDescription() : "");
            row.put("是否触发通知", l.getNotificationTriggered() != null ? l.getNotificationTriggered() : "");
            row.put("通知ID", l.getNotificationId() != null ? l.getNotificationId() : "");
            row.put("所属机构", l.getBranchId() != null ? String.valueOf(l.getBranchId()) : "");
            row.put("操作时间", l.getActionTime());
            row.put("备注", l.getRemark() != null ? l.getRemark() : "");
            rows.add(row);
        }

        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(rows, true);
        writer.autoSizeColumnAll();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.flush(out, true);
        writer.close();
        return out.toByteArray();
    }

    private String mapBusinessType(String type) {
        if (type == null) return "未知";
        return switch (type) {
            case "contribution" -> "缴存申报";
            case "withdrawal" -> "提取申请";
            case "loan" -> "贷款申请";
            case "repayment" -> "还款";
            case "approval" -> "审批";
            default -> type;
        };
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
