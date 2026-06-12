package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.ApprovalRuleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApprovalRuleConfigMapper extends BaseMapper<ApprovalRuleConfig> {

    @Select("SELECT * FROM approval_rule_config WHERE business_type = #{businessType} AND status = 1 AND deleted = 0 ORDER BY approval_level ASC")
    List<ApprovalRuleConfig> findByBusinessType(@Param("businessType") String businessType);

    @Select("SELECT * FROM approval_rule_config WHERE business_type = #{businessType} AND approval_level = #{level} AND status = 1 AND deleted = 0 LIMIT 1")
    ApprovalRuleConfig findByBusinessTypeAndLevel(@Param("businessType") String businessType, @Param("level") Integer level);
}
