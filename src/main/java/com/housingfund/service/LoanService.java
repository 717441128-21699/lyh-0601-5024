package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.config.HousingFundConfig;
import com.housingfund.dto.LoanApplyDTO;
import com.housingfund.dto.LoanPreAuditResultDTO;
import com.housingfund.dto.RiskScoreDimensionDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.*;
import com.housingfund.mapper.EmployeeMapper;
import com.housingfund.mapper.LoanApplicationMapper;
import com.housingfund.mapper.LoanRiskScoreDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final HousingFundConfig config;
    private final LoanApplicationMapper loanApplicationMapper;
    private final EmployeeMapper employeeMapper;
    private final LoanRiskScoreDetailMapper riskScoreDetailMapper;
    private final FundAccountService fundAccountService;
    private final RepaymentService repaymentService;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;
    private final RiskAlertService riskAlertService;
    private final BusinessAuditLogService auditLogService;

    public LoanPreAuditResultDTO preAuditLoan(LoanApplyDTO dto) {
        LoanPreAuditResultDTO result = new LoanPreAuditResultDTO();
        result.setEligible(true);
        result.setEmployeeId(dto.getEmployeeId());
        result.setApplicationAmount(dto.getApplicationAmount());
        result.setHouseAppraisalValue(dto.getHouseAppraisalValue());
        result.setLoanTerm(dto.getLoanTerm());

        Employee employee = employeeMapper.selectById(dto.getEmployeeId());
        if (employee == null) {
            result.setEligible(false);
            result.getReasons().add("员工信息不存在");
            return result;
        }
        result.setEmployeeName(employee.getName());
        result.setCreditScore(employee.getCreditScore());

        HousingFundConfig.LoanConfig lc = config.getLoan();

        int continuousMonths = calculateContinuousMonths(employee);
        result.setContinuousMonths(continuousMonths);
        result.setRequiredMonths(lc.getMinContinuousMonths());

        CreditLevelEnum creditLevel = CreditLevelEnum.getByScore(employee.getCreditScore());
        result.setCreditLevel(creditLevel.getCode());

        List<RiskScoreDimensionDTO> riskDetails = new ArrayList<>();

        RiskScoreDimensionDTO contributionDim = scoreContribution(continuousMonths, lc);
        riskDetails.add(contributionDim);
        if (continuousMonths < lc.getMinContinuousMonths()) {
            result.setEligible(false);
            result.getReasons().add(String.format(
                    "连续缴存时长不足：当前连续缴存%d个月，要求至少%d个月",
                    continuousMonths, lc.getMinContinuousMonths()));
        }

        RiskScoreDimensionDTO creditDim = scoreCredit(employee.getCreditScore(), creditLevel);
        riskDetails.add(creditDim);
        if (creditLevel == CreditLevelEnum.POOR) {
            result.setEligible(false);
            result.getReasons().add(String.format(
                    "信用评分不足：当前%d分，低于贷款最低要求%d分",
                    employee.getCreditScore(), CreditLevelEnum.AVERAGE.getMinScore()));
        }

        FundAccount account = fundAccountService.getEmployeeFundAccount(dto.getEmployeeId());
        BigDecimal balance = account != null ? account.getBalance() : BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        RiskScoreDimensionDTO debtDim = scoreDebt(balance, dto.getApplicationAmount(), dto.getLoanTerm());
        riskDetails.add(debtDim);

        if (debtDim.getActualScore().compareTo(new BigDecimal("5")) <= 0
                && debtDim.getActualScore().compareTo(BigDecimal.ZERO) > 0) {
            riskAlertService.createAlert(
                    RiskAlertTypeEnum.DEBT_ABNORMAL, "LOAN_PRE_AUDIT",
                    "PRE_AUDIT_" + dto.getEmployeeId(), "loan", null,
                    dto.getEmployeeId(), employee.getName(), employee.getBranchId(),
                    "贷款申请人负债异常",
                    String.format("职工%s负债比率异常，负债评分仅%.0f分（满分20分）", employee.getName(), debtDim.getActualScore()),
                    debtDim.getActualScore(), new BigDecimal("5"));
        }

        RiskScoreDimensionDTO houseDim = scoreHouseValuation(dto.getHouseAppraisalValue(), dto.getApplicationAmount(), lc);
        riskDetails.add(houseDim);

        BigDecimal totalScore = BigDecimal.ZERO;
        for (RiskScoreDimensionDTO d : riskDetails) {
            totalScore = totalScore.add(d.getWeightedScore() != null ? d.getWeightedScore() : BigDecimal.ZERO);
        }
        result.setRiskScoreDetails(riskDetails);
        result.setRiskTotalScore(totalScore);

        if (totalScore.compareTo(new BigDecimal("60")) < 0 && totalScore.compareTo(BigDecimal.ZERO) > 0) {
            riskAlertService.createAlert(
                    RiskAlertTypeEnum.LOW_RISK_SCORE, "LOAN_PRE_AUDIT",
                    "PRE_AUDIT_" + dto.getEmployeeId(), "loan", null,
                    dto.getEmployeeId(), employee.getName(), employee.getBranchId(),
                    "贷款预审评分过低",
                    String.format("职工%s预审风控评分%.2f分，低于60分警戒线", employee.getName(), totalScore),
                    totalScore, new BigDecimal("60"));
        }

        BigDecimal balanceBasedMax = balance.multiply(new BigDecimal("15"))
                .setScale(2, RoundingMode.HALF_DOWN);
        BigDecimal ltvMax = dto.getHouseAppraisalValue().multiply(lc.getMaxLoanToValue())
                .setScale(2, RoundingMode.HALF_DOWN);
        result.setMaxLoanToValueAmount(ltvMax);
        result.setBalanceBasedMax(balanceBasedMax);
        result.setLtvBasedMax(ltvMax);
        result.setStatutoryMax(lc.getMaxLoanAmount());

        BigDecimal maxAmount = balanceBasedMax.min(ltvMax).min(lc.getMaxLoanAmount());
        BigDecimal creditMultiplier = creditLevel.getMultiplier();
        result.setCreditMultiplier(creditMultiplier);
        maxAmount = maxAmount.multiply(creditMultiplier).setScale(2, RoundingMode.HALF_DOWN);
        result.setMaxLoanableAmount(maxAmount);

        if (dto.getApplicationAmount().compareTo(maxAmount) > 0) {
            result.setEligible(false);
            result.getReasons().add(String.format(
                    "申请金额超过最高可贷额度：申请%.2f元，最高可贷%.2f元",
                    dto.getApplicationAmount(), maxAmount));
        }

        BigDecimal ageAdjustment = BigDecimal.ONE;
        if (employee.getBirthDate() != null) {
            int age = (int) ChronoUnit.YEARS.between(employee.getBirthDate(), LocalDate.now());
            if (age + dto.getLoanTerm() > 65) {
                ageAdjustment = BigDecimal.valueOf(65 - age)
                        .divide(BigDecimal.valueOf(dto.getLoanTerm()), 6, RoundingMode.HALF_UP);
                if (ageAdjustment.compareTo(BigDecimal.ZERO) < 0) ageAdjustment = BigDecimal.ZERO;
            }
        }
        result.setAgeAdjustment(ageAdjustment);
        result.setBaseRate(lc.getBaseRate());

        BigDecimal finalRate = lc.getBaseRate().multiply(creditMultiplier).multiply(ageAdjustment)
                .setScale(6, RoundingMode.HALF_UP);
        result.setInterestRate(finalRate);

        String rateTrace = String.format(
                "执行利率推算：基准利率%.4f × 信用乘数%.2f(%s) × 年龄修正%.4f = %.6f（%.2f%%）",
                lc.getBaseRate(), creditMultiplier, creditLevel.getDesc(), ageAdjustment,
                finalRate, finalRate.doubleValue() * 100);
        result.setRateTrace(rateTrace);

        result.setPreAuditReport(generatePreAuditReport(employee, result, dto));
        return result;
    }

    private RiskScoreDimensionDTO scoreContribution(int continuousMonths, HousingFundConfig.LoanConfig lc) {
        RiskScoreDimensionDTO dim = new RiskScoreDimensionDTO();
        dim.setDimensionCode("CONTRIBUTION");
        dim.setDimensionName("连续缴存");
        dim.setFullScore(new BigDecimal("30"));
        dim.setWeight(new BigDecimal("0.30"));

        int required = lc.getMinContinuousMonths();
        BigDecimal actualScore;
        String reason;

        if (continuousMonths >= 60) {
            actualScore = new BigDecimal("30");
            reason = "连续缴存≥60个月，满分";
        } else if (continuousMonths >= 36) {
            actualScore = new BigDecimal("25");
            reason = String.format("连续缴存%d个月(≥36)，扣5分", continuousMonths);
        } else if (continuousMonths >= 24) {
            actualScore = new BigDecimal("20");
            reason = String.format("连续缴存%d个月(≥24)，扣10分", continuousMonths);
        } else if (continuousMonths >= required) {
            actualScore = new BigDecimal("15");
            reason = String.format("连续缴存%d个月(刚达线%d)，扣15分", continuousMonths, required);
        } else {
            actualScore = BigDecimal.ZERO;
            reason = String.format("连续缴存%d个月，未达要求%d个月，扣30分（一票否决）", continuousMonths, required);
        }

        dim.setActualScore(actualScore);
        dim.setDeduction(dim.getFullScore().subtract(actualScore));
        dim.setDeductionReason(reason);
        dim.setWeightedScore(actualScore.multiply(dim.getWeight()).setScale(2, RoundingMode.HALF_UP));
        return dim;
    }

    private RiskScoreDimensionDTO scoreCredit(Integer creditScore, CreditLevelEnum creditLevel) {
        RiskScoreDimensionDTO dim = new RiskScoreDimensionDTO();
        dim.setDimensionCode("CREDIT");
        dim.setDimensionName("信用记录");
        dim.setFullScore(new BigDecimal("30"));
        dim.setWeight(new BigDecimal("0.30"));

        BigDecimal actualScore;
        String reason;

        if (creditScore >= 780) {
            actualScore = new BigDecimal("30");
            reason = String.format("信用评分%d(优秀≥780)，满分", creditScore);
        } else if (creditScore >= 720) {
            actualScore = new BigDecimal("25");
            reason = String.format("信用评分%d(良好≥720)，扣5分", creditScore);
        } else if (creditScore >= 650) {
            actualScore = new BigDecimal("18");
            reason = String.format("信用评分%d(一般≥650)，扣12分", creditScore);
        } else if (creditScore >= 550) {
            actualScore = new BigDecimal("8");
            reason = String.format("信用评分%d(较差≥550)，扣22分", creditScore);
        } else {
            actualScore = BigDecimal.ZERO;
            reason = String.format("信用评分%d(极差<550)，扣30分（一票否决）", creditScore);
        }

        dim.setActualScore(actualScore);
        dim.setDeduction(dim.getFullScore().subtract(actualScore));
        dim.setDeductionReason(reason);
        dim.setWeightedScore(actualScore.multiply(dim.getWeight()).setScale(2, RoundingMode.HALF_UP));
        return dim;
    }

    private RiskScoreDimensionDTO scoreDebt(BigDecimal balance, BigDecimal loanAmount, int loanTerm) {
        RiskScoreDimensionDTO dim = new RiskScoreDimensionDTO();
        dim.setDimensionCode("DEBT");
        dim.setDimensionName("负债情况");
        dim.setFullScore(new BigDecimal("20"));
        dim.setWeight(new BigDecimal("0.20"));

        BigDecimal monthlyRepaymentCap = balance.divide(BigDecimal.valueOf(loanTerm), 2, RoundingMode.HALF_UP);
        BigDecimal debtRatio = BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            debtRatio = loanAmount.divide(balance.multiply(new BigDecimal("15")), 4, RoundingMode.HALF_UP);
        }

        BigDecimal actualScore;
        String reason;

        if (debtRatio.compareTo(new BigDecimal("0.5")) <= 0) {
            actualScore = new BigDecimal("20");
            reason = String.format("负债比率%.1f%%（≤50%%），余额充足，满分", debtRatio.doubleValue() * 100);
        } else if (debtRatio.compareTo(new BigDecimal("0.7")) <= 0) {
            actualScore = new BigDecimal("15");
            reason = String.format("负债比率%.1f%%（50%%~70%%），扣5分", debtRatio.doubleValue() * 100);
        } else if (debtRatio.compareTo(BigDecimal.ONE) <= 0) {
            actualScore = new BigDecimal("8");
            reason = String.format("负债比率%.1f%%（70%%~100%%），扣12分", debtRatio.doubleValue() * 100);
        } else {
            actualScore = new BigDecimal("2");
            reason = String.format("负债比率%.1f%%（>100%%），扣18分，偿债压力较大", debtRatio.doubleValue() * 100);
        }

        dim.setActualScore(actualScore);
        dim.setDeduction(dim.getFullScore().subtract(actualScore));
        dim.setDeductionReason(reason);
        dim.setWeightedScore(actualScore.multiply(dim.getWeight()).setScale(2, RoundingMode.HALF_UP));
        return dim;
    }

    private RiskScoreDimensionDTO scoreHouseValuation(BigDecimal houseValue, BigDecimal loanAmount,
                                                      HousingFundConfig.LoanConfig lc) {
        RiskScoreDimensionDTO dim = new RiskScoreDimensionDTO();
        dim.setDimensionCode("HOUSE_VALUATION");
        dim.setDimensionName("房屋估值");
        dim.setFullScore(new BigDecimal("20"));
        dim.setWeight(new BigDecimal("0.20"));

        BigDecimal ltv = BigDecimal.ZERO;
        if (houseValue.compareTo(BigDecimal.ZERO) > 0) {
            ltv = loanAmount.divide(houseValue, 4, RoundingMode.HALF_UP);
        }

        BigDecimal actualScore;
        String reason;

        if (ltv.compareTo(new BigDecimal("0.5")) <= 0) {
            actualScore = new BigDecimal("20");
            reason = String.format("贷款价值比%.1f%%（≤50%%），抵押充足，满分", ltv.doubleValue() * 100);
        } else if (ltv.compareTo(lc.getMaxLoanToValue()) <= 0) {
            actualScore = new BigDecimal("15");
            reason = String.format("贷款价值比%.1f%%（合规范围内≤%.0f%%），扣5分",
                    ltv.doubleValue() * 100, lc.getMaxLoanToValue().doubleValue() * 100);
        } else if (ltv.compareTo(new BigDecimal("0.8")) <= 0) {
            actualScore = new BigDecimal("8");
            reason = String.format("贷款价值比%.1f%%（超标70%%~80%%），扣12分", ltv.doubleValue() * 100);
        } else {
            actualScore = new BigDecimal("2");
            reason = String.format("贷款价值比%.1f%%（>80%%），扣18分，抵押物覆盖不足", ltv.doubleValue() * 100);
        }

        dim.setActualScore(actualScore);
        dim.setDeduction(dim.getFullScore().subtract(actualScore));
        dim.setDeductionReason(reason);
        dim.setWeightedScore(actualScore.multiply(dim.getWeight()).setScale(2, RoundingMode.HALF_UP));
        return dim;
    }

    private int calculateContinuousMonths(Employee employee) {
        if (employee.getLastContributionDate() == null || employee.getFirstContributionDate() == null) {
            return 0;
        }
        LocalDate now = LocalDate.now();
        LocalDate lastMonth = employee.getLastContributionDate();
        long monthsBetween = ChronoUnit.MONTHS.between(lastMonth, now);
        if (monthsBetween > 1) {
            return 0;
        }
        return employee.getContinuousMonths() != null ? employee.getContinuousMonths() : 0;
    }

    private String generatePreAuditReport(Employee employee, LoanPreAuditResultDTO r, LoanApplyDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== 公积金贷款预审报告 ==========\n");
        sb.append(String.format("申请人：%s（%s）\n", employee.getName(), employee.getIdCard()));
        sb.append(String.format("申请时间：%s\n", LocalDateTime.now()));
        sb.append("----------------------------------------\n");

        sb.append("【一、风控评分明细】\n");
        sb.append(String.format("  风控总分：%.2f / 100.00\n", r.getRiskTotalScore()));
        sb.append("  ┌──────────────┬──────┬──────┬──────┬────────┬────────────────────────────┐\n");
        sb.append("  │ 评分维度     │ 满分 │ 得分 │ 扣分 │ 权重   │ 扣分原因                   │\n");
        sb.append("  ├──────────────┼──────┼──────┼──────┼────────┼────────────────────────────┤\n");
        for (RiskScoreDimensionDTO d : r.getRiskScoreDetails()) {
            sb.append(String.format("  │ %-12s │ %4.0f │ %4.0f │ %4.0f │ %.0f%%    │ %-26s │\n",
                    d.getDimensionName(), d.getFullScore(), d.getActualScore(),
                    d.getDeduction(), d.getWeight().doubleValue() * 100, d.getDeductionReason()));
        }
        sb.append("  └──────────────┴──────┴──────┴──────┴────────┴────────────────────────────┘\n");
        sb.append(String.format("  ★加权总分：%.2f\n\n", r.getRiskTotalScore()));

        sb.append("【二、资格审查】\n");
        sb.append(String.format("  1. 连续缴存：%d个月（要求≥%d个月） - %s\n",
                r.getContinuousMonths(), r.getRequiredMonths(),
                r.getContinuousMonths() >= r.getRequiredMonths() ? "✓通过" : "✗不通过"));
        sb.append(String.format("  2. 信用评分：%d分（等级：%s） - %s\n",
                r.getCreditScore(), CreditLevelEnum.valueOf(r.getCreditLevel().toUpperCase()).getDesc(),
                !"poor".equals(r.getCreditLevel()) ? "✓通过" : "✗不通过"));

        sb.append("【三、额度推算过程】\n");
        sb.append(String.format("  Step1：账户余额(%.2f) × 15 = %.2f元\n",
                dto.getHouseAppraisalValue() != null ? BigDecimal.ZERO : BigDecimal.ZERO,
                r.getBalanceBasedMax()));
        sb.append(String.format("  Step2：房屋评估价(%.2f) × 成数(%.0f%%) = %.2f元\n",
                dto.getHouseAppraisalValue(), config.getLoan().getMaxLoanToValue().doubleValue() * 100, r.getLtvBasedMax()));
        sb.append(String.format("  Step3：法定上限 = %.2f元\n", r.getStatutoryMax()));
        sb.append(String.format("  Step4：三值取低 = %.2f元\n",
                r.getBalanceBasedMax().min(r.getLtvBasedMax()).min(r.getStatutoryMax())));
        sb.append(String.format("  Step5：× 信用乘数%.2f(%s) = %.2f元\n",
                r.getCreditMultiplier(),
                CreditLevelEnum.valueOf(r.getCreditLevel().toUpperCase()).getDesc(),
                r.getMaxLoanableAmount()));
        sb.append(String.format("  ★综合最高可贷额度：%.2f元\n", r.getMaxLoanableAmount()));

        sb.append("【四、利率推算过程】\n");
        sb.append(String.format("  %s\n", r.getRateTrace()));

        sb.append("【五、预审结论】\n");
        if (r.getEligible()) {
            sb.append(String.format("  ★预审通过：建议批准贷款%.2f元，期限%d年，年利率%.2f%%\n",
                    r.getMaxLoanableAmount().min(dto.getApplicationAmount()),
                    dto.getLoanTerm(), r.getInterestRate().doubleValue() * 100));
        } else {
            sb.append("  ★预审不通过，原因如下：\n");
            for (String reason : r.getReasons()) {
                sb.append(String.format("    - %s\n", reason));
            }
        }
        sb.append("----------------------------------------\n");
        sb.append("报告生成时间：").append(LocalDateTime.now()).append("\n");
        sb.append("备注：本报告仅供审批参考，最终以审批结论为准。");
        return sb.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRiskScoreDetails(Long applicationId, String applicationNo, Long employeeId,
                                    List<RiskScoreDimensionDTO> details) {
        int order = 1;
        for (RiskScoreDimensionDTO d : details) {
            LoanRiskScoreDetail entity = new LoanRiskScoreDetail();
            entity.setLoanApplicationId(applicationId);
            entity.setApplicationNo(applicationNo);
            entity.setEmployeeId(employeeId);
            entity.setDimensionCode(d.getDimensionCode());
            entity.setDimensionName(d.getDimensionName());
            entity.setFullScore(d.getFullScore());
            entity.setActualScore(d.getActualScore());
            entity.setDeduction(d.getDeduction());
            entity.setScoreRule(d.getDeductionReason());
            entity.setDeductionReason(d.getDeductionReason());
            entity.setSortOrder(order++);
            entity.setWeight(d.getWeight());
            entity.setWeightedScore(d.getWeightedScore());
            riskScoreDetailMapper.insert(entity);
        }
    }

    public List<LoanRiskScoreDetail> getRiskScoreDetails(Long applicationId) {
        LambdaQueryWrapper<LoanRiskScoreDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoanRiskScoreDetail::getLoanApplicationId, applicationId)
                .orderByAsc(LoanRiskScoreDetail::getSortOrder);
        return riskScoreDetailMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoanApplication applyLoan(LoanApplyDTO dto) {
        LoanPreAuditResultDTO preAudit = preAuditLoan(dto);
        if (!preAudit.getEligible()) {
            String reason = String.join("；", preAudit.getReasons());
            throw new BusinessException(ErrorCode.LOAN_CONTINUOUS_MONTHS_NOT_MET,
                    "贷款预审未通过：" + reason);
        }

        Employee employee = employeeMapper.selectById(dto.getEmployeeId());
        if (employee == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "员工信息不存在");

        String applicationNo = "LA" + IdUtil.getSnowflakeNextIdStr();

        LoanApplication application = new LoanApplication();
        application.setApplicationNo(applicationNo);
        application.setEmployeeId(dto.getEmployeeId());
        application.setEmployeeNo(employee.getEmployeeNo());
        application.setEmployeeName(employee.getName());
        application.setIdCard(employee.getIdCard());
        application.setPhone(employee.getPhone());
        application.setCompanyId(employee.getCompanyId());
        application.setBranchId(employee.getBranchId());
        application.setLoanType(dto.getLoanType());
        application.setApplicationAmount(dto.getApplicationAmount());
        application.setLoanTerm(dto.getLoanTerm());
        application.setHouseAppraisalValue(dto.getHouseAppraisalValue());
        application.setDownPayment(dto.getDownPayment());
        application.setMaxLoanableAmount(preAudit.getMaxLoanableAmount());
        application.setApprovedAmount(BigDecimal.ZERO);
        application.setInterestRate(preAudit.getInterestRate());
        application.setRepaymentMethod(dto.getRepaymentMethod() != null ? dto.getRepaymentMethod() : "EQUAL_INSTALLMENT");
        application.setContinuousMonths(preAudit.getContinuousMonths());
        application.setCreditScore(preAudit.getCreditScore());
        application.setCreditLevel(preAudit.getCreditLevel());
        application.setPreAuditReport(preAudit.getPreAuditReport());
        application.setApprovalStatus(ApprovalStatusEnum.PENDING.getCode());
        application.setCurrentApprovalLevel(1);
        application.setSubmitTime(LocalDateTime.now());
        application.setHouseAddress(dto.getHouseAddress());
        application.setHouseType(dto.getHouseType());
        application.setGuaranteeType(dto.getGuaranteeType());
        application.setRemark(dto.getRemark());
        application.setStatus(1);
        loanApplicationMapper.insert(application);

        saveRiskScoreDetails(application.getId(), applicationNo, employee.getId(), preAudit.getRiskScoreDetails());

        approvalService.initApprovalProcess(application.getId(), ApplicationTypeEnum.LOAN.getCode(),
                applicationNo, employee.getId(), employee.getName(), employee.getBranchId(),
                dto.getApplicationAmount());

        auditLogService.log(applicationNo, "loan", application.getId(),
                "PRE_AUDIT", "贷款预审通过",
                dto.getEmployeeId(), employee.getName(), "个人",
                BigDecimal.ZERO, preAudit.getMaxLoanableAmount(), preAudit.getMaxLoanableAmount(),
                String.format("最高可贷%.2f元，利率%.4f，风控评分%.2f",
                        preAudit.getMaxLoanableAmount(), preAudit.getInterestRate(), preAudit.getRiskTotalScore()),
                null, null, employee.getBranchId(), null);

        sendLoanNotification(employee, application, "贷款申请已提交，预审通过，等待审批");

        log.info("贷款申请提交成功: applicationNo={}, amount={}", applicationNo, dto.getApplicationAmount());
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoanAccount processLoanApproved(Long applicationId) {
        LoanApplication application = loanApplicationMapper.selectById(applicationId);
        if (application == null) return null;

        BigDecimal approvedAmount = application.getApplicationAmount().min(application.getMaxLoanableAmount());
        application.setApprovedAmount(approvedAmount);
        loanApplicationMapper.updateById(application);

        LoanAccount loanAccount = repaymentService.createLoanAccount(application);
        repaymentService.generateRepaymentPlan(loanAccount);

        auditLogService.log(application.getApplicationNo(), "loan", applicationId,
                "APPROVED", "贷款审批通过-生成还款计划",
                null, "SYSTEM", "系统",
                BigDecimal.ZERO, approvedAmount, approvedAmount,
                String.format("批准金额%.2f元，期限%d年，利率%.4f", approvedAmount, application.getLoanTerm(), application.getInterestRate()),
                "YES", null, application.getBranchId(), null);

        Employee employee = employeeMapper.selectById(application.getEmployeeId());
        sendLoanNotification(employee, application,
                String.format("贷款审批已通过！批准金额%.2f元，期限%d年，年利率%.2f%%，还款计划已生成",
                        approvedAmount, application.getLoanTerm(), application.getInterestRate().doubleValue() * 100));

        return loanAccount;
    }

    public Page<LoanApplication> queryApplications(Long employeeId, Long companyId,
                                                   Integer status, String loanType,
                                                   int pageNum, int pageSize) {
        LambdaQueryWrapper<LoanApplication> wrapper = new LambdaQueryWrapper<>();
        if (employeeId != null) wrapper.eq(LoanApplication::getEmployeeId, employeeId);
        if (companyId != null) wrapper.eq(LoanApplication::getCompanyId, companyId);
        if (status != null) wrapper.eq(LoanApplication::getApprovalStatus, status);
        if (loanType != null) wrapper.eq(LoanApplication::getLoanType, loanType);
        wrapper.orderByDesc(LoanApplication::getCreateTime);

        Page<LoanApplication> page = new Page<>(pageNum, pageSize);
        return loanApplicationMapper.selectPage(page, wrapper);
    }

    public LoanApplication getApplicationDetail(Long applicationId) {
        return loanApplicationMapper.selectById(applicationId);
    }

    private void sendLoanNotification(Employee employee, LoanApplication application, String action) {
        String title = "公积金贷款申请通知";
        String content = String.format("【%s】您好，您的%s。申请编号：%s，申请金额：%.2f元，期限：%d年",
                employee.getName(), action,
                application.getApplicationNo(),
                application.getApplicationAmount(),
                application.getLoanTerm());

        notificationService.pushNotification(
                employee.getId(), "EMPLOYEE", employee.getName(), employee.getPhone(),
                NotificationTypeEnum.APPROVAL_STATUS, title, content,
                application.getId(), ApplicationTypeEnum.LOAN.getCode(),
                application.getApplicationNo(), employee.getCompanyId(), employee.getBranchId()
        );
    }
}
