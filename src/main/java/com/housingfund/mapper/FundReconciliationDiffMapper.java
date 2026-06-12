package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.FundReconciliationDiff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FundReconciliationDiffMapper extends BaseMapper<FundReconciliationDiff> {

    @Select("SELECT * FROM fund_reconciliation_diff WHERE recon_id = #{reconId} AND deleted = 0 ORDER BY diff_amount DESC")
    List<FundReconciliationDiff> findByReconId(@Param("reconId") Long reconId);
}
