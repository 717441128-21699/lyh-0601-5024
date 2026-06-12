package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.config.HousingFundConfig;
import com.housingfund.dto.LoanApplyDTO;
import com.housingfund.dto.LoanPreAuditResultDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.*;
import com.housingfund.mapper.EmployeeMapper;
import com.housingfund.mapper.LoanApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final HousingFundConfig config;
    private final LoanApplicationMapper loanApplicationMapper;
    private final EmployeeMapper employeeMapper;
    private final FundAccountService fundAccountService;
    private final RepaymentService repaymentService;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;

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

        if (continuousMonths < lc.getMinContinuousMonths()) {
            result.setEligible(false);
            result.getReasons().add(String.format(
                    "连续缴存时长不足：当前连续缴存%d个月，要求至少%d个月",
                    continuousMonths, lc.getMinContinuousMonths()));
        }

        CreditLevelEnum creditLevel = CreditLevelEnum.getByScore(employee.getCreditScore());
        result.setCreditLevel(creditLevel.getCode());

        if (creditLevel == CreditLevelEnum.POOR) {
            result.setEligible(false);
            result.getReasons().add(String.format(
                    "信用评分不足：当前%d分，低于贷款最低要求%d分",
                    employee.getCreditScore(), CreditLevelEnum.AVERAGE.getMinScore()));
        }

        FundAccount account = fundAccountService.getEmployeeFundAccount(dto.getEmployeeId());
        BigDecimal balance = account != null ? account.getBalance() : BigDecimal.ZERO;

        BigDecimal balanceBasedMax = balance.multiply(new BigDecimal("15"))
                .setScale(2, RoundingMode.HALF_DOWN);
        BigDecimal ltvMax = dto.getHouseAppraisalValue().multiply(lc.getMaxLoanToValue())
                .setScale(2, RoundingMode.HALF_DOWN);
        result.setMaxLoanToValueAmount(ltvMax);

        BigDecimal maxAmount = balanceBasedMax.min(ltvMax).min(lc.getMaxLoanAmount());
        BigDecimal creditMultiplier = creditLevel.getMultiplier();
        maxAmount = maxAmount.multiply(creditMultiplier).setScale(2, RoundingMode.HALF_DOWN);
        result.setMaxLoanableAmount(maxAmount);

        if (dto.getApplicationAmount().compareTo(maxAmount) > 0) {
            result.setEligible(false);
            result.getReasons().add(String.format(
                    "申请金额超过最高可贷额度：申请%.2f元，最高可贷%.2f元",
                    dto.getApplicationAmount(), maxAmount));
        }

        BigDecimal termFactor = BigDecimal.valueOf(dto.getLoanTerm()).divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP);
        BigDecimal ageAdjustment = BigDecimal.ONE;
        if (employee.getBirthDate() != null) {
            int age = (int) ChronoUnit.YEARS.between(employee.getBirthDate(), LocalDate.now());
            if (age + dto.getLoanTerm() > 65) {
                ageAdjustment = BigDecimal.valueOf(65 - age).divide(BigDecimal.valueOf(dto.getLoanTerm()), 6, RoundingMode.HALF_UP);
                if (ageAdjustment.compareTo(BigDecimal.ZERO) < 0) ageAdjustment = BigDecimal.ZERO;
            }
        }

        BigDecimal finalRate = lc.getBaseRate().multiply(creditMultiplier).multiply(ageAdjustment)
                .setScale(6, RoundingMode.HALF_UP);
        result.setInterestRate(finalRate);

        result.setPreAuditReport(generatePreAuditReport(employee, result, dto));
        return result;
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
        sb.append("【一、资格审查】\n");
        sb.append(String.format("  1. 连续缴存：%d个月（要求≥%d个月） - %s\n",
                r.getContinuousMonths(), r.getRequiredMonths(),
                r.getContinuousMonths() >= r.getRequiredMonths() ? "通过" : "不通过"));
        sb.append(String.format("  2. 信用评分：%d分（等级：%s） - %s\n",
                r.getCreditScore(), CreditLevelEnum.valueOf(r.getCreditLevel().toUpperCase()).getDesc(),
                !"poor".equals(r.getCreditLevel()) ? "通过" : "不通过"));
        sb.append("【二、额度计算】\n");
        sb.append(String.format("  1. 账户余额倍数法：最高可贷参考\n"));
        sb.append(String.format("  2. 房屋评估价（%.2f元）× 成数（%.0f%%）= %.2f元\n",
                dto.getHouseAppraisalValue(), config.getLoan().getMaxLoanToValue() * 100, r.getMaxLoanToValueAmount()));
        sb.append(String.format("  3. 信用乘数：%s（倍数%.2f）\n",
                CreditLevelEnum.valueOf(r.getCreditLevel().toUpperCase()).getDesc(),
                CreditLevelEnum.valueOf(r.getCreditLevel().toUpperCase()).getMultiplier()));
        sb.append(String.format("  4. ★综合最高可贷额度：%.2f元\n", r.getMaxLoanableAmount()));
        sb.append("【三、利率计算】\n");
        sb.append(String.format("  基准利率：%.4f（%.2f%%）\n",
                config.getLoan().getBaseRate(), config.getLoan().getBaseRate() * 100));
        sb.append(String.format("  执行利率：%.4f（%.2f%%）\n", r.getInterestRate(), r.getInterestRate().doubleValue() * 100));
        sb.append("【四、预审结论】\n");

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

        approvalService.initApprovalProcess(application.getId(), ApplicationTypeEnum.LOAN.getCode(),
                applicationNo, employee.getId(), employee.getName(), employee.getBranchId());

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
