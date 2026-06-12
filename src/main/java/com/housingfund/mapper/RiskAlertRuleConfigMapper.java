package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.RiskAlertRuleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RiskAlertRuleConfigMapper extends BaseMapper<RiskAlertRuleConfig> {

    @Select("SELECT * FROM risk_alert_rule_config WHERE alert_type = #{alertType} AND status = 1 AND deleted = 0 " +
            "AND (effective_time IS NULL OR effective_time <= #{now}) " +
            "AND (expiry_time IS NULL OR expiry_time > #{now}) " +
            "ORDER BY sort_order ASC")
    List<RiskAlertRuleConfig> findEffectiveByAlertType(@Param("alertType") String alertType, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM risk_alert_rule_config WHERE rule_code = #{ruleCode} AND deleted = 0 ORDER BY effective_time DESC LIMIT 1")
    RiskAlertRuleConfig findLatestByRuleCode(@Param("ruleCode") String ruleCode);

    @Select("SELECT * FROM risk_alert_rule_config WHERE rule_code = #{ruleCode} AND rule_version = #{ruleVersion} AND deleted = 0")
    RiskAlertRuleConfig findByRuleCodeAndVersion(@Param("ruleCode") String ruleCode, @Param("ruleVersion") String ruleVersion);

    @Select("SELECT * FROM risk_alert_rule_config WHERE publish_status = 'PUBLISHED' AND status = 1 AND deleted = 0 " +
            "AND (effective_time IS NULL OR effective_time <= #{now}) " +
            "AND (expiry_time IS NULL OR expiry_time > #{now}) " +
            "ORDER BY create_time DESC")
    List<RiskAlertRuleConfig> findActivePublishedRules(@Param("now") LocalDateTime now);
}
