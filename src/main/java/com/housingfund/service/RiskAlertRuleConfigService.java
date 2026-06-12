package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.entity.RiskAlertRuleConfig;
import com.housingfund.enums.RiskAlertTypeEnum;
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

    public Page<RiskAlertRuleConfig> queryRules(String alertType, String publishStatus,
                                                  Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<RiskAlertRuleConfig> w = new LambdaQueryWrapper<>();
        if (alertType != null) w.eq(RiskAlertRuleConfig::getAlertType, alertType);
        if (publishStatus != null) w.eq(RiskAlertRuleConfig::getPublishStatus, publishStatus);
        w.orderByDesc(RiskAlertRuleConfig::getCreateTime);
        return ruleConfigMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    public List<RiskAlertRuleConfig> getActivePublishedRules(LocalDateTime now) {
        return ruleConfigMapper.findActivePublishedRules(now);
    }

    public RiskAlertRuleConfig getRuleById(Long id) {
        RiskAlertRuleConfig r = ruleConfigMapper.selectById(id);
        if (r == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "规则不存在");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public RiskAlertRuleConfig createDraft(RiskAlertRuleConfig rule) {
        rule.setRuleCode("RAR" + IdUtil.getSnowflakeNextIdStr());
        rule.setRuleVersion("v1.0");
        rule.setPublishStatus("DRAFT");
        rule.setDraftTime(LocalDateTime.now());
        rule.setStatus(1);
        ruleConfigMapper.insert(rule);
        log.info("风险规则草稿已创建: ruleCode={}", rule.getRuleCode());
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public RiskAlertRuleConfig submitForRelease(Long ruleId, String changeLog, Long publisherId, String publisherName) {
        RiskAlertRuleConfig r = ruleConfigMapper.selectById(ruleId);
        if (r == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "规则不存在");
        if (!"DRAFT".equals(r.getPublishStatus()) && !"REVOKED".equals(r.getPublishStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED, "仅草稿或停用状态规则可提交发布");
        }
        r.setPublishStatus("PENDING_RELEASE");
        r.setChangeLog(changeLog);
        ruleConfigMapper.updateById(r);
        log.info("风险规则已提交待发布: ruleCode={}", r.getRuleCode());
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public RiskAlertRuleConfig publishRule(Long ruleId, Long publisherId, String publisherName) {
        RiskAlertRuleConfig r = ruleConfigMapper.selectById(ruleId);
        if (r == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "规则不存在");
        if (!"PENDING_RELEASE".equals(r.getPublishStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED, "仅待发布状态规则可发布");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<RiskAlertRuleConfig> w = new LambdaQueryWrapper<>();
        w.eq(RiskAlertRuleConfig::getAlertType, r.getAlertType())
                .eq(RiskAlertRuleConfig::getPublishStatus, "PUBLISHED");
        List<RiskAlertRuleConfig> oldRules = ruleConfigMapper.selectList(w);
        for (RiskAlertRuleConfig old : oldRules) {
            old.setPublishStatus("REVOKED");
            if (old.getEffectiveTime() == null) old.setEffectiveTime(now.minusYears(1));
            old.setExpiryTime(now);
            ruleConfigMapper.updateById(old);
        }

        r.setPublishStatus("PUBLISHED");
        r.setPublisherId(publisherId);
        r.setPublisherName(publisherName);
        r.setPublishTime(now);
        if (r.getEffectiveTime() == null) r.setEffectiveTime(now);
        if (oldRules.size() > 0) {
            RiskAlertRuleConfig latestOld = oldRules.get(0);
            int nextVer = extractVersionNumber(latestOld.getRuleVersion()) + 1;
            r.setRuleVersion("v" + nextVer + ".0");
        }
        ruleConfigMapper.updateById(r);
        log.info("风险规则已发布: ruleCode={}, version={}", r.getRuleCode(), r.getRuleVersion());
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public RiskAlertRuleConfig revokeRule(Long ruleId) {
        RiskAlertRuleConfig r = ruleConfigMapper.selectById(ruleId);
        if (r == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "规则不存在");
        r.setPublishStatus("REVOKED");
        r.setExpiryTime(LocalDateTime.now());
        ruleConfigMapper.updateById(r);
        log.info("风险规则已停用: ruleCode={}", r.getRuleCode());
        return r;
    }

    public String buildRuleSnapshot(RiskAlertRuleConfig r) {
        StringBuilder sb = new StringBuilder();
        sb.append("规则[").append(r.getRuleName()).append("]");
        sb.append("版本(").append(r.getRuleVersion() != null ? r.getRuleVersion() : "v1.0").append(")");
        sb.append("类型:").append(r.getAlertType());
        if (r.getThresholdValue() != null) sb.append("阈值:").append(r.getThresholdValue());
        if (r.getAlertLevel() != null) sb.append("等级:").append(r.getAlertLevel());
        sb.append("发布人:").append(r.getPublisherName() != null ? r.getPublisherName() : "");
        sb.append("发布时间:").append(r.getPublishTime() != null ? r.getPublishTime() : "");
        return sb.toString();
    }

    public String buildRuleDescription(RiskAlertRuleConfig r) {
        if (r.getRuleDescription() != null && !r.getRuleDescription().isEmpty()) return r.getRuleDescription();
        String type = r.getAlertType();
        return switch (type) {
            case "LOW_RISK_SCORE" -> String.format("贷款预审风控评分低于%.0分时触发预警",
                    r.getThresholdValue() != null ? r.getThresholdValue() : new BigDecimal("60"));
            case "DEBT_ABNORMAL" -> String.format("负债评分低于等于%.0分时触发预警",
                    r.getThresholdValue() != null ? r.getThresholdValue() : new BigDecimal("5"));
            case "OVERDUE_RISING" -> String.format("贷款逾期天数超过%.0天时触发预警",
                    r.getThresholdValue() != null ? r.getThresholdValue() : new BigDecimal("30"));
            case "EARLY_REPAYMENT_ABNORMAL" -> String.format("部分提前还款金额超过剩余本金的%.0f%%时触发预警",
                    r.getThresholdValue() != null ? r.getThresholdValue() : new BigDecimal("50"));
            default -> r.getRuleName() != null ? r.getRuleName() : "自定义规则";
        };
    }

    private int extractVersionNumber(String version) {
        if (version == null || version.isEmpty()) return 1;
        try {
            String num = version.replaceAll("[^0-9]", "");
            if (num.isEmpty()) return 1;
            return Integer.parseInt(num.substring(0, 1));
        } catch (Exception e) {
            return 1;
        }
    }
}
