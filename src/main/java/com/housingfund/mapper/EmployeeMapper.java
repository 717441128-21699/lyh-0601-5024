package com.housingfund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housingfund.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(employee_no, 3) AS UNSIGNED)), 0) FROM sys_employee WHERE employee_no LIKE CONCAT(#{prefix}, '%')")
    Integer getMaxEmployeeNoSeq(@Param("prefix") String prefix);
}
