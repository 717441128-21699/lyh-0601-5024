package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.FundReconciliation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FundReconciliationMapper extends BaseMapper<FundReconciliation> {

    @Select("SELECT * FROM fund_reconciliation WHERE recon_month = #{month} AND deleted = 0 ORDER BY branch_id, recon_type")
    List<FundReconciliation> findByMonth(@Param("month") String month);

    @Select("SELECT * FROM fund_reconciliation WHERE recon_month = #{month} AND branch_id = #{branchId} AND deleted = 0 ORDER BY recon_type")
    List<FundReconciliation> findByMonthAndBranch(@Param("month") String month, @Param("branchId") Long branchId);
}
