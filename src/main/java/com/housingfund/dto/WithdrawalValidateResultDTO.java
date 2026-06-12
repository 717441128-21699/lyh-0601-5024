package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class WithdrawalValidateResultDTO {

    private Boolean valid;
    private String withdrawalType;
    private String withdrawalTypeDesc;
    private Integer contributionMonths;
    private Integer requiredMonths;
    private BigDecimal accountBalance;
    private BigDecimal maxWithdrawableAmount;
    private BigDecimal applicationAmount;
    private List<String> reasons = new ArrayList<>();
}
