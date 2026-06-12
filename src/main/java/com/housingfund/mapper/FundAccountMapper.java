package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.FundAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface FundAccountMapper extends BaseMapper<FundAccount> {

    @Update("UPDATE fund_account SET balance = balance + #{amount}, total_contribution = total_contribution + #{amount}, " +
            "company_contribution = company_contribution + #{companyAmount}, personal_contribution = personal_contribution + #{personalAmount}, " +
            "update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int increaseContribution(@Param("id") Long id, @Param("amount") BigDecimal amount,
                             @Param("companyAmount") BigDecimal companyAmount, @Param("personalAmount") BigDecimal personalAmount);

    @Update("UPDATE fund_account SET balance = balance - #{amount}, total_withdrawal = total_withdrawal + #{amount}, " +
            "update_time = NOW() WHERE id = #{id} AND balance >= #{amount} AND deleted = 0")
    int decreaseWithdrawal(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE fund_account SET frozen_amount = frozen_amount + #{amount}, update_time = NOW() " +
            "WHERE id = #{id} AND balance - frozen_amount >= #{amount} AND deleted = 0")
    int freezeAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE fund_account SET frozen_amount = frozen_amount - #{amount}, balance = balance - #{amount}, " +
            "total_withdrawal = total_withdrawal + #{amount}, update_time = NOW() " +
            "WHERE id = #{id} AND frozen_amount >= #{amount} AND deleted = 0")
    int deductFrozenAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE fund_account SET frozen_amount = frozen_amount - #{amount}, update_time = NOW() " +
            "WHERE id = #{id} AND frozen_amount >= #{amount} AND deleted = 0")
    int unfreezeAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
