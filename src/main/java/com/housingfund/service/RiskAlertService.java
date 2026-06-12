package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.dto.RiskAlertHandleDTO;
import com.housingfund.entity.RiskAlertEvent;
import com.housingfund.entity.RiskAlertRuleConfig;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.enums.RiskAlertStatusEnum;
import com.housingfund.enums.RiskAlertTypeEnum;
import com.housingfund.mapper.RiskAlertEventMapper;
import com.housingfund.mapper.RiskAlertRuleConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAlertService {

    private final RiskAlertEventMapper alertEventMapper;
    private final NotificationService notificationService;
    private final RiskAlertRuleConfigMapper ruleConfigMapper;
    private final RiskAlertRuleConfigService riskAlertRuleConfigService;

    public BigDecimal getEffectiveThreshold(String alertType) {
        List<RiskAlertRuleConfig> rules = ruleConfigMapper.findEffectiveByAlertType(alertType, LocalDateTime.now());
        if (rules == null || rules.isEmpty()) {
            return getDefaultThreshold(alertType);
        }
        return rules.get(0).getThresholdValue();
    }

    private BigDecimal getDefaultThreshold(String alertType) {
        return switch (alertType) {
            case "LOW_RISK_SCORE" -> new BigDecimal("60");
            case "DEBT_ABNORMAL" -> new BigDecimal("5");
            case "OVERDUE_RISING" -> new BigDecimal("30");
            case "EARLY_REPAYMENT_ABNORMAL" -> new BigDecimal("0.5");
            default -> BigDecimal.ZERO;
        };
    }

    private String buildRuleSnapshot(RiskAlertRuleConfig config) {
        if (config == null) return null;
        return String.format("规则%s|阈值%.2f|比较符%s|等级%s|版本%s",
                config.getRuleCode() != null ? config.getRuleCode() : "",
                config.getThresholdValue() != null ? config.getThresholdValue() : BigDecimal.ZERO,
                config.getComparisonOperator() != null ? config.getComparisonOperator() : "<",
                config.getAlertLevel() != null ? config.getAlertLevel() : "",
                config.getRuleVersion() != null ? config.getRuleVersion() : "DEFAULT");
    }

    @Transactional(rollbackFor = Exception.class)
    public RiskAlertEvent createAlert(RiskAlertTypeEnum alertType, String alertSource,
                                      String businessNo, String businessType, Long businessId,
                                      Long employeeId, String employeeName, Long branchId,
                                      String alertTitle, String alertContent,
                                      BigDecimal alertValue, BigDecimal thresholdValue,
                                      String ruleCode, String ruleVersion) {
        RiskAlertEvent event = new RiskAlertEvent();
        event.setAlertNo("RA" + IdUtil.getSnowflakeNextIdStr());
        event.setAlertType(alertType.getCode());
        event.setAlertTypeName(alertType.getDesc());
        event.setAlertLevel(alertType.getDefaultLevel());
        event.setAlertSource(alertSource);
        event.setBusinessNo(businessNo);
        event.setBusinessType(businessType);
        event.setBusinessId(businessId);
        event.setEmployeeId(employeeId);
        event.setEmployeeName(employeeName);
        event.setBranchId(branchId);
        event.setAlertTitle(alertTitle);
        event.setAlertContent(alertContent);
        event.setAlertValue(alertValue);
        event.setThresholdValue(thresholdValue);
        event.setAlertStatus(RiskAlertStatusEnum.PENDING.getCode());
        event.setResolveDeadline(LocalDateTime.now().plusHours(24));
        event.setStatus(1);

        List<RiskAlertRuleConfig> activeRules = riskAlertRuleConfigService.getActivePublishedRules(LocalDateTime.now());
        RiskAlertRuleConfig matchedRule = null;
        for (RiskAlertRuleConfig r : activeRules) {
            if (alertType.getCode().equals(r.getAlertType())) {
                matchedRule = r;
                break;
            }
        }
        if (matchedRule != null) {
            event.setRuleVersion(matchedRule.getRuleVersion());
            event.setRuleSnapshot(riskAlertRuleConfigService.buildRuleSnapshot(matchedRule));
            event.setRulePublishInfo(String.format("发布人:%s,发布时间:%s,变更说明:%s",
                    matchedRule.getPublisherName() != null ? matchedRule.getPublisherName() : "系统",
                    matchedRule.getPublishTime() != null ? matchedRule.getPublishTime().toString() : "-",
                    matchedRule.getChangeLog() != null ? matchedRule.getChangeLog() : "-"));
            event.setRuleDescription(riskAlertRuleConfigService.buildRuleDescription(matchedRule));
            if (matchedRule.getAlertLevel() != null) {
                event.setAlertLevel(matchedRule.getAlertLevel());
            }
            if (matchedRule.getThresholdValue() != null) {
                event.setThresholdValue(matchedRule.getThresholdValue());
            }
            event.setRuleCode(matchedRule.getRuleCode());
        } else {
            RiskAlertRuleConfig config = null;
            if (ruleCode != null) {
                config = ruleConfigMapper.findByRuleCodeAndVersion(ruleCode, ruleVersion);
            }
            if (config == null) {
                List<RiskAlertRuleConfig> configs = ruleConfigMapper.findEffectiveByAlertType(alertType.getCode(), LocalDateTime.now());
                if (configs != null && !configs.isEmpty()) {
                    config = configs.get(0);
                }
            }
            event.setRuleCode(config != null ? config.getRuleCode() : null);
            event.setRuleVersion(config != null ? config.getRuleVersion() : null);
            event.setRuleSnapshot(buildRuleSnapshot(config));
        }

        alertEventMapper.insert(event);

        sendAlertNotification(event);
        log.info("风险预警事件已创建: alertNo={}, type={}, businessNo={}",
                event.getAlertNo(), alertType.getCode(), businessNo);
        return event;
    }

    private void sendAlertNotification(RiskAlertEvent event) {
        String title = "风险预警：" + event.getAlertTypeName();
        String content = String.format("预警编号%s，类型：%s，等级：%s。%s 请及时处理。",
                event.getAlertNo(), event.getAlertTypeName(), event.getAlertLevel(), event.getAlertContent());
        notificationService.pushNotification(
                event.getBranchId(), "BRANCH", "风控管理员", null,
                NotificationTypeEnum.SYSTEM_NOTICE, title, content,
                event.getId(), "RISK_ALERT", event.getAlertNo(),
                null, event.getBranchId()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public RiskAlertEvent handleAlert(RiskAlertHandleDTO dto) {
        RiskAlertEvent event = alertEventMapper.selectById(dto.getAlertId());
        if (event == null) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "预警事件不存在");
        }
        if (!RiskAlertStatusEnum.PENDING.getCode().equals(event.getAlertStatus())
                && !RiskAlertStatusEnum.PROCESSING.getCode().equals(event.getAlertStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED, "该预警事件已处理，无法重复操作");
        }

        event.setHandlerId(1L);
        event.setHandlerName("风控管理员");
        event.setHandleTime(LocalDateTime.now());
        event.setHandleResult(dto.getHandleResult());
        event.setHandleRemark(dto.getHandleRemark());

        switch (dto.getHandleResult()) {
            case "RESOLVED" -> event.setAlertStatus(RiskAlertStatusEnum.RESOLVED.getCode());
            case "FALSE_POSITIVE" -> event.setAlertStatus(RiskAlertStatusEnum.FALSE_POSITIVE.getCode());
            case "ESCALATED" -> event.setAlertStatus(RiskAlertStatusEnum.ESCALATED.getCode());
            default -> event.setAlertStatus(RiskAlertStatusEnum.PROCESSING.getCode());
        }

        alertEventMapper.updateById(event);
        log.info("风险预警已处理: alertNo={}, result={}", event.getAlertNo(), dto.getHandleResult());
        return event;
    }

    public Page<RiskAlertEvent> queryAlerts(String alertType, String alertStatus, String alertLevel,
                                              Long branchId, String businessNo,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              int pageNum, int pageSize) {
        LambdaQueryWrapper<RiskAlertEvent> wrapper = new LambdaQueryWrapper<>();
        if (alertType != null) wrapper.eq(RiskAlertEvent::getAlertType, alertType);
        if (alertStatus != null) wrapper.eq(RiskAlertEvent::getAlertStatus, alertStatus);
        if (alertLevel != null) wrapper.eq(RiskAlertEvent::getAlertLevel, alertLevel);
        if (branchId != null) wrapper.eq(RiskAlertEvent::getBranchId, branchId);
        if (businessNo != null) wrapper.eq(RiskAlertEvent::getBusinessNo, businessNo);
        if (startTime != null) wrapper.ge(RiskAlertEvent::getCreateTime, startTime);
        if (endTime != null) wrapper.le(RiskAlertEvent::getCreateTime, endTime);
        wrapper.orderByDesc(RiskAlertEvent::getCreateTime);
        return alertEventMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public List<RiskAlertEvent> getAlertsByBusinessNo(String businessNo) {
        return alertEventMapper.findByBusinessNo(businessNo);
    }

    public List<RiskAlertEvent> getPendingAlerts() {
        return alertEventMapper.findByStatus(RiskAlertStatusEnum.PENDING.getCode());
    }
}
