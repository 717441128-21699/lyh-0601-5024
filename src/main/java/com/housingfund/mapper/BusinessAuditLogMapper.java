package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.BusinessAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BusinessAuditLogMapper extends BaseMapper<BusinessAuditLog> {

    @Select("SELECT * FROM business_audit_log WHERE business_no = #{businessNo} AND deleted = 0 ORDER BY action_time ASC")
    List<BusinessAuditLog> findByBusinessNo(@Param("businessNo") String businessNo);

    @Select("SELECT * FROM business_audit_log WHERE business_type = #{businessType} AND business_id = #{businessId} AND deleted = 0 ORDER BY action_time ASC")
    List<BusinessAuditLog> findByBusiness(@Param("businessType") String businessType, @Param("businessId") Long businessId);
}
