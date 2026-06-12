package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.RepaymentPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RepaymentPlanMapper extends BaseMapper<RepaymentPlan> {

    @Select("SELECT * FROM repayment_plan WHERE loan_account_id = #{loanAccountId} AND due_date = #{dueDate} " +
            "AND repayment_status IN (0, 3) AND deleted = 0 ORDER BY term_no LIMIT 1")
    RepaymentPlan findDuePlan(@Param("loanAccountId") Long loanAccountId, @Param("dueDate") LocalDate dueDate);

    @Select("SELECT * FROM repayment_plan WHERE loan_account_id = #{loanAccountId} AND due_date <= #{today} " +
            "AND repayment_status IN (0, 3, 4) AND deleted = 0 ORDER BY term_no")
    List<RepaymentPlan> findOverduePlans(@Param("loanAccountId") Long loanAccountId, @Param("today") LocalDate today);

    @Select("SELECT * FROM repayment_plan WHERE due_date = #{reminderDate} AND repayment_status = 0 " +
            "AND reminder_sent = 0 AND deleted = 0")
    List<RepaymentPlan> findPlansForReminder(@Param("reminderDate") LocalDate reminderDate);

    @Update("UPDATE repayment_plan SET reminder_sent = 1 WHERE id = #{id}")
    int markReminderSent(@Param("id") Long id);
}
