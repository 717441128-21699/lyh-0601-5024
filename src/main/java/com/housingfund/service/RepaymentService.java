package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.config.HousingFundConfig;
import com.housingfund.dto.EarlyRepaymentDTO;
import com.housingfund.dto.EarlyRepaymentResultDTO;
import com.housingfund.dto.RepaymentDTO;
import com.housingfund.dto.RepaymentPlanResultDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.enums.RepaymentStatusEnum;
import com.housingfund.enums.RiskAlertTypeEnum;
import com.housingfund.mapper.*;
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
public class RepaymentService {

    private final HousingFundConfig config;
    private final LoanAccountMapper loanAccountMapper;
    private final RepaymentPlanMapper repaymentPlanMapper;
    private final CollectionTaskMapper collectionTaskMapper;
    private final EmployeeMapper employeeMapper;
    private final NotificationService notificationService;
    private final RiskAlertService riskAlertService;
    private final FundAccountService fundAccountService;
    private final BusinessAuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public LoanAccount createLoanAccount(LoanApplication application) {
        String loanAccountNo = "LNA" + IdUtil.getSnowflakeNextIdStr();
        LocalDate firstRepaymentDate = LocalDate.now().plusMonths(1).withDayOfMonth(20);
        LocalDate maturityDate = firstRepaymentDate.plusMonths(application.getLoanTerm() - 1L);

        LoanAccount account = new LoanAccount();
        account.setLoanAccountNo(loanAccountNo);
        account.setLoanApplicationId(application.getId());
        account.setApplicationNo(application.getApplicationNo());
        account.setEmployeeId(application.getEmployeeId());
        account.setCompanyId(application.getCompanyId());
        account.setBranchId(application.getBranchId());
        account.setLoanAmount(application.getApprovedAmount());
        account.setPaidPrincipal(BigDecimal.ZERO);
        account.setPaidInterest(BigDecimal.ZERO);
        account.setRemainingPrincipal(application.getApprovedAmount());
        BigDecimal totalInterest = calculateTotalInterestEqualInstallment(
                application.getApprovedAmount(), application.getInterestRate(), application.getLoanTerm());
        account.setRemainingInterest(totalInterest);
        account.setInterestRate(application.getInterestRate());
        account.setLoanTerm(application.getLoanTerm());
        account.setPaidTerm(0);
        account.setRepaymentMethod(application.getRepaymentMethod());
        account.setFirstRepaymentDate(firstRepaymentDate);
        account.setMaturityDate(maturityDate);
        account.setTotalPenalty(BigDecimal.ZERO);
        account.setPaidPenalty(BigDecimal.ZERO);
        account.setOverdueDays(0);
        account.setOverdueTimes(0);
        account.setRepaymentStatus(RepaymentStatusEnum.PENDING.getCode());
        account.setStatus(1);
        loanAccountMapper.insert(account);
        return account;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<RepaymentPlan> generateRepaymentPlan(LoanAccount account) {
        List<RepaymentPlan> planItems = calculateEqualInstallmentPlan(
                account.getLoanAmount(), account.getInterestRate(),
                account.getLoanTerm(), account.getFirstRepaymentDate());

        for (RepaymentPlan plan : planItems) {
            plan.setLoanAccountId(account.getId());
            plan.setLoanAccountNo(account.getLoanAccountNo());
            plan.setEmployeeId(account.getEmployeeId());
            plan.setBranchId(account.getBranchId());
            plan.setPaidPrincipal(BigDecimal.ZERO);
            plan.setPaidInterest(BigDecimal.ZERO);
            plan.setPenaltyAmount(BigDecimal.ZERO);
            plan.setPaidPenalty(BigDecimal.ZERO);
            plan.setOverdueDays(0);
            plan.setRepaymentStatus(RepaymentStatusEnum.PENDING.getCode());
            plan.setReminderSent(0);
            plan.setStatus(1);
            repaymentPlanMapper.insert(plan);
        }
        log.info("还款计划生成完成: loanAccountId={}, 共{}期", account.getId(), planItems.size());
        return planItems;
    }

    public RepaymentPlanResultDTO previewRepaymentPlan(BigDecimal loanAmount, BigDecimal annualRate,
                                                       Integer termMonths, String method, LocalDate startDate) {
        RepaymentPlanResultDTO result = new RepaymentPlanResultDTO();
        result.setLoanAmount(loanAmount);
        result.setInterestRate(annualRate);
        result.setLoanTerm(termMonths);
        result.setRepaymentMethod(method);

        LocalDate firstDate = startDate != null ? startDate : LocalDate.now().plusMonths(1).withDayOfMonth(20);
        result.setFirstRepaymentDate(firstDate);
        result.setMaturityDate(firstDate.plusMonths(termMonths - 1L));

        List<RepaymentPlan> plans;
        if ("EQUAL_PRINCIPAL".equalsIgnoreCase(method)) {
            plans = calculateEqualPrincipalPlan(loanAmount, annualRate, termMonths, firstDate);
        } else {
            plans = calculateEqualInstallmentPlan(loanAmount, annualRate, termMonths, firstDate);
        }

        BigDecimal totalRepayment = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        List<RepaymentPlanResultDTO.PlanItem> items = new ArrayList<>();
        BigDecimal remainingPrincipal = loanAmount;

        for (RepaymentPlan p : plans) {
            RepaymentPlanResultDTO.PlanItem item = new RepaymentPlanResultDTO.PlanItem();
            item.setTermNo(p.getTermNo());
            item.setDueDate(p.getDueDate());
            item.setPrincipalAmount(p.getPrincipalAmount());
            item.setInterestAmount(p.getInterestAmount());
            item.setTotalAmount(p.getTotalAmount());
            remainingPrincipal = remainingPrincipal.subtract(p.getPrincipalAmount());
            item.setRemainingPrincipal(remainingPrincipal.max(BigDecimal.ZERO));
            totalRepayment = totalRepayment.add(p.getTotalAmount());
            totalInterest = totalInterest.add(p.getInterestAmount());
            items.add(item);
        }

        result.setTotalRepayment(totalRepayment);
        result.setTotalInterest(totalInterest);
        result.setPlanItems(items);
        return result;
    }

    private List<RepaymentPlan> calculateEqualInstallmentPlan(BigDecimal principal, BigDecimal annualRate,
                                                              int months, LocalDate firstDueDate) {
        List<RepaymentPlan> plans = new ArrayList<>();
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        BigDecimal monthlyPayment;
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            monthlyPayment = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(months);
            monthlyPayment = principal.multiply(monthlyRate).multiply(factor)
                    .divide(factor.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
        }

        BigDecimal remainingPrincipal = principal;
        for (int i = 1; i <= months; i++) {
            BigDecimal interest = remainingPrincipal.multiply(monthlyRate)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalPart;

            if (i == months) {
                principalPart = remainingPrincipal;
            } else {
                principalPart = monthlyPayment.subtract(interest);
            }

            RepaymentPlan plan = new RepaymentPlan();
            plan.setTermNo(i);
            plan.setDueDate(firstDueDate.plusMonths(i - 1L));
            plan.setPrincipalAmount(principalPart.setScale(2, RoundingMode.HALF_UP));
            plan.setInterestAmount(interest);
            plan.setTotalAmount(principalPart.add(interest).setScale(2, RoundingMode.HALF_UP));
            plans.add(plan);

            remainingPrincipal = remainingPrincipal.subtract(principalPart);
        }
        return plans;
    }

    private List<RepaymentPlan> calculateEqualPrincipalPlan(BigDecimal principal, BigDecimal annualRate,
                                                            int months, LocalDate firstDueDate) {
        List<RepaymentPlan> plans = new ArrayList<>();
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal monthlyPrincipal = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

        BigDecimal remainingPrincipal = principal;
        for (int i = 1; i <= months; i++) {
            BigDecimal interest = remainingPrincipal.multiply(monthlyRate)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualPrincipal = (i == months) ? remainingPrincipal : monthlyPrincipal;

            RepaymentPlan plan = new RepaymentPlan();
            plan.setTermNo(i);
            plan.setDueDate(firstDueDate.plusMonths(i - 1L));
            plan.setPrincipalAmount(actualPrincipal);
            plan.setInterestAmount(interest);
            plan.setTotalAmount(actualPrincipal.add(interest));
            plans.add(plan);

            remainingPrincipal = remainingPrincipal.subtract(actualPrincipal);
        }
        return plans;
    }

    private BigDecimal calculateTotalInterestEqualInstallment(BigDecimal principal, BigDecimal annualRate, int months) {
        RepaymentPlanResultDTO preview = previewRepaymentPlan(principal, annualRate, months,
                "EQUAL_INSTALLMENT", null);
        return preview.getTotalInterest();
    }

    @Transactional(rollbackFor = Exception.class)
    public void processRepayment(RepaymentDTO dto) {
        RepaymentPlan plan = repaymentPlanMapper.selectById(dto.getRepaymentPlanId());
        if (plan == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "还款计划不存在");
        if (plan.getRepaymentStatus().equals(RepaymentStatusEnum.PAID.getCode())) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED, "该期已结清");
        }

