package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.dto.DashboardDTO;
import com.housingfund.dto.FundReportQueryDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.ApprovalStatusEnum;
import com.housingfund.enums.RepaymentStatusEnum;
import com.housingfund.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundReportService {

    private final FundReportMapper reportMapper;
    private final BranchMapper branchMapper;
    private final CompanyMapper companyMapper;
    private final EmployeeMapper employeeMapper;
    private final ContributionDetailMapper contributionDetailMapper;
    private final WithdrawalApplicationMapper withdrawalMapper;
    private final LoanApplicationMapper loanMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final RepaymentPlanMapper repaymentPlanMapper;
    private final FundAccountMapper fundAccountMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<FundReport> generateDailyReport(LocalDate reportDate) {
        List<FundReport> reports = new ArrayList<>();

        FundReport totalReport = generateBranchReport(reportDate, null, "全辖汇总");
        reportMapper.insert(totalReport);
        reports.add(totalReport);

        LambdaQueryWrapper<Branch> branchWrapper = new LambdaQueryWrapper<>();
        List<Branch> branches = branchMapper.selectList(branchWrapper);
        for (Branch branch : branches) {
            FundReport report = generateBranchReport(reportDate, branch.getId(), branch.getBranchName());
            reportMapper.insert(report);
            reports.add(report);
        }

        log.info("资金报表生成完成: reportDate={}, 共{}份", reportDate, reports.size());
        return reports;
    }

    private FundReport generateBranchReport(LocalDate reportDate, Long branchId, String branchName) {
        YearMonth ym = YearMonth.from(reportDate);
        LocalDate monthStart = ym.atDay(1);
        LocalDate yearStart = reportDate.withDayOfYear(1);
        LocalDateTime monthStartDT = monthStart.atStartOfDay();
        LocalDateTime reportEndDT = reportDate.atTime(23, 59, 59);

        FundReport report = new FundReport();
        report.setReportNo("FR" + IdUtil.getSnowflakeNextIdStr());
        report.setReportDate(reportDate);
        report.setReportType(branchId == null ? "TOTAL" : "BRANCH");
        report.setBranchId(branchId);
        report.setBranchName(branchName);
        report.setStatus(1);

        LambdaQueryWrapper<Company> companyWrapper = new LambdaQueryWrapper<>();
        if (branchId != null) companyWrapper.eq(Company::getBranchId, branchId);
        companyWrapper.eq(Company::getStatus, 1);
        Long c = companyMapper.selectCount(companyWrapper);
        report.setActiveCompanyCount(c != null ? c.intValue() : 0);

        LambdaQueryWrapper<Employee> empWrapper = new LambdaQueryWrapper<>();
        if (branchId != null) empWrapper.eq(Employee::getBranchId, branchId);
        empWrapper.eq(Employee::getStatus, 1);
        Long e = employeeMapper.selectCount(empWrapper);
        report.setActiveEmployeeCount(e != null ? e.intValue() : 0);

        LambdaQueryWrapper<Company> newCompanyWrapper = new LambdaQueryWrapper<>();
        if (branchId != null) newCompanyWrapper.eq(Company::getBranchId, branchId);
        newCompanyWrapper.ge(Company::getCreateTime, monthStartDT)
                .le(Company::getCreateTime, reportEndDT);
        Long nc = companyMapper.selectCount(newCompanyWrapper);
        report.setNewCompanyCount(nc != null ? nc.intValue() : 0);

        LambdaQueryWrapper<Employee> newEmpWrapper = new LambdaQueryWrapper<>();
        if (branchId != null) newEmpWrapper.eq(Employee::getBranchId, branchId);
        newEmpWrapper.ge(Employee::getCreateTime, monthStartDT)
                .le(Employee::getCreateTime, reportEndDT);
        Long ne = employeeMapper.selectCount(newEmpWrapper);
        report.setNewEmployeeCount(ne != null ? ne.intValue() : 0);

        BigDecimal monthlyContribution = reportMapper.sumContributionByPeriodAndBranch(monthStart, reportDate, branchId);
        report.setMonthlyContribution(monthlyContribution != null ? monthlyContribution : BigDecimal.ZERO);
        BigDecimal totalContribution = reportMapper.sumContributionByPeriodAndBranch(yearStart, reportDate, branchId);
        report.setTotalContribution(totalContribution != null ? totalContribution : BigDecimal.ZERO);

        LambdaQueryWrapper<ContributionDetail> cdWrapper = new LambdaQueryWrapper<>();
        cdWrapper.ge(ContributionDetail::getContributionMonth, monthStart)
                .le(ContributionDetail::getContributionMonth, reportDate)
                .eq(ContributionDetail::getStatus, 1);
        if (branchId != null) cdWrapper.eq(ContributionDetail::getBranchId, branchId);
        List<ContributionDetail> monthDetails = contributionDetailMapper.selectList(cdWrapper);
        BigDecimal monthCompany = BigDecimal.ZERO;
        BigDecimal monthPersonal = BigDecimal.ZERO;
        for (ContributionDetail d : monthDetails) {
            if (d.getCompanyAmount() != null) monthCompany = monthCompany.add(d.getCompanyAmount());
            if (d.getPersonalAmount() != null) monthPersonal = monthPersonal.add(d.getPersonalAmount());
        }
        report.setMonthlyCompanyContribution(monthCompany);
        report.setMonthlyPersonalContribution(monthPersonal);

        BigDecimal monthlyWithdrawal = reportMapper.sumWithdrawalByPeriodAndBranch(monthStart, reportDate, branchId);
        report.setMonthlyWithdrawal(monthlyWithdrawal != null ? monthlyWithdrawal : BigDecimal.ZERO);
        BigDecimal totalWithdrawal = reportMapper.sumWithdrawalByPeriodAndBranch(yearStart, reportDate, branchId);
        report.setTotalWithdrawal(totalWithdrawal != null ? totalWithdrawal : BigDecimal.ZERO);

        Map<String, Object> loanStats = reportMapper.sumLoanByPeriodAndBranch(monthStart, reportDate, branchId);
        if (loanStats != null) {
            Object sumObj = loanStats.get("IFNULL(SUM(approved_amount), 0)");
            Object cntObj = loanStats.get("IFNULL(COUNT(*), 0)");
            report.setMonthlyLoanIssue(sumObj instanceof BigDecimal ? (BigDecimal) sumObj :
                    sumObj != null ? new BigDecimal(sumObj.toString()) : BigDecimal.ZERO);
            report.setMonthlyLoanCount(cntObj instanceof Number ? ((Number) cntObj).intValue() : 0);
        } else {
            report.setMonthlyLoanIssue(BigDecimal.ZERO);
            report.setMonthlyLoanCount(0);
        }

        LambdaQueryWrapper<LoanAccount> laWrapper = new LambdaQueryWrapper<>();
        if (branchId != null) laWrapper.eq(LoanAccount::getBranchId, branchId);
        laWrapper.ne(LoanAccount::getRepaymentStatus, RepaymentStatusEnum.PAID.getCode());
        List<LoanAccount> activeLoans = loanAccountMapper.selectList(laWrapper);
        BigDecimal totalLoanBalance = BigDecimal.ZERO;
        int overdueCount = 0;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        for (LoanAccount la : activeLoans) {
            BigDecimal rp = la.getRemainingPrincipal() != null ? la.getRemainingPrincipal() : BigDecimal.ZERO;
            BigDecimal ri = la.getRemainingInterest() != null ? la.getRemainingInterest() : BigDecimal.ZERO;
            totalLoanBalance = totalLoanBalance.add(rp).add(ri);
            if (RepaymentStatusEnum.OVERDUE.getCode().equals(la.getRepaymentStatus())) {
                overdueCount++;
                overdueAmount = overdueAmount.add(rp).add(ri);
            }
        }
        report.setTotalLoanBalance(totalLoanBalance);
        report.setOverdueLoanCount(overdueCount);
        report.setOverdueLoanAmount(overdueAmount);
        if (totalLoanBalance.compareTo(BigDecimal.ZERO) > 0) {
            report.setOverdueRate(overdueAmount.divide(totalLoanBalance, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")));
        } else {
            report.setOverdueRate(BigDecimal.ZERO);
        }

        LambdaQueryWrapper<RepaymentPlan> rpWrapper = new LambdaQueryWrapper<>();
        if (branchId != null) {
            LambdaQueryWrapper<LoanAccount> branchLA = new LambdaQueryWrapper<>();
            branchLA.eq(LoanAccount::getBranchId, branchId);
            List<LoanAccount> branchAccounts = loanAccountMapper.selectList(branchLA);
            if (!branchAccounts.isEmpty()) {
                rpWrapper.in(RepaymentPlan::getLoanAccountId,
                        branchAccounts.stream().map(LoanAccount::getId).toList());
            }
        }
        rpWrapper.eq(RepaymentPlan::getRepaymentStatus, RepaymentStatusEnum.PAID.getCode())
                .ge(RepaymentPlan::getActualPayTime, monthStartDT)
                .le(RepaymentPlan::getActualPayTime, reportEndDT);
        List<RepaymentPlan> paidPlans = repaymentPlanMapper.selectList(rpWrapper);
        BigDecimal monthRepayment = BigDecimal.ZERO;
        BigDecimal monthPenalty = BigDecimal.ZERO;
        for (RepaymentPlan p : paidPlans) {
            if (p.getPaidPrincipal() != null) monthRepayment = monthRepayment.add(p.getPaidPrincipal());
            if (p.getPaidInterest() != null) monthRepayment = monthRepayment.add(p.getPaidInterest());
            if (p.getPaidPenalty() != null) monthPenalty = monthPenalty.add(p.getPaidPenalty());
        }
        report.setMonthlyRepayment(monthRepayment);
        report.setMonthlyPenalty(monthPenalty);

        LambdaQueryWrapper<FundAccount> faWrapper = new LambdaQueryWrapper<>();
        if (branchId != null) faWrapper.eq(FundAccount::getBranchId, branchId);
        faWrapper.eq(FundAccount::getStatus, 1);
        List<FundAccount> accounts = fundAccountMapper.selectList(faWrapper);
        BigDecimal fundBalance = BigDecimal.ZERO;
        for (FundAccount fa : accounts) {
            if (fa.getBalance() != null) fundBalance = fundBalance.add(fa.getBalance());
        }
        report.setFundBalance(fundBalance);

        return report;
    }

    public Page<FundReport> queryReports(FundReportQueryDTO query) {
        LambdaQueryWrapper<FundReport> wrapper = new LambdaQueryWrapper<>();
        if (query.getStartDate() != null) wrapper.ge(FundReport::getReportDate, query.getStartDate());
        if (query.getEndDate() != null) wrapper.le(FundReport::getReportDate, query.getEndDate());
        if (query.getBranchId() != null) wrapper.eq(FundReport::getBranchId, query.getBranchId());
        if (query.getReportType() != null) wrapper.eq(FundReport::getReportType, query.getReportType());
        wrapper.orderByDesc(FundReport::getReportDate).orderByAsc(FundReport::getBranchId);

        Page<FundReport> page = new Page<>(query.getPageNum(), query.getPageSize());
        return reportMapper.selectPage(page, wrapper);
    }

    public byte[] exportReports(FundReportQueryDTO query) {
        query.setPageNum(1);
        query.setPageSize(10000);
        Page<FundReport> page = queryReports(query);
        List<FundReport> reports = page.getRecords();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (FundReport r : reports) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("报表日期", r.getReportDate());
            row.put("机构名称", r.getBranchName());
            row.put("报表类型", "TOTAL".equals(r.getReportType()) ? "全辖汇总" : "分支机构");
            row.put("新增单位数", r.getNewCompanyCount());
            row.put("新增职工数", r.getNewEmployeeCount());
            row.put("活跃单位数", r.getActiveCompanyCount());
            row.put("活跃职工数", r.getActiveEmployeeCount());
            row.put("本月缴存额(元)", formatAmount(r.getMonthlyContribution()));
            row.put("  其中单位缴存(元)", formatAmount(r.getMonthlyCompanyContribution()));
            row.put("  其中个人缴存(元)", formatAmount(r.getMonthlyPersonalContribution()));
            row.put("本年累计缴存(元)", formatAmount(r.getTotalContribution()));
            row.put("本月提取额(元)", formatAmount(r.getMonthlyWithdrawal()));
            row.put("本年累计提取(元)", formatAmount(r.getTotalWithdrawal()));
            row.put("本月发放贷款(元)", formatAmount(r.getMonthlyLoanIssue()));
            row.put("本月贷款笔数", r.getMonthlyLoanCount());
            row.put("贷款余额(元)", formatAmount(r.getTotalLoanBalance()));
            row.put("逾期贷款笔数", r.getOverdueLoanCount());
            row.put("逾期贷款金额(元)", formatAmount(r.getOverdueLoanAmount()));
            row.put("逾期率(%)", formatAmount(r.getOverdueRate()));
            row.put("本月还款额(元)", formatAmount(r.getMonthlyRepayment()));
            row.put("本月罚息(元)", formatAmount(r.getMonthlyPenalty()));
            row.put("资金池余额(元)", formatAmount(r.getFundBalance()));
            rows.add(row);
        }

        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(rows, true);
        writer.autoSizeColumnAll();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.flush(out, true);
        writer.close();
        return out.toByteArray();
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public FundReport getLatestSummary(Long branchId) {
        LambdaQueryWrapper<FundReport> wrapper = new LambdaQueryWrapper<>();
        if (branchId != null) {
            wrapper.eq(FundReport::getBranchId, branchId);
        } else {
            wrapper.eq(FundReport::getReportType, "TOTAL");
        }
        wrapper.orderByDesc(FundReport::getReportDate);
        Page<FundReport> page = new Page<>(1, 1);
        Page<FundReport> result = reportMapper.selectPage(page, wrapper);
        return result.getRecords().isEmpty() ? null : result.getRecords().get(0);
    }

    public DashboardDTO getDashboard(Long branchId, Integer months) {
        if (months == null || months <= 0) months = 12;
        DashboardDTO dto = new DashboardDTO();

        LambdaQueryWrapper<FundReport> latestWrapper = new LambdaQueryWrapper<>();
        latestWrapper.orderByDesc(FundReport::getReportDate);
        Page<FundReport> lp = new Page<>(1, 1);
        List<FundReport> lr = reportMapper.selectPage(lp, latestWrapper).getRecords();
        if (lr.isEmpty()) return dto;
        LocalDate latestDate = lr.get(0).getReportDate();

        YearMonth latestYm = YearMonth.from(latestDate);
        YearMonth startYm = latestYm.minusMonths(months - 1);

        YearMonth yoyStartYm = startYm.minusYears(1);
        LambdaQueryWrapper<FundReport> rangeWrapper = new LambdaQueryWrapper<>();
        rangeWrapper.ge(FundReport::getReportDate, yoyStartYm.atDay(1))
                .le(FundReport::getReportDate, latestYm.atEndOfMonth());
        List<FundReport> allReports = reportMapper.selectList(rangeWrapper);

        YearMonth prevYm = latestYm.minusMonths(1);
        YearMonth lastYearYm = latestYm.minusYears(1);

        // --- Branch Comparison ---
        Map<Long, FundReport> latestByBranch = new HashMap<>();
        for (FundReport r : allReports) {
            if (!"BRANCH".equals(r.getReportType())) continue;
            if (!YearMonth.from(r.getReportDate()).equals(latestYm)) continue;
            FundReport existing = latestByBranch.get(r.getBranchId());
            if (existing == null || r.getReportDate().isAfter(existing.getReportDate())) {
                latestByBranch.put(r.getBranchId(), r);
            }
        }

        List<DashboardDTO.BranchComparison> branchComparisons = new ArrayList<>();
        for (FundReport current : latestByBranch.values()) {
            DashboardDTO.BranchComparison bc = new DashboardDTO.BranchComparison();
            bc.setBranchId(current.getBranchId());
            bc.setBranchName(current.getBranchName());
            bc.setMonthlyContribution(current.getMonthlyContribution());
            bc.setMonthlyWithdrawal(current.getMonthlyWithdrawal());
            bc.setMonthlyLoanIssue(current.getMonthlyLoanIssue());
            bc.setOverdueRate(current.getOverdueRate());
            bc.setFundBalance(current.getFundBalance());

            FundReport prevReport = findLatestReportInMonth(allReports, current.getBranchId(), prevYm);
            FundReport lastYearReport = findLatestReportInMonth(allReports, current.getBranchId(), lastYearYm);

            bc.setContributionYoY(calcYoY(current.getMonthlyContribution(), lastYearReport != null ? lastYearReport.getMonthlyContribution() : null));
            bc.setContributionMoM(calcMoM(current.getMonthlyContribution(), prevReport != null ? prevReport.getMonthlyContribution() : null));
            bc.setWithdrawalYoY(calcYoY(current.getMonthlyWithdrawal(), lastYearReport != null ? lastYearReport.getMonthlyWithdrawal() : null));
            bc.setWithdrawalMoM(calcMoM(current.getMonthlyWithdrawal(), prevReport != null ? prevReport.getMonthlyWithdrawal() : null));
            bc.setLoanYoY(calcYoY(current.getMonthlyLoanIssue(), lastYearReport != null ? lastYearReport.getMonthlyLoanIssue() : null));
            bc.setLoanMoM(calcMoM(current.getMonthlyLoanIssue(), prevReport != null ? prevReport.getMonthlyLoanIssue() : null));
            bc.setOverdueRateYoY(calcRateChange(current.getOverdueRate(), lastYearReport != null ? lastYearReport.getOverdueRate() : null));
            bc.setOverdueRateMoM(calcRateChange(current.getOverdueRate(), prevReport != null ? prevReport.getOverdueRate() : null));

            branchComparisons.add(bc);
        }
        dto.setBranchComparisons(branchComparisons);

        // --- Trend Data ---
        Long trendBranchId = branchId;
        List<DashboardDTO.TrendItem> contributionTrend = new ArrayList<>();
        List<DashboardDTO.TrendItem> withdrawalTrend = new ArrayList<>();
        List<DashboardDTO.TrendItem> loanTrend = new ArrayList<>();
        List<DashboardDTO.TrendItem> overdueTrend = new ArrayList<>();

        for (int i = 0; i < months; i++) {
            YearMonth ym = startYm.plusMonths(i);
            FundReport monthReport = findLatestReportInMonth(allReports, trendBranchId, ym);
            FundReport lastYearMonthReport = findLatestReportInMonth(allReports, trendBranchId, ym.minusYears(1));
            FundReport prevMonthReport = findLatestReportInMonth(allReports, trendBranchId, ym.minusMonths(1));

            DashboardDTO.TrendItem contribItem = new DashboardDTO.TrendItem();
            contribItem.setDate(ym.toString());
            contribItem.setContribution(monthReport != null ? monthReport.getMonthlyContribution() : BigDecimal.ZERO);
            contribItem.setContributionYoY(calcYoY(
                    monthReport != null ? monthReport.getMonthlyContribution() : null,
                    lastYearMonthReport != null ? lastYearMonthReport.getMonthlyContribution() : null));
            contribItem.setContributionMoM(calcMoM(
                    monthReport != null ? monthReport.getMonthlyContribution() : null,
                    prevMonthReport != null ? prevMonthReport.getMonthlyContribution() : null));
            contributionTrend.add(contribItem);

            DashboardDTO.TrendItem withItem = new DashboardDTO.TrendItem();
            withItem.setDate(ym.toString());
            withItem.setWithdrawal(monthReport != null ? monthReport.getMonthlyWithdrawal() : BigDecimal.ZERO);
            withItem.setWithdrawalYoY(calcYoY(
                    monthReport != null ? monthReport.getMonthlyWithdrawal() : null,
                    lastYearMonthReport != null ? lastYearMonthReport.getMonthlyWithdrawal() : null));
            withItem.setWithdrawalMoM(calcMoM(
                    monthReport != null ? monthReport.getMonthlyWithdrawal() : null,
                    prevMonthReport != null ? prevMonthReport.getMonthlyWithdrawal() : null));
            withdrawalTrend.add(withItem);

            DashboardDTO.TrendItem loanItem = new DashboardDTO.TrendItem();
            loanItem.setDate(ym.toString());
            loanItem.setLoan(monthReport != null ? monthReport.getMonthlyLoanIssue() : BigDecimal.ZERO);
            loanItem.setLoanYoY(calcYoY(
                    monthReport != null ? monthReport.getMonthlyLoanIssue() : null,
                    lastYearMonthReport != null ? lastYearMonthReport.getMonthlyLoanIssue() : null));
            loanItem.setLoanMoM(calcMoM(
                    monthReport != null ? monthReport.getMonthlyLoanIssue() : null,
                    prevMonthReport != null ? prevMonthReport.getMonthlyLoanIssue() : null));
            loanTrend.add(loanItem);

            DashboardDTO.TrendItem overdueItem = new DashboardDTO.TrendItem();
            overdueItem.setDate(ym.toString());
            overdueItem.setOverdueRate(monthReport != null ? monthReport.getOverdueRate() : BigDecimal.ZERO);
            overdueItem.setOverdueRateYoY(calcRateChange(
                    monthReport != null ? monthReport.getOverdueRate() : null,
                    lastYearMonthReport != null ? lastYearMonthReport.getOverdueRate() : null));
            overdueItem.setOverdueRateMoM(calcRateChange(
                    monthReport != null ? monthReport.getOverdueRate() : null,
                    prevMonthReport != null ? prevMonthReport.getOverdueRate() : null));
            overdueTrend.add(overdueItem);
        }

        dto.setContributionTrend(contributionTrend);
        dto.setWithdrawalTrend(withdrawalTrend);
        dto.setLoanTrend(loanTrend);
        dto.setOverdueTrend(overdueTrend);

        // --- Overdue Summary ---
        FundReport latestTotal = findLatestReportInMonth(allReports, null, latestYm);
        if (latestTotal != null) {
            DashboardDTO.OverdueSummary os = new DashboardDTO.OverdueSummary();

            LambdaQueryWrapper<LoanAccount> activeLaWrapper = new LambdaQueryWrapper<>();
            activeLaWrapper.ne(LoanAccount::getRepaymentStatus, RepaymentStatusEnum.PAID.getCode());
            Long activeLoanCount = loanAccountMapper.selectCount(activeLaWrapper);
            os.setTotalLoanCount(activeLoanCount != null ? activeLoanCount.intValue() : 0);

            os.setOverdueLoanCount(latestTotal.getOverdueLoanCount());
            os.setTotalLoanBalance(latestTotal.getTotalLoanBalance());
            os.setOverdueLoanAmount(latestTotal.getOverdueLoanAmount());
            os.setOverdueRate(latestTotal.getOverdueRate());

            FundReport prevTotal = findLatestReportInMonth(allReports, null, prevYm);
            FundReport lastYearTotal = findLatestReportInMonth(allReports, null, lastYearYm);
            os.setOverdueRateYoY(calcRateChange(latestTotal.getOverdueRate(),
                    lastYearTotal != null ? lastYearTotal.getOverdueRate() : null));
            os.setOverdueRateMoM(calcRateChange(latestTotal.getOverdueRate(),
                    prevTotal != null ? prevTotal.getOverdueRate() : null));

            dto.setOverdueSummary(os);
        }

        return dto;
    }

    private FundReport findLatestReportInMonth(List<FundReport> reports, Long branchId, YearMonth ym) {
        LocalDate mStart = ym.atDay(1);
        LocalDate mEnd = ym.atEndOfMonth();
        FundReport result = null;
        for (FundReport r : reports) {
            if (branchId == null && !"TOTAL".equals(r.getReportType())) continue;
            if (branchId != null && !branchId.equals(r.getBranchId())) continue;
            if (r.getReportDate().isBefore(mStart) || r.getReportDate().isAfter(mEnd)) continue;
            if (result == null || r.getReportDate().isAfter(result.getReportDate())) {
                result = r;
            }
        }
        return result;
    }

    private BigDecimal calcYoY(BigDecimal current, BigDecimal lastYear) {
        if (current == null || lastYear == null || lastYear.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(lastYear)
                .divide(lastYear, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private BigDecimal calcMoM(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private BigDecimal calcRateChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) return null;
        return current.subtract(previous);
    }
}
