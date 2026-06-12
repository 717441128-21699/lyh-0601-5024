package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {

    @Select("SELECT * FROM approval_record WHERE business_id = #{businessId} AND business_type = #{businessType} " +
            "AND deleted = 0 ORDER BY approval_level ASC")
    List<ApprovalRecord> findByBusiness(@Param("businessId") Long businessId, @Param("businessType") String businessType);

    @Select("SELECT * FROM approval_record WHERE business_id = #{businessId} AND business_type = #{businessType} " +
            "AND approval_level = #{level} AND deleted = 0 LIMIT 1")
    ApprovalRecord findCurrentApproval(@Param("businessId") Long businessId,
                                       @Param("businessType") String businessType,
                                       @Param("level") Integer level);

    @Select("SELECT * FROM approval_record WHERE approval_result = 1 AND deadline_time <= #{now} " +
            "AND timeout_escalated = 0 AND deleted = 0")
    List<ApprovalRecord> findTimeoutApprovals(@Param("now") LocalDateTime now);

    @Update("UPDATE approval_record SET timeout_escalated = 1, escalated_to_id = #{escalatedToId}, " +
            "approval_comment = CONCAT(IFNULL(approval_comment, ''), '; 超时自动转办') WHERE id = #{id}")
    int markTimeoutEscalated(@Param("id") Long id, @Param("escalatedToId") Long escalatedToId);
}