        LoanAccount account = loanAccountMapper.selectById(plan.getLoanAccountId());
        if (account == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "贷款账户不存在");

        LocalDate today = dto.getRepaymentDate() != null ? dto.getRepaymentDate() : LocalDate.now();
        long overdueDays = ChronoUnit.DAYS.between(plan.getDueDate(), today);
        if (overdueDays < 0) overdueDays = 0;

        BigDecimal penalty = BigDecimal.ZERO;
        if (overdueDays > 0 && plan.getOverdueDays() == 0) {
            penalty = plan.getTotalAmount().multiply(config.getRepayment().getPenaltyRate())
                    .multiply(BigDecimal.valueOf(overdueDays))
                    .setScale(2, RoundingMode.HALF_UP);
            plan.setPenaltyAmount(penalty);
            plan.setOverdueDays((int) overdueDays);

            account.setOverdueDays(account.getOverdueDays() + (int) overdueDays);
            account.setOverdueTimes(account.getOverdueTimes() + 1);
            account.setTotalPenalty(account.getTotalPenalty().add(penalty));
            account.setRepaymentStatus(RepaymentStatusEnum.OVERDUE.getCode());
        }

        BigDecimal totalDue = plan.getTotalAmount().add(plan.getPenaltyAmount());
        BigDecimal payment = dto.getRepaymentAmount();
        if (payment.compareTo(totalDue) < 0) {
            throw new BusinessException(ErrorCode.REPAYMENT_AMOUNT_ERROR,
                    String.format("还款金额不足，应还%.2f元（含罚息%.2f元）", totalDue, plan.getPenaltyAmount()));
        }

