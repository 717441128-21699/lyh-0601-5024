package com.housingfund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.entity.RiskAlertRuleConfig;
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
public class RiskAlertRuleConfigService {

    private final RiskAlertRuleConfigMapper ruleConfigMapper;

    @Transactional(rollbackFor = Exception.class)
    public RiskAlertRuleConfig saveRule(RiskAlertRuleConfig config) {
        if (config.getId() == null) {
            ruleConfigMapper.insert(config);
        } else {
            ruleConfigMapper.updateById(config);
        }
        log.info("风险预警规则已保存: ruleCode={}, version={}", config.getRuleCode(), config.getRuleVersion());
        return config;
    }

    public Page<RiskAlertRuleConfig> queryRules(String alertType, String ruleCode, Integer status,
                                                 int pageNum, int pageSize) {
        LambdaQueryWrapper<RiskAlertRuleConfig> wrapper = new LambdaQueryWrapper<>();
        if (alertType != null) wrapper.eq(RiskAlertRuleConfig::getAlertType, alertType);
        if (ruleCode != null) wrapper.eq(RiskAlertRuleConfig::getRuleCode, ruleCode);
        if (status != null) wrapper.eq(RiskAlertRuleConfig::getStatus, status);
        wrapper.orderByDesc(RiskAlertRuleConfig::getCreateTime);
        return ruleConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public List<RiskAlertRuleConfig> getEffectiveRules(String alertType) {
        return ruleConfigMapper.findEffectiveByAlertType(alertType, LocalDateTime.now());
    }

    public RiskAlertRuleConfig getRuleByCodeAndVersion(String ruleCode, String ruleVersion) {
        return ruleConfigMapper.findByRuleCodeAndVersion(ruleCode, ruleVersion);
    }

    public RiskAlertRuleConfig getLatestRuleByCode(String ruleCode) {
        return ruleConfigMapper.findLatestByRuleCode(ruleCode);
    }

    public BigDecimal getEffectiveThreshold(String alertType) {
        List<RiskAlertRuleConfig> rules = getEffectiveRules(alertType);
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
}
