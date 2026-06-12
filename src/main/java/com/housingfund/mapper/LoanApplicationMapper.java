package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.LoanApplication;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoanApplicationMapper extends BaseMapper<LoanApplication> {
}