        plan.setPaidPrincipal(plan.getPrincipalAmount());
        plan.setPaidInterest(plan.getInterestAmount());
        if (plan.getPenaltyAmount().compareTo(BigDecimal.ZERO) > 0) {
            plan.setPaidPenalty(plan.getPenaltyAmount());
        }
        plan.setActualPayTime(LocalDateTime.now());
        plan.setRepaymentStatus(RepaymentStatusEnum.PAID.getCode());
        repaymentPlanMapper.updateById(plan);

        account.setPaidPrincipal(account.getPaidPrincipal().add(plan.getPrincipalAmount()));
        account.setPaidInterest(account.getPaidInterest().add(plan.getInterestAmount()));
        account.setRemainingPrincipal(account.getRemainingPrincipal().subtract(plan.getPrincipalAmount()));
        account.setRemainingInterest(account.getRemainingInterest().subtract(plan.getInterestAmount()));
        account.setPaidPenalty(account.getPaidPenalty().add(plan.getPaidPenalty()));
        account.setPaidTerm(account.getPaidTerm() + 1);
        account.setLastRepaymentDate(today);

        if (account.getRemainingPrincipal().compareTo(BigDecimal.ZERO) <= 0
                || account.getPaidTerm().equals(account.getLoanTerm())) {
            account.setRepaymentStatus(RepaymentStatusEnum.PAID.getCode());
            account.setSettlementTime(LocalDateTime.now());
        } else {
            account.setRepaymentStatus(RepaymentStatusEnum.NORMAL.getCode());
        }
        loanAccountMapper.updateById(account);

        Employee employee = employeeMapper.selectById(account.getEmployeeId());
        if (employee != null) {
            String title = "还款成功通知";
            String content = String.format("【%s】您好，您第%d期还款已成功，金额%.2f元%s。剩余本金%.2f元，剩余%d期",
                    employee.getName(), plan.getTermNo(), plan.getTotalAmount(),
                    plan.getPenaltyAmount().compareTo(BigDecimal.ZERO) > 0
                            ? String.format("（含罚息%.2f元，逾期%d天）", plan.getPenaltyAmount(), overdueDays) : "",
                    account.getRemainingPrincipal(),
                    account.getLoanTerm() - account.getPaidTerm());
            notificationService.pushNotification(
                    employee.getId(), "EMPLOYEE", employee.getName(), employee.getPhone(),
                    NotificationTypeEnum.REPAYMENT_REMINDER, title, content,
                    account.getId(), "LOAN_ACCOUNT", account.getLoanAccountNo(),
                    account.getCompanyId(), account.getBranchId()
            );
        }

        auditLogService.log(plan.getLoanAccountNo(), "repayment", plan.getLoanAccountId(),
                "REPAY", "正常还款",
                account.getEmployeeId(), employee != null ? employee.getName() : "", "个人",
                account.getRemainingPrincipal().add(plan.getPrincipalAmount()), account.getRemainingPrincipal(), plan.getPrincipalAmount().negate(),
                String.format("第%d期还款%.2f元%s", plan.getTermNo(), plan.getTotalAmount(),
                        plan.getPenaltyAmount().compareTo(BigDecimal.ZERO) > 0 ? String.format("(含罚息%.2f)", plan.getPenaltyAmount()) : ""),
                "YES", null, account.getBranchId(), null);

