package com.housingfund.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ContributionDeclarationDTO {

    private Long companyId;

    private LocalDate contributionMonth;

    private List<ContributionDetailItem> employees;

    @Data
    public static class ContributionDetailItem {
        private Long employeeId;
        private BigDecimal contributionBase;
        private BigDecimal companyRatio;
        private BigDecimal personalRatio;
        private BigDecimal companyAmount;
        private BigDecimal personalAmount;
    }
}
