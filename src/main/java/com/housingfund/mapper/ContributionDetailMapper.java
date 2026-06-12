package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.ContributionDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface ContributionDetailMapper extends BaseMapper<ContributionDetail> {

    @Select("SELECT IFNULL(SUM(total_amount), 0) FROM contribution_detail WHERE employee_id = #{employeeId} " +
            "AND contribution_month BETWEEN #{startDate} AND #{endDate} AND status = 1 AND deleted = 0")
    BigDecimal sumContributionByPeriod(@Param("employeeId") Long employeeId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    @Select("SELECT COUNT(DISTINCT contribution_month) FROM contribution_detail WHERE employee_id = #{employeeId} " +
            "AND status = 1 AND deleted = 0")
    Integer countContributionMonths(@Param("employeeId") Long employeeId);

    @Select("SELECT MAX(contribution_month) FROM contribution_detail WHERE employee_id = #{employeeId} " +
            "AND status = 1 AND deleted = 0")
    LocalDate getLastContributionMonth(@Param("employeeId") Long employeeId);
}
