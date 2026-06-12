package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.entity.*;
import com.housingfund.enums.ApprovalStatusEnum;
import com.housingfund.enums.RepaymentStatusEnum;
import com.housingfund.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundReconciliationService {

    private final FundReconciliationMapper reconMapper;
    private final FundReconciliationDiffMapper diffMapper;
    private final FundReportMapper fundReportMapper;
    private final BranchMapper branchMapper;
    private final ContributionDetailMapper contributionDetailMapper;
    private final WithdrawalApplicationMapper withdrawalMapper;
    private final LoanApplicationMapper loanMapper;
    private final RepaymentPlanMapper repaymentPlanMapper;
    private final LoanAccountMapper loanAccountMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<FundReconciliation> runMonthlyReconciliation(String month) {
        List<FundReconciliation> results = new ArrayList<>();
        LambdaQueryWrapper<Branch> bw = new LambdaQueryWrapper<>();
        bw.eq(Branch::getStatus, 1);
        List<Branch> branches = branchMapper.selectList(bw);
        branches.add(0, null);

        for (Branch branch : branches) {
            Long branchId = branch != null ? branch.getId() : null;
            String branchName = branch != null ? branch.getBranchName() : "全辖";
            results.add(reconcileType(month, branchId, branchName, "contribution", "缴存"));
            results.add(reconcileType(month, branchId, branchName, "withdrawal", "提取"));
            results.add(reconcileType(month, branchId, branchName, "loan", "贷款发放"));
            results.add(reconcileType(month, branchId, branchName, "repayment", "还款"));
        }
        log.info("月度资金对账完成，月份={}，共{}条对账记录", month, results.size());
        return results;
    }

    private FundReconciliation reconcileType(String month, Long branchId, String branchName,
                                              String type, String typeName) {
        YearMonth ym = YearMonth.parse(month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        LocalDateTime mStart = monthStart.atStartOfDay();
        LocalDateTime mEnd = monthEnd.atTime(23, 59, 59);

        BigDecimal summary = BigDecimal.ZERO;
        BigDecimal detail = BigDecimal.ZERO;

        LambdaQueryWrapper<FundReport> repWrapper = new LambdaQueryWrapper<>();
        repWrapper.ge(FundReport::getReportDate, monthStart)
                .le(FundReport::getReportDate, monthEnd);
        if (branchId != null) repWrapper.eq(FundReport::getBranchId, branchId);
        else repWrapper.and(w -> w.isNull(FundReport::getBranchId).or().eq(FundReport::getBranchId, 0));
        List<FundReport> reports = fundReportMapper.selectList(repWrapper);

        for (FundReport r : reports) {
            summary = summary.add(switch (type) {
                case "contribution" -> r.getMonthlyContribution() != null ? r.getMonthlyContribution() : BigDecimal.ZERO;
                case "withdrawal" -> r.getMonthlyWithdrawal() != null ? r.getMonthlyWithdrawal() : BigDecimal.ZERO;
                case "loan" -> r.getMonthlyLoanIssue() != null ? r.getMonthlyLoanIssue() : BigDecimal.ZERO;
                case "repayment" -> {
                    BigDecimal repayment = r.getMonthlyRepayment() != null ? r.getMonthlyRepayment() : BigDecimal.ZERO;
                    BigDecimal penalty = r.getMonthlyPenalty() != null ? r.getMonthlyPenalty() : BigDecimal.ZERO;
                    yield repayment.add(penalty);
                }
                default -> BigDecimal.ZERO;
            });
        }

        List<FundReconciliationDiff> diffs = new ArrayList<>();
        switch (type) {
            case "contribution" -> {
                LambdaQueryWrapper<ContributionDetail> cw = new LambdaQueryWrapper<>();
                cw.ge(ContributionDetail::getContributionMonth, monthStart)
                        .le(ContributionDetail::getContributionMonth, monthEnd);
                if (branchId != null) cw.eq(ContributionDetail::getBranchId, branchId);
                cw.eq(ContributionDetail::getStatus, 1);
                List<ContributionDetail> cds = contributionDetailMapper.selectList(cw);
                for (ContributionDetail d : cds) {
                    BigDecimal amt = d.getTotalAmount() != null ? d.getTotalAmount() : BigDecimal.ZERO;
                    detail = detail.add(amt);
                }
            }
            case "withdrawal" -> {
                LambdaQueryWrapper<WithdrawalApplication> ww = new LambdaQueryWrapper<>();
                ww.ge(WithdrawalApplication::getApprovalTime, mStart)
                        .le(WithdrawalApplication::getApprovalTime, mEnd)
                        .eq(WithdrawalApplication::getApprovalStatus, ApprovalStatusEnum.APPROVED.getCode());
                if (branchId != null) ww.eq(WithdrawalApplication::getBranchId, branchId);
                List<WithdrawalApplication> was = withdrawalMapper.selectList(ww);
                for (WithdrawalApplication a : was) {
                    BigDecimal amt = a.getApprovedAmount() != null ? a.getApprovedAmount() : BigDecimal.ZERO;
                    detail = detail.add(amt);
                }
            }
            case "loan" -> {
                LambdaQueryWrapper<LoanApplication> lw = new LambdaQueryWrapper<>();
                lw.ge(LoanApplication::getApprovalTime, mStart)
                        .le(LoanApplication::getApprovalTime, mEnd)
                        .eq(LoanApplication::getApprovalStatus, ApprovalStatusEnum.APPROVED.getCode());
                if (branchId != null) lw.eq(LoanApplication::getBranchId, branchId);
                List<LoanApplication> las = loanMapper.selectList(lw);
                for (LoanApplication a : las) {
                    BigDecimal amt = a.getApprovedAmount() != null ? a.getApprovedAmount() : BigDecimal.ZERO;
                    detail = detail.add(amt);
                }
            }
            case "repayment" -> {
                LambdaQueryWrapper<RepaymentPlan> pw = new LambdaQueryWrapper<>();
                pw.ge(RepaymentPlan::getActualPayTime, mStart)
                        .le(RepaymentPlan::getActualPayTime, mEnd)
                        .eq(RepaymentPlan::getRepaymentStatus, RepaymentStatusEnum.PAID.getCode());
                if (branchId != null) pw.eq(RepaymentPlan::getBranchId, branchId);
                List<RepaymentPlan> plans = repaymentPlanMapper.selectList(pw);
                for (RepaymentPlan p : plans) {
                    BigDecimal amt = (p.getPaidPrincipal() != null ? p.getPaidPrincipal() : BigDecimal.ZERO)
                            .add(p.getPaidInterest() != null ? p.getPaidInterest() : BigDecimal.ZERO)
                            .add(p.getPaidPenalty() != null ? p.getPaidPenalty() : BigDecimal.ZERO);
                    detail = detail.add(amt);
                }
            }
        }

        BigDecimal diff = summary.subtract(detail);
        FundReconciliation recon = new FundReconciliation();
        recon.setReconNo("FR" + IdUtil.getSnowflakeNextIdStr());
        recon.setReconMonth(month);
        recon.setBranchId(branchId);
        recon.setBranchName(branchName);
        recon.setReconType(type);
        recon.setReconTypeName(typeName);
        recon.setSummaryAmount(summary);
        recon.setDetailAmount(detail);
        recon.setDiffAmount(diff.abs());
        recon.setDiffDirection(diff.compareTo(BigDecimal.ZERO) >= 0 ? "SUMMARY_GREATER" : "DETAIL_GREATER");
        recon.setDiffCount(diff.abs().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
        recon.setAutoCause(autoAnalyzeCause(type, diff, summary, detail));
        recon.setHandleStatus(diff.abs().compareTo(new BigDecimal("100")) <= 0 ? "AUTO_CONFIRMED" : "PENDING_REVIEW");
        recon.setStatus(1);
        reconMapper.insert(recon);

        if (diff.abs().compareTo(BigDecimal.ZERO) > 0) {
            FundReconciliationDiff d = new FundReconciliationDiff();
            d.setReconId(recon.getId());
            d.setReconNo(recon.getReconNo());
            d.setReconMonth(month);
            d.setBranchId(branchId);
            d.setDiffType("GLOBAL");
            d.setSummaryAmount(summary);
            d.setDetailAmount(detail);
            d.setDiffAmount(diff);
            d.setDiffReason(recon.getAutoCause());
            d.setHandleStatus(recon.getHandleStatus());
            d.setBusinessDate(monthStart);
            d.setStatus(1);
            diffMapper.insert(d);
        }
        return recon;
    }

    private String autoAnalyzeCause(String type, BigDecimal diff, BigDecimal summary, BigDecimal detail) {
        if (diff.abs().compareTo(BigDecimal.ZERO) == 0) return "无差异，汇总数与明细数一致";
        BigDecimal pct = summary.compareTo(BigDecimal.ZERO) != 0
                ? diff.abs().divide(summary, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
        String base = String.format("差异%.2f元，占汇总数的%.4f%%", diff.abs(), pct);
        if (diff.abs().compareTo(new BigDecimal("100")) <= 0) {
            return base + "，属于尾差/四舍五入范围内，自动确认";
        }
        if (pct.compareTo(new BigDecimal("1")) > 0) {
            return base + "，差异超过1%，建议排查报表生成时间与明细入账时间不一致或漏统计";
        }
        return base + "，建议人工复核：检查是否有跨月入账、冲正、状态不一致的业务记录";
    }

    public Page<FundReconciliation> queryReconciliations(String month, Long branchId, String reconType,
                                                           String handleStatus, int pageNum, int pageSize) {
        LambdaQueryWrapper<FundReconciliation> w = new LambdaQueryWrapper<>();
        if (month != null) w.eq(FundReconciliation::getReconMonth, month);
        if (branchId != null) w.eq(FundReconciliation::getBranchId, branchId);
        if (reconType != null) w.eq(FundReconciliation::getReconType, reconType);
        if (handleStatus != null) w.eq(FundReconciliation::getHandleStatus, handleStatus);
        w.orderByDesc(FundReconciliation::getReconMonth);
        return reconMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    public List<FundReconciliation> getReconciliations(String month, Long branchId) {
        if (branchId != null) return reconMapper.findByMonthAndBranch(month, branchId);
        return reconMapper.findByMonth(month);
    }

    public FundReconciliation getReconDetail(Long reconId) {
        FundReconciliation r = reconMapper.selectById(reconId);
        if (r == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "对账记录不存在");
        return r;
    }

    public List<FundReconciliationDiff> getReconDiffs(Long reconId) {
        return diffMapper.findByReconId(reconId);
    }

    @Transactional(rollbackFor = Exception.class)
    public FundReconciliation handleReconciliation(Long reconId, String handleStatus,
                                                     String manualCause, String handleRemark,
                                                     Long handlerId, String handlerName) {
        FundReconciliation r = reconMapper.selectById(reconId);
        if (r == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "对账记录不存在");
        r.setHandleStatus(handleStatus);
        r.setManualCause(manualCause);
        r.setHandleRemark(handleRemark);
        r.setHandlerId(handlerId);
        r.setHandlerName(handlerName);
        r.setHandleTime(LocalDateTime.now());
        reconMapper.updateById(r);

        LambdaQueryWrapper<FundReconciliationDiff> dw = new LambdaQueryWrapper<>();
        dw.eq(FundReconciliationDiff::getReconId, reconId);
        List<FundReconciliationDiff> diffs = diffMapper.selectList(dw);
        for (FundReconciliationDiff d : diffs) {
            d.setHandleStatus(handleStatus);
            d.setHandleRemark(handleRemark);
            diffMapper.updateById(d);
        }
        log.info("对账记录处理完成: reconNo={}, status={}", r.getReconNo(), handleStatus);
        return r;
    }
}
