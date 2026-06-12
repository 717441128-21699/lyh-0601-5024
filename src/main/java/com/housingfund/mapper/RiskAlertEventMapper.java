package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.RiskAlertEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RiskAlertEventMapper extends BaseMapper<RiskAlertEvent> {

    @Select("SELECT * FROM risk_alert_event WHERE alert_status = #{status} AND deleted = 0 ORDER BY create_time DESC")
    List<RiskAlertEvent> findByStatus(@Param("status") String status);

    @Select("SELECT * FROM risk_alert_event WHERE business_no = #{businessNo} AND deleted = 0 ORDER BY create_time DESC")
    List<RiskAlertEvent> findByBusinessNo(@Param("businessNo") String businessNo);
}