        log.info("还款处理完成: loanAccountId={}, term={}, amount={}",
                plan.getLoanAccountId(), plan.getTermNo(), plan.getTotalAmount());
    }

    @Transactional(rollbackFor = Exception.class)
    public int processRepaymentReminders() {
        int daysBefore = config.getRepayment().getReminderDaysBefore();
        LocalDate reminderDate = LocalDate.now().plusDays(daysBefore);
        List<RepaymentPlan> plans = repaymentPlanMapper.findPlansForReminder(reminderDate);

        int count = 0;
        for (RepaymentPlan plan : plans) {
            try {
                LoanAccount account = loanAccountMapper.selectById(plan.getLoanAccountId());
                if (account == null) continue;

                Employee employee = employeeMapper.selectById(account.getEmployeeId());
                if (employee != null) {
                    String title = "还款提醒";
                    String content = String.format("【%s】您好，您的第%d期公积金贷款将于%s到期，应还金额%.2f元（本金%.2f元+利息%.2f元），请确保账户余额充足。",
                            employee.getName(), plan.getTermNo(), plan.getDueDate(),
                            plan.getTotalAmount(), plan.getPrincipalAmount(), plan.getInterestAmount());
                    notificationService.pushNotification(
                            employee.getId(), "EMPLOYEE", employee.getName(), employee.getPhone(),
                            NotificationTypeEnum.REPAYMENT_REMINDER, title, content,
                            plan.getId(), "REPAYMENT_PLAN", plan.getLoanAccountNo(),
                            account.getCompanyId(), account.getBranchId()
                    );
                }
                repaymentPlanMapper.markReminderSent(plan.getId());
                count++;
            } catch (Exception e) {
                log.error("还款提醒处理失败: planId={}, error={}", plan.getId(), e.getMessage());
            }
        }
        if (count > 0) {
            log.info("还款提醒处理完成，共{}条", count);
        }
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int processOverdueAndCollection() {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<LoanAccount> accountWrapper = new LambdaQueryWrapper<>();
        accountWrapper.in(LoanAccount::getRepaymentStatus,
                RepaymentStatusEnum.PENDING.getCode(),
                RepaymentStatusEnum.NORMAL.getCode(),
                RepaymentStatusEnum.OVERDUE.getCode());
        List<LoanAccount> accounts = loanAccountMapper.selectList(accountWrapper);

        int overdueCount = 0;
        for (LoanAccount account : accounts) {
            try {
                List<RepaymentPlan> overduePlans = repaymentPlanMapper.findOverduePlans(account.getId(), today);
                if (overduePlans.isEmpty()) continue;

                int totalOverdueDays = 0;
                BigDecimal totalOverdueAmount = BigDecimal.ZERO;
                BigDecimal totalOverduePrincipal = BigDecimal.ZERO;
                BigDecimal totalOverdueInterest = BigDecimal.ZERO;
                BigDecimal totalPenalty = BigDecimal.ZERO;

                for (RepaymentPlan plan : overduePlans) {
                    long days = ChronoUnit.DAYS.between(plan.getDueDate(), today);
                    if (days <= 0) continue;

                    BigDecimal penalty;
                    if (plan.getPenaltyAmount() == null || plan.getPenaltyAmount().compareTo(BigDecimal.ZERO) == 0) {
                        penalty = plan.getTotalAmount().multiply(config.getRepayment().getPenaltyRate())
                                .multiply(BigDecimal.valueOf(days))
                                .setScale(2, RoundingMode.HALF_UP);
                        plan.setPenaltyAmount(penalty);
                    } else {
                        penalty = plan.getPenaltyAmount();
                    }
                    plan.setOverdueDays((int) days);
                    plan.setRepaymentStatus(RepaymentStatusEnum.OVERDUE.getCode());
                    repaymentPlanMapper.updateById(plan);

                    totalOverdueDays = Math.max(totalOverdueDays, (int) days);
                    BigDecimal remainTotal = plan.getTotalAmount().subtract(
                            plan.getPaidPrincipal().add(plan.getPaidInterest()));
                    totalOverdueAmount = totalOverdueAmount.add(remainTotal).add(penalty.subtract(plan.getPaidPenalty()));
                    totalOverduePrincipal = totalOverduePrincipal.add(plan.getPrincipalAmount().subtract(plan.getPaidPrincipal()));
                    totalOverdueInterest = totalOverdueInterest.add(plan.getInterestAmount().subtract(plan.getPaidInterest()));
                    totalPenalty = totalPenalty.add(penalty.subtract(plan.getPaidPenalty()));
                }

                if (totalOverdueDays > 0) {
                    account.setOverdueDays(totalOverdueDays);
                    account.setRepaymentStatus(RepaymentStatusEnum.OVERDUE.getCode());
                    account.setTotalPenalty(account.getTotalPenalty().add(totalPenalty));
                    loanAccountMapper.updateById(account);

                    createCollectionTask(account, totalOverdueDays, totalOverdueAmount,
                            totalOverduePrincipal, totalOverdueInterest, totalPenalty);

                    sendOverdueAlert(account, totalOverdueDays, totalOverdueAmount, totalPenalty);

                    Employee emp = employeeMapper.selectById(account.getEmployeeId());
                    riskAlertService.createAlert(
                            RiskAlertTypeEnum.OVERDUE_RISING, "OVERDUE_CHECK",
                            account.getLoanAccountNo(), "repayment", account.getId(),
                            account.getEmployeeId(), emp != null ? emp.getName() : "", account.getBranchId(),
                            "贷款逾期预警",
                            String.format("贷款账户%s逾期%d天，累计欠款%.2f元",
                                    account.getLoanAccountNo(), totalOverdueDays, totalOverdueAmount),
                            BigDecimal.valueOf(totalOverdueDays), BigDecimal.valueOf(30));

                    overdueCount++;
                }
            } catch (Exception e) {
                log.error("逾期处理失败: accountId={}, error={}", account.getId(), e.getMessage());
            }
        }
        if (overdueCount > 0) {
            log.info("逾期及催收处理完成，共{}个账户", overdueCount);
        }
        return overdueCount;
    }

    private void createCollectionTask(LoanAccount account, int overdueDays, BigDecimal totalAmount,
                                      BigDecimal principal, BigDecimal interest, BigDecimal penalty) {
        String taskNo = "CT" + IdUtil.getSnowflakeNextIdStr();
        int taskLevel = overdueDays <= 15 ? 1 : overdueDays <= 30 ? 2 : 3;

        LambdaQueryWrapper<CollectionTask> existing = new LambdaQueryWrapper<>();
        existing.eq(CollectionTask::getLoanAccountId, account.getId())
                .in(CollectionTask::getTaskStatus, 0, 1);
        if (collectionTaskMapper.selectCount(existing) > 0) return;

        CollectionTask task = new CollectionTask();
        task.setTaskNo(taskNo);
        task.setLoanAccountId(account.getId());
        task.setLoanAccountNo(account.getLoanAccountNo());
        task.setEmployeeId(account.getEmployeeId());
        Employee emp = employeeMapper.selectById(account.getEmployeeId());
        if (emp != null) {
            task.setEmployeeName(emp.getName());
            task.setEmployeePhone(emp.getPhone());
        }
        task.setAssigneeId(1000L + taskLevel);
        task.setAssigneeName("信贷员-" + (taskLevel == 1 ? "初级" : taskLevel == 2 ? "中级" : "高级"));
        task.setBranchId(account.getBranchId());
        task.setOverdueDays(overdueDays);
        task.setOverdueAmount(totalAmount);
        task.setOverduePrincipal(principal);
        task.setOverdueInterest(interest);
        task.setPenaltyAmount(penalty);
        task.setTaskLevel(taskLevel);
        task.setTaskStatus(0);
        task.setAssignTime(LocalDateTime.now());
        task.setDeadlineTime(LocalDateTime.now().plusDays(taskLevel == 1 ? 5 : taskLevel == 2 ? 3 : 1));
        task.setStatus(1);
        collectionTaskMapper.insert(task);

        notificationService.pushNotification(
                task.getAssigneeId(), "STAFF", task.getAssigneeName(), null,
                NotificationTypeEnum.COLLECTION_TASK,
                "新催收任务分配",
                String.format("任务编号%s：贷款账户%s，逾期%d天，欠款总额%.2f元（含罚息%.2f元）",
                        taskNo, account.getLoanAccountNo(), overdueDays, totalAmount, penalty),
                task.getId(), "COLLECTION", taskNo,
                null, account.getBranchId()
        );
    }

    private void sendOverdueAlert(LoanAccount account, int overdueDays, BigDecimal amount, BigDecimal penalty) {
        Employee employee = employeeMapper.selectById(account.getEmployeeId());
        if (employee == null) return;

        String title = "贷款逾期警示通知";
        String content = String.format("【%s】您好，您的公积金贷款（账户号%s）已逾期%d天，累计欠款%.2f元（含罚息%.2f元），请尽快还款，逾期将影响您的信用记录。",
                employee.getName(), account.getLoanAccountNo(), overdueDays, amount, penalty);
        notificationService.pushNotification(
                employee.getId(), "EMPLOYEE", employee.getName(), employee.getPhone(),
                NotificationTypeEnum.OVERDUE_ALERT, title, content,
                account.getId(), "LOAN_ACCOUNT", account.getLoanAccountNo(),
                account.getCompanyId(), account.getBranchId()
        );
    }

    public List<RepaymentPlan> getRepaymentPlans(Long loanAccountId) {
        LambdaQueryWrapper<RepaymentPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RepaymentPlan::getLoanAccountId, loanAccountId)
                .orderByAsc(RepaymentPlan::getTermNo);
        return repaymentPlanMapper.selectList(wrapper);
    }

    public LoanAccount getLoanAccountDetail(Long loanAccountId) {
        return loanAccountMapper.selectById(loanAccountId);
    }

    public Page<CollectionTask> queryCollectionTasks(Long assigneeId, Integer taskStatus,
                                                     Integer taskLevel, int pageNum, int pageSize) {
        LambdaQueryWrapper<CollectionTask> wrapper = new LambdaQueryWrapper<>();
        if (assigneeId != null) wrapper.eq(CollectionTask::getAssigneeId, assigneeId);
        if (taskStatus != null) wrapper.eq(CollectionTask::getTaskStatus, taskStatus);
        if (taskLevel != null) wrapper.eq(CollectionTask::getTaskLevel, taskLevel);
        wrapper.orderByDesc(CollectionTask::getCreateTime);

        Page<CollectionTask> page = new Page<>(pageNum, pageSize);
        return collectionTaskMapper.selectPage(page, wrapper);
    }

    public EarlyRepaymentResultDTO previewEarlyRepayment(EarlyRepaymentDTO dto) {
        return buildEarlyRepaymentResult(dto, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public EarlyRepaymentResultDTO processEarlyRepayment(EarlyRepaymentDTO dto) {
        return buildEarlyRepaymentResult(dto, false);
    }

    private EarlyRepaymentResultDTO buildEarlyRepaymentResult(EarlyRepaymentDTO dto, boolean previewOnly) {
        LoanAccount account = loanAccountMapper.selectById(dto.getLoanAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "贷款账户不存在");
        }
        if (account.getRepaymentStatus().equals(RepaymentStatusEnum.PAID.getCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED, "贷款已结清，无法提前还款");
        }

        LambdaQueryWrapper<RepaymentPlan> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(RepaymentPlan::getLoanAccountId, account.getId())
                .eq(RepaymentPlan::getRepaymentStatus, RepaymentStatusEnum.PENDING.getCode())
                .orderByAsc(RepaymentPlan::getTermNo);
        List<RepaymentPlan> pendingPlans = repaymentPlanMapper.selectList(pendingWrapper);

        if (pendingPlans.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED, "无待还期数，无需提前还款");
        }

        int paidTerm = account.getPaidTerm() != null ? account.getPaidTerm() : 0;
        int remainingTermBefore = account.getLoanTerm() - paidTerm;
        BigDecimal remainingPrincipal = account.getRemainingPrincipal();

        boolean isFull = "FULL".equalsIgnoreCase(dto.getRepaymentType());
        BigDecimal earlyRepaymentAmount;
        if (isFull) {
            earlyRepaymentAmount = remainingPrincipal;
        } else {
            if (dto.getRepaymentAmount() == null || dto.getRepaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.REPAYMENT_AMOUNT_ERROR, "部分提前还款金额必须大于0");
            }
            if (dto.getRepaymentAmount().compareTo(remainingPrincipal) >= 0) {
                throw new BusinessException(ErrorCode.REPAYMENT_AMOUNT_ERROR, "部分提前还款金额不能大于等于剩余本金");
            }
            earlyRepaymentAmount = dto.getRepaymentAmount();
        }

        BigDecimal remainingPrincipalAfter = remainingPrincipal.subtract(earlyRepaymentAmount);
        BigDecimal oldMonthlyPayment = calculateOldMonthlyPayment(account);
        BigDecimal oldRemainingInterest = calculateRemainingInterest(pendingPlans);

        EarlyRepaymentResultDTO result = new EarlyRepaymentResultDTO();
        result.setLoanAccountId(account.getId());
        result.setEarlyRepaymentAmount(earlyRepaymentAmount);
        result.setRemainingPrincipalBefore(remainingPrincipal);
        result.setRemainingPrincipalAfter(remainingPrincipalAfter);
        result.setRemainingTermBefore(remainingTermBefore);
        result.setRepaymentType(dto.getRepaymentType());
        result.setOldMonthlyPayment(oldMonthlyPayment);

        if (isFull) {
            result.setRemainingTermAfter(0);
            result.setNewMonthlyPayment(BigDecimal.ZERO);
            result.setSavedInterest(oldRemainingInterest);
            result.setNewPlanItems(new ArrayList<>());

            if (!previewOnly) {
                for (RepaymentPlan plan : pendingPlans) {
                    plan.setPaidPrincipal(plan.getPrincipalAmount());
                    plan.setPaidInterest(plan.getInterestAmount());
                    plan.setActualPayTime(LocalDateTime.now());
                    plan.setRepaymentStatus(RepaymentStatusEnum.PAID.getCode());
                    repaymentPlanMapper.updateById(plan);
                }

                account.setPaidPrincipal(account.getPaidPrincipal().add(remainingPrincipal));
                account.setRemainingPrincipal(BigDecimal.ZERO);
                account.setRemainingInterest(BigDecimal.ZERO);
                account.setPaidTerm(account.getLoanTerm());
                account.setRepaymentStatus(RepaymentStatusEnum.PAID.getCode());
                account.setSettlementTime(LocalDateTime.now());
                account.setLastRepaymentDate(LocalDate.now());
                loanAccountMapper.updateById(account);

                deductFromFundAccount(account, earlyRepaymentAmount);

                Employee employee = employeeMapper.selectById(account.getEmployeeId());
                auditLogService.log(account.getLoanAccountNo(), "repayment", account.getId(),
                        "EARLY_REPAYMENT_FULL", "全额提前还款",
                        account.getEmployeeId(), employee != null ? employee.getName() : "", "个人",
                        remainingPrincipal, BigDecimal.ZERO, earlyRepaymentAmount.negate(),
                        String.format("全额提前还款%.2f元，贷款结清", earlyRepaymentAmount),
                        "YES", null, account.getBranchId(), null);

                sendEarlyRepaymentNotification(account, true, earlyRepaymentAmount, BigDecimal.ZERO, 0);
            }
        } else {
            int newRemainingTerm;
            if (dto.getReduceTermMonths() != null && dto.getReduceTermMonths() > 0) {
                newRemainingTerm = remainingTermBefore - dto.getReduceTermMonths();
                if (newRemainingTerm < 1) {
                    throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED, "缩短期数后剩余期数不能小于1");
                }
            } else {
                newRemainingTerm = remainingTermBefore;
            }

            LocalDate nextDueDate = pendingPlans.get(0).getDueDate();
            List<RepaymentPlan> newPlans = calculateEqualInstallmentPlan(
                    remainingPrincipalAfter, account.getInterestRate(),
                    newRemainingTerm, nextDueDate);

            BigDecimal newMonthlyPayment = newPlans.isEmpty() ? BigDecimal.ZERO : newPlans.get(0).getTotalAmount();
            BigDecimal newRemainingInterest = newPlans.stream()
                    .map(RepaymentPlan::getInterestAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal savedInterest = oldRemainingInterest.subtract(newRemainingInterest).max(BigDecimal.ZERO);

            result.setRemainingTermAfter(newRemainingTerm);
            result.setNewMonthlyPayment(newMonthlyPayment);
            result.setSavedInterest(savedInterest);

            List<RepaymentPlanResultDTO.PlanItem> planItems = new ArrayList<>();
            BigDecimal rp = remainingPrincipalAfter;
            for (RepaymentPlan p : newPlans) {
                RepaymentPlanResultDTO.PlanItem item = new RepaymentPlanResultDTO.PlanItem();
                item.setTermNo(p.getTermNo());
                item.setDueDate(p.getDueDate());
                item.setPrincipalAmount(p.getPrincipalAmount());
                item.setInterestAmount(p.getInterestAmount());
                item.setTotalAmount(p.getTotalAmount());
                rp = rp.subtract(p.getPrincipalAmount());
                item.setRemainingPrincipal(rp.max(BigDecimal.ZERO));
                planItems.add(item);
            }
            result.setNewPlanItems(planItems);

            if (!previewOnly) {
                for (RepaymentPlan plan : pendingPlans) {
                    repaymentPlanMapper.deleteById(plan.getId());
                }

                int termNoBase = paidTerm;
                for (int i = 0; i < newPlans.size(); i++) {
                    RepaymentPlan plan = newPlans.get(i);
                    plan.setLoanAccountId(account.getId());
                    plan.setLoanAccountNo(account.getLoanAccountNo());
                    plan.setEmployeeId(account.getEmployeeId());
                    plan.setBranchId(account.getBranchId());
                    plan.setTermNo(termNoBase + i + 1);
                    plan.setPaidPrincipal(BigDecimal.ZERO);
                    plan.setPaidInterest(BigDecimal.ZERO);
                    plan.setPenaltyAmount(BigDecimal.ZERO);
                    plan.setPaidPenalty(BigDecimal.ZERO);
                    plan.setOverdueDays(0);
                    plan.setRepaymentStatus(RepaymentStatusEnum.PENDING.getCode());
                    plan.setReminderSent(0);
                    plan.setStatus(1);
                    repaymentPlanMapper.insert(plan);
                }

                BigDecimal paidInterestForEarlyRepayment = earlyRepaymentAmount.multiply(account.getInterestRate())
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

                account.setPaidPrincipal(account.getPaidPrincipal().add(earlyRepaymentAmount));
                account.setRemainingPrincipal(remainingPrincipalAfter);
                account.setRemainingInterest(newRemainingInterest);
                account.setPaidInterest(account.getPaidInterest().add(paidInterestForEarlyRepayment));
                account.setLastRepaymentDate(LocalDate.now());
                loanAccountMapper.updateById(account);

                deductFromFundAccount(account, earlyRepaymentAmount);

                if (earlyRepaymentAmount.compareTo(remainingPrincipal.multiply(new BigDecimal("0.5"))) > 0) {
                    Employee emp = employeeMapper.selectById(account.getEmployeeId());
                    riskAlertService.createAlert(
                            RiskAlertTypeEnum.EARLY_REPAYMENT_ABNORMAL, "EARLY_REPAYMENT",
                            account.getLoanAccountNo(), "repayment", account.getId(),
                            account.getEmployeeId(), emp != null ? emp.getName() : "", account.getBranchId(),
                            "部分提前还款金额异常",
                            String.format("贷款账户%s部分提前还款%.2f元，超过剩余本金50%%(%.2f元)",
                                    account.getLoanAccountNo(), earlyRepaymentAmount, remainingPrincipal.multiply(new BigDecimal("0.5"))),
                            earlyRepaymentAmount, remainingPrincipal.multiply(new BigDecimal("0.5")));
                }

                Employee employee = employeeMapper.selectById(account.getEmployeeId());
                auditLogService.log(account.getLoanAccountNo(), "repayment", account.getId(),
                        "EARLY_REPAYMENT_PARTIAL", "部分提前还款",
                        account.getEmployeeId(), employee != null ? employee.getName() : "", "个人",
                        remainingPrincipal, remainingPrincipalAfter, earlyRepaymentAmount.negate(),
                        String.format("部分提前还款%.2f元，剩余%d期，新月供%.2f元", earlyRepaymentAmount, newRemainingTerm, newMonthlyPayment),
                        "YES", null, account.getBranchId(), null);

                sendEarlyRepaymentNotification(account, false, earlyRepaymentAmount, newMonthlyPayment, newRemainingTerm);
            }
        }

        log.info("提前还款{}完成: loanAccountId={}, type={}, amount={}",
                previewOnly ? "预览" : "处理", account.getId(), dto.getRepaymentType(), earlyRepaymentAmount);
        return result;
    }

    private BigDecimal calculateOldMonthlyPayment(LoanAccount account) {
        int remainingTerm = account.getLoanTerm() - (account.getPaidTerm() != null ? account.getPaidTerm() : 0);
        if (remainingTerm <= 0) return BigDecimal.ZERO;
        BigDecimal monthlyRate = account.getInterestRate().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return account.getRemainingPrincipal().divide(BigDecimal.valueOf(remainingTerm), 2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(remainingTerm);
        return account.getRemainingPrincipal().multiply(monthlyRate).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRemainingInterest(List<RepaymentPlan> pendingPlans) {
        return pendingPlans.stream()
                .map(RepaymentPlan::getInterestAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void deductFromFundAccount(LoanAccount account, BigDecimal amount) {
        FundAccount fundAccount = fundAccountService.getEmployeeFundAccount(account.getEmployeeId());
        if (fundAccount == null) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "员工公积金账户不存在");
        }
        fundAccountService.freezeAmount(fundAccount.getId(), amount,
                account.getId(), "EARLY_REPAYMENT", account.getLoanAccountNo(), "SYSTEM");
        fundAccountService.deductFrozenAmount(fundAccount.getId(), amount,
                account.getId(), "EARLY_REPAYMENT", account.getLoanAccountNo(), "SYSTEM");
    }

    private void sendEarlyRepaymentNotification(LoanAccount account, boolean isFull,
                                                 BigDecimal amount, BigDecimal newMonthlyPayment, int newRemainingTerm) {
        Employee employee = employeeMapper.selectById(account.getEmployeeId());
        if (employee == null) return;

        String title = isFull ? "全额提前还款成功通知" : "部分提前还款成功通知";
        String content;
        if (isFull) {
            content = String.format("【%s】您好，您已成功全额提前还款%.2f元，贷款已结清。",
                    employee.getName(), amount);
        } else {
            content = String.format("【%s】您好，您已成功部分提前还款%.2f元，剩余本金%.2f元，剩余%d期，新月供%.2f元。",
                    employee.getName(), amount, account.getRemainingPrincipal(), newRemainingTerm, newMonthlyPayment);
        }
        notificationService.pushNotification(
                employee.getId(), "EMPLOYEE", employee.getName(), employee.getPhone(),
                NotificationTypeEnum.REPAYMENT_REMINDER, title, content,
                account.getId(), "LOAN_ACCOUNT", account.getLoanAccountNo(),
                account.getCompanyId(), account.getBranchId()
        );
    }
}
