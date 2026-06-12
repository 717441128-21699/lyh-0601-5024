package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.InspectionEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InspectionEvidenceMapper extends BaseMapper<InspectionEvidence> {

    @Select("SELECT * FROM inspection_evidence WHERE case_id = #{caseId} AND deleted = 0 ORDER BY sort_order ASC")
    List<InspectionEvidence> findByCaseId(@Param("caseId") Long caseId);
}
