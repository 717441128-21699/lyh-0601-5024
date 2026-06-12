package com.housingfund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FundReportQueryDTO {

    private LocalDate startDate;

    private LocalDate endDate;

    private Long branchId;

    private String reportType;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
