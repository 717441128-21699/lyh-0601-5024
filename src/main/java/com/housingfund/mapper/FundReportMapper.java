package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.FundReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface FundReportMapper extends BaseMapper<FundReport> {

    @Select("SELECT IFNULL(SUM(total_amount), 0) FROM contribution_detail cd " +
            "WHERE cd.contribution_month BETWEEN #{startDate} AND #{endDate} AND cd.status = 1 AND cd.deleted = 0 " +
            "AND (#{branchId} IS NULL OR cd.branch_id = #{branchId})")
    BigDecimal sumContributionByPeriodAndBranch(@Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate,
                                                 @Param("branchId") Long branchId);

    @Select("SELECT IFNULL(SUM(approved_amount), 0) FROM withdrawal_application wa " +
            "WHERE wa.approval_time BETWEEN #{startDate} AND #{endDate} AND wa.approval_status = 2 AND wa.deleted = 0 " +
            "AND (#{branchId} IS NULL OR wa.branch_id = #{branchId})")
    BigDecimal sumWithdrawalByPeriodAndBranch(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("branchId") Long branchId);

    @Select("SELECT IFNULL(SUM(approved_amount), 0), IFNULL(COUNT(*), 0) FROM loan_application la " +
            "WHERE la.approval_time BETWEEN #{startDate} AND #{endDate} AND la.approval_status = 2 AND la.deleted = 0 " +
            "AND (#{branchId} IS NULL OR la.branch_id = #{branchId})")
    java.util.Map<String, Object> sumLoanByPeriodAndBranch(@Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate,
                                                           @Param("branchId") Long branchId);
}
