package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.dto.AuditReviewReportDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.ApprovalStatusEnum;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.enums.RiskAlertStatusEnum;
import com.housingfund.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditReportService {

    private final LoanApplicationMapper loanMapper;
    private final LoanRiskScoreDetailMapper riskScoreMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final RepaymentPlanMapper repaymentPlanMapper;
    private final NotificationRecordMapper notificationMapper;
    private final RiskAlertEventMapper alertEventMapper;
    private final EmployeeMapper employeeMapper;
    private final CompanyMapper companyMapper;
    private final BranchMapper branchMapper;
    private final ContributionDeclarationMapper contributionDeclarationMapper;
    private final WithdrawalApplicationMapper withdrawalMapper;
    private final BusinessAuditLogMapper auditLogMapper;

    public AuditReviewReportDTO generateAuditReport(String businessNo) {
        AuditReviewReportDTO report = new AuditReviewReportDTO();
        report.setReportNo("AR" + IdUtil.getSnowflakeNextIdStr());
        report.setBusinessNo(businessNo);
        report.setReportGenerateTime(LocalDateTime.now());

        LoanApplication loan = null;
        ContributionDeclaration declaration = null;
        WithdrawalApplication withdrawal = null;

        loan = findLoanByNo(businessNo);
        if (loan != null) {
            fillLoanReport(report, loan);
        } else {
            declaration = findDeclarationByNo(businessNo);
            if (declaration != null) {
                fillContributionReport(report, declaration);
            } else {
                withdrawal = findWithdrawalByNo(businessNo);
                if (withdrawal != null) {
                    fillWithdrawalReport(report, withdrawal);
                } else {
                    throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "未找到业务单号对应的记录");
                }
            }
        }

        fillApprovalSection(report);
        fillNotificationSection(report);
        fillRiskSection(report);
        fillFinalConclusion(report);
        return report;
    }

    private LoanApplication findLoanByNo(String no) {
        LambdaQueryWrapper<LoanApplication> w = new LambdaQueryWrapper<>();
        w.eq(LoanApplication::getApplicationNo, no);
        List<LoanApplication> list = loanMapper.selectList(w);
        return list.isEmpty() ? null : list.get(0);
    }

    private ContributionDeclaration findDeclarationByNo(String no) {
        LambdaQueryWrapper<ContributionDeclaration> w = new LambdaQueryWrapper<>();
        w.eq(ContributionDeclaration::getDeclarationNo, no);
        List<ContributionDeclaration> list = contributionDeclarationMapper.selectList(w);
        return list.isEmpty() ? null : list.get(0);
    }

    private WithdrawalApplication findWithdrawalByNo(String no) {
        LambdaQueryWrapper<WithdrawalApplication> w = new LambdaQueryWrapper<>();
        w.eq(WithdrawalApplication::getApplicationNo, no);
        List<WithdrawalApplication> list = withdrawalMapper.selectList(w);
        return list.isEmpty() ? null : list.get(0);
    }

    private void fillLoanReport(AuditReviewReportDTO dto, LoanApplication loan) {
        dto.setBusinessType("loan");
        dto.setBusinessTypeName("贷款申请");
        dto.setApplicationAmount(loan.getApplicationAmount());
        dto.setApprovalStatus(mapApprovalStatus(loan.getApprovalStatus()));

        Employee emp = employeeMapper.selectById(loan.getEmployeeId());
        if (emp != null) {
            dto.setEmployeeName(emp.getName());
            dto.setEmployeeIdCard(emp.getIdCard());
        }
        Branch br = branchMapper.selectById(loan.getBranchId());
        if (br != null) dto.setBranchName(br.getBranchName());

        AuditReviewReportDTO.PreAuditSection pre = new AuditReviewReportDTO.PreAuditSection();
        pre.setPreAuditReport(loan.getPreAuditReport());
        pre.setMaxLoanableAmount(loan.getMaxLoanableAmount());

        List<LoanRiskScoreDetail> details = riskScoreMapper.selectList(
                new LambdaQueryWrapper<LoanRiskScoreDetail>()
                        .eq(LoanRiskScoreDetail::getLoanApplicationId, loan.getId())
                        .orderByAsc(LoanRiskScoreDetail::getSortOrder));
        List<AuditReviewReportDTO.RiskScoreItem> items = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        for (LoanRiskScoreDetail d : details) {
            AuditReviewReportDTO.RiskScoreItem it = new AuditReviewReportDTO.RiskScoreItem();
            it.setDimensionCode(d.getDimensionCode());
            it.setDimensionName(d.getDimensionName());
            it.setFullScore(d.getFullScore());
            it.setActualScore(d.getActualScore());
            it.setDeduction(d.getDeduction());
            it.setDeductionReason(d.getDeductionReason());
            items.add(it);
            if (d.getActualScore() != null) {
                totalScore = totalScore.add(d.getActualScore());
            }
        }
        pre.setRiskScores(items);
        pre.setRiskTotalScore(totalScore);
        dto.setPreAudit(pre);

        if (ApprovalStatusEnum.APPROVED.getCode().equals(loan.getApprovalStatus())) {
            fillLoanRepaymentSection(dto, loan);
        }
    }

    private void fillLoanRepaymentSection(AuditReviewReportDTO dto, LoanApplication loan) {
        AuditReviewReportDTO.RepaymentSection repay = new AuditReviewReportDTO.RepaymentSection();
        LambdaQueryWrapper<LoanAccount> w = new LambdaQueryWrapper<>();
        w.eq(LoanAccount::getLoanApplicationId, loan.getId());
        List<LoanAccount> accounts = loanAccountMapper.selectList(w);
        if (!accounts.isEmpty()) {
            LoanAccount acc = accounts.get(0);
            repay.setApprovedAmount(acc.getLoanAmount());
            repay.setInterestRate(acc.getInterestRate());
            repay.setLoanTermMonths(acc.getLoanTerm());
            repay.setRepaymentMethod("1".equals(acc.getRepaymentMethod()) ? "等额本息" : "等额本金");
            repay.setRemainingPrincipal(acc.getRemainingPrincipal());
            repay.setPaidTerms(acc.getPaidTerm());
            repay.setRemainingTerms(acc.getLoanTerm() != null && acc.getPaidTerm() != null
                    ? acc.getLoanTerm() - acc.getPaidTerm() : acc.getLoanTerm());
            repay.setTotalPaid(acc.getPaidPrincipal() != null
                    ? acc.getPaidPrincipal().add(acc.getPaidInterest() != null ? acc.getPaidInterest() : BigDecimal.ZERO)
                    : BigDecimal.ZERO);

            LambdaQueryWrapper<RepaymentPlan> pw = new LambdaQueryWrapper<>();
            pw.eq(RepaymentPlan::getLoanAccountId, acc.getId())
                    .orderByAsc(RepaymentPlan::getTermNo)
                    .last("LIMIT 24");
            List<RepaymentPlan> plans = repaymentPlanMapper.selectList(pw);
            List<AuditReviewReportDTO.RepaymentPlanItem> planItems = new ArrayList<>();
            List<String> exceptions = new ArrayList<>();
            for (RepaymentPlan p : plans) {
                AuditReviewReportDTO.RepaymentPlanItem pi = new AuditReviewReportDTO.RepaymentPlanItem();
                pi.setTermNo(p.getTermNo());
                pi.setDueDate(p.getDueDate());
                pi.setPrincipalAmount(p.getPrincipalAmount());
                pi.setInterestAmount(p.getInterestAmount());
                pi.setPenaltyAmount(p.getPenaltyAmount());
                pi.setStatus(mapRepaymentStatus(p.getRepaymentStatus()));
                planItems.add(pi);
                if (p.getOverdueDays() != null && p.getOverdueDays() > 0) {
                    exceptions.add(String.format("第%d期逾期%d天，罚息%.2f元",
                            p.getTermNo(), p.getOverdueDays(), p.getPenaltyAmount()));
                }
            }
            repay.setRecentPlans(planItems);
            repay.setExceptions(exceptions);
        }
        dto.setRepayment(repay);
    }

    private void fillContributionReport(AuditReviewReportDTO dto, ContributionDeclaration d) {
        dto.setBusinessType("contribution");
        dto.setBusinessTypeName("单位缴存申报");
        dto.setApplicationAmount(d.getTotalAmount());
        dto.setApprovalStatus(mapApprovalStatus(d.getApprovalStatus()));
        Company c = companyMapper.selectById(d.getCompanyId());
        if (c != null) {
            dto.setCompanyName(c.getCompanyName());
        }
        Branch br = branchMapper.selectById(d.getBranchId());
        if (br != null) dto.setBranchName(br.getBranchName());
    }

    private void fillWithdrawalReport(AuditReviewReportDTO dto, WithdrawalApplication w) {
        dto.setBusinessType("withdrawal");
        dto.setBusinessTypeName("个人提取申请");
        dto.setApplicationAmount(w.getApplicationAmount());
        dto.setApprovalStatus(mapApprovalStatus(w.getApprovalStatus()));
        Employee emp = employeeMapper.selectById(w.getEmployeeId());
        if (emp != null) {
            dto.setEmployeeName(emp.getName());
            dto.setEmployeeIdCard(emp.getIdCard());
        }
        Branch br = branchMapper.selectById(w.getBranchId());
        if (br != null) dto.setBranchName(br.getBranchName());
    }

    private void fillApprovalSection(AuditReviewReportDTO dto) {
        List<ApprovalRecord> records = approvalRecordMapper.findByBusinessNo(dto.getBusinessNo());
        if (records == null || records.isEmpty()) return;

        AuditReviewReportDTO.ApprovalSection appr = new AuditReviewReportDTO.ApprovalSection();
        appr.setTotalLevels(records.isEmpty() ? 0 : records.get(0).getTotalLevels());
        appr.setHasTimeoutEscalation(false);

        List<AuditReviewReportDTO.ApprovalStep> steps = new ArrayList<>();
        for (ApprovalRecord r : records) {
            AuditReviewReportDTO.ApprovalStep step = new AuditReviewReportDTO.ApprovalStep();
            step.setLevel(r.getApprovalLevel());
            step.setApproverName(r.getApproverName());
            step.setApproverRoleName(r.getApproverRoleName());
            step.setResult(r.getApprovalResult() != null ? mapApprovalResult(r.getApprovalResult()) : "待处理");
            step.setComment(r.getApprovalComment());
            step.setActionTime(r.getApprovalTime() != null ? r.getApprovalTime() : r.getCreateTime());
            step.setIsTimeoutEscalated(Boolean.TRUE.equals(r.getTimeoutEscalated()));
            if (Boolean.TRUE.equals(r.getTimeoutEscalated())) appr.setHasTimeoutEscalation(true);
            steps.add(step);
            if (r.getRuleVersion() != null && appr.getRuleVersion() == null) appr.setRuleVersion(r.getRuleVersion());
            if (r.getRuleSnapshot() != null && appr.getRuleSnapshot() == null) appr.setRuleSnapshot(r.getRuleSnapshot());
        }
        appr.setSteps(steps);
        dto.setApproval(appr);
    }

    private void fillNotificationSection(AuditReviewReportDTO dto) {
        LambdaQueryWrapper<NotificationRecord> w = new LambdaQueryWrapper<>();
        w.eq(NotificationRecord::getBusinessNo, dto.getBusinessNo());
        w.orderByDesc(NotificationRecord::getCreateTime).last("LIMIT 50");
        List<NotificationRecord> notifs = notificationMapper.selectList(w);

        AuditReviewReportDTO.NotificationSection n = new AuditReviewReportDTO.NotificationSection();
        n.setTotalSent(notifs.size());
        List<AuditReviewReportDTO.NotificationItem> items = new ArrayList<>();
        for (NotificationRecord nr : notifs) {
            AuditReviewReportDTO.NotificationItem it = new AuditReviewReportDTO.NotificationItem();
            it.setType(nr.getNotificationType() != null ? String.valueOf(nr.getNotificationType()) : "");
            it.setTypeName(mapNotificationType(nr.getNotificationType()));
            it.setTitle(nr.getTitle());
            it.setContent(nr.getContent());
            it.setSentTime(nr.getCreateTime());
            it.setReceiver(nr.getReceiverName());
            items.add(it);
        }
        n.setItems(items);
        dto.setNotification(n);
    }

    private void fillRiskSection(AuditReviewReportDTO dto) {
        List<RiskAlertEvent> alerts = alertEventMapper.findByBusinessNo(dto.getBusinessNo());
        AuditReviewReportDTO.RiskSection r = new AuditReviewReportDTO.RiskSection();
        r.setAlertCount(alerts.size());
        List<AuditReviewReportDTO.AlertItem> items = new ArrayList<>();
        for (RiskAlertEvent a : alerts) {
            AuditReviewReportDTO.AlertItem it = new AuditReviewReportDTO.AlertItem();
            it.setAlertNo(a.getAlertNo());
            it.setAlertType(a.getAlertType());
            it.setAlertTypeName(a.getAlertTypeName());
            it.setAlertLevel(a.getAlertLevel());
            it.setRuleVersion(a.getRuleVersion());
            it.setRuleDescription(a.getRuleDescription());
            it.setRulePublishInfo(a.getRulePublishInfo());
            it.setStatus(mapAlertStatus(a.getAlertStatus()));
            it.setCreateTime(a.getCreateTime());
            it.setHandleResult(a.getHandleResult());
            items.add(it);
        }
        r.setAlerts(items);
        dto.setRisk(r);
    }

    private void fillFinalConclusion(AuditReviewReportDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("业务类型:").append(dto.getBusinessTypeName()).append("; ");
        sb.append("申请金额:").append(dto.getApplicationAmount() != null ? dto.getApplicationAmount().setScale(2) : "0.00").append("元; ");
        sb.append("审批状态:").append(dto.getApprovalStatus() != null ? dto.getApprovalStatus() : "未知").append("; ");
        if (dto.getApproval() != null) {
            sb.append("审批层级:").append(dto.getApproval().getTotalLevels()).append("级; ");
            if (Boolean.TRUE.equals(dto.getApproval().getHasTimeoutEscalation())) sb.append("存在超时转交; ");
        }
        if (dto.getRisk() != null) sb.append("风险预警:").append(dto.getRisk().getAlertCount()).append("条; ");
        if (dto.getNotification() != null) sb.append("消息推送:").append(dto.getNotification().getTotalSent()).append("条; ");
        if (dto.getRepayment() != null && dto.getRepayment().getExceptions() != null
                && !dto.getRepayment().getExceptions().isEmpty()) {
            sb.append("还款异常:").append(dto.getRepayment().getExceptions().size()).append("项");
        }
        dto.setFinalConclusion(sb.toString());
    }

    private String mapApprovalStatus(Integer s) {
        if (s == null) return "未知";
        for (ApprovalStatusEnum e : ApprovalStatusEnum.values()) {
            if (e.getCode().equals(s)) return e.getDesc();
        }
        return "状态" + s;
    }

    private String mapApprovalResult(Integer s) {
        if (s == null) return "待处理";
        return switch (s) {
            case 0 -> "待审批";
            case 1 -> "审批中";
            case 2 -> "通过";
            case 3 -> "驳回";
            case 4 -> "超时转办";
            default -> "未知";
        };
    }

    private String mapRepaymentStatus(Integer s) {
        if (s == null) return "待还款";
        return switch (s) {
            case 0 -> "待还款";
            case 1 -> "已正常还款";
            case 2 -> "已结清";
            case 3 -> "逾期";
            case 4 -> "部分还款";
            default -> "未知";
        };
    }

    private String mapNotificationType(Integer t) {
        if (t == null) return "未知";
        for (NotificationTypeEnum e : NotificationTypeEnum.values()) {
            if (e.getCode().equals(t)) return e.getDesc();
        }
        return String.valueOf(t);
    }

    private String mapAlertStatus(String s) {
        if (s == null) return "未知";
        for (RiskAlertStatusEnum e : RiskAlertStatusEnum.values()) {
            if (e.getCode().equals(s)) return e.getDesc();
        }
        return s;
    }

    public byte[] exportAuditReportExcel(String businessNo) {
        AuditReviewReportDTO report = generateAuditReport(businessNo);

        List<Map<String, Object>> rows = new ArrayList<>();
        addReportRow(rows, "稽核报告编号", report.getReportNo());
        addReportRow(rows, "业务单号", report.getBusinessNo());
        addReportRow(rows, "业务类型", report.getBusinessTypeName());
        addReportRow(rows, "申请人/单位",
                (report.getEmployeeName() != null ? report.getEmployeeName() : "")
                        + (report.getCompanyName() != null ? "/" + report.getCompanyName() : ""));
        addReportRow(rows, "所属分支机构", report.getBranchName() != null ? report.getBranchName() : "");
        addReportRow(rows, "申请金额(元)", report.getApplicationAmount() != null ? report.getApplicationAmount().setScale(2) : "0.00");
        addReportRow(rows, "审批状态", report.getApprovalStatus() != null ? report.getApprovalStatus() : "");
        addReportRow(rows, "报告生成时间", report.getReportGenerateTime());
        addReportRow(rows, "", "=== 以上为基本信息 ===");

        if (report.getPreAudit() != null) {
            addReportRow(rows, "预审风控总分", report.getPreAudit().getRiskTotalScore());
            if (report.getPreAudit().getRiskScores() != null) {
                for (AuditReviewReportDTO.RiskScoreItem s : report.getPreAudit().getRiskScores()) {
                    addReportRow(rows, "  " + s.getDimensionName(),
                            String.format("满分%.0f/实得%.0f/扣%.0f分 - %s",
                                    s.getFullScore(), s.getActualScore(), s.getDeduction(),
                                    s.getDeductionReason() != null ? s.getDeductionReason() : ""));
                }
            }
            addReportRow(rows, "最高可贷额度(元)", report.getPreAudit().getMaxLoanableAmount());
            addReportRow(rows, "利率推算", report.getPreAudit().getRateTrace());
            addReportRow(rows, "", "=== 以上为预审信息 ===");
        }

        if (report.getApproval() != null) {
            addReportRow(rows, "使用审批规则版本", report.getApproval().getRuleVersion() != null ? report.getApproval().getRuleVersion() : "默认");
            addReportRow(rows, "规则快照", report.getApproval().getRuleSnapshot() != null ? report.getApproval().getRuleSnapshot() : "");
            addReportRow(rows, "存在超时转交", Boolean.TRUE.equals(report.getApproval().getHasTimeoutEscalation()) ? "是" : "否");
            if (report.getApproval().getSteps() != null) {
                for (AuditReviewReportDTO.ApprovalStep st : report.getApproval().getSteps()) {
                    addReportRow(rows, String.format("  第%d级%s", st.getLevel(), st.getApproverRoleName() != null ? "(" + st.getApproverRoleName() + ")" : ""),
                            String.format("审批人:%s 结果:%s 意见:%s 时间:%s%s",
                                    st.getApproverName() != null ? st.getApproverName() : "-",
                                    st.getResult(),
                                    st.getComment() != null ? st.getComment() : "无",
                                    st.getActionTime(),
                                    Boolean.TRUE.equals(st.getIsTimeoutEscalated()) ? " [超时转交]" : ""));
                }
            }
            addReportRow(rows, "", "=== 以上为审批信息 ===");
        }

        if (report.getRepayment() != null) {
            addReportRow(rows, "批准金额(元)", report.getRepayment().getApprovedAmount());
            addReportRow(rows, "利率", report.getRepayment().getInterestRate());
            addReportRow(rows, "期限(月)", report.getRepayment().getLoanTermMonths());
            addReportRow(rows, "还款方式", report.getRepayment().getRepaymentMethod());
            addReportRow(rows, "已还期数/剩余期数", String.format("%d/%d",
                    report.getRepayment().getPaidTerms() != null ? report.getRepayment().getPaidTerms() : 0,
                    report.getRepayment().getRemainingTerms() != null ? report.getRepayment().getRemainingTerms() : 0));
            addReportRow(rows, "剩余本金(元)", report.getRepayment().getRemainingPrincipal());
            if (report.getRepayment().getExceptions() != null && !report.getRepayment().getExceptions().isEmpty()) {
                for (String ex : report.getRepayment().getExceptions()) {
                    addReportRow(rows, "  还款异常", ex);
                }
            }
            addReportRow(rows, "", "=== 以上为还款信息 ===");
        }

        if (report.getNotification() != null) {
            addReportRow(rows, "消息推送总数", report.getNotification().getTotalSent());
            if (report.getNotification().getItems() != null) {
                for (AuditReviewReportDTO.NotificationItem ni : report.getNotification().getItems()) {
                    addReportRow(rows, String.format("  [%s]%s", ni.getTypeName(), ni.getTitle()),
                            String.format("接收人:%s 时间:%s 内容:%s",
                                    ni.getReceiver(), ni.getSentTime(),
                                    ni.getContent() != null && ni.getContent().length() > 50
                                            ? ni.getContent().substring(0, 50) + "..." : ni.getContent()));
                }
            }
            addReportRow(rows, "", "=== 以上为消息触达 ===");
        }

        if (report.getRisk() != null) {
            addReportRow(rows, "风险预警总数", report.getRisk().getAlertCount());
            if (report.getRisk().getAlerts() != null) {
                for (AuditReviewReportDTO.AlertItem ai : report.getRisk().getAlerts()) {
                    addReportRow(rows, String.format("  [%s]%s(%s)", ai.getAlertLevel(), ai.getAlertTypeName(), ai.getRuleVersion() != null ? ai.getRuleVersion() : ""),
                            String.format("规则:%s 状态:%s 处理结果:%s",
                                    ai.getRuleDescription() != null ? ai.getRuleDescription() : "-",
                                    ai.getStatus(),
                                    ai.getHandleResult() != null ? ai.getHandleResult() : "未处理"));
                }
            }
            addReportRow(rows, "", "=== 以上为风险预警 ===");
        }

        addReportRow(rows, "稽核结论", report.getFinalConclusion());

        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(rows, true);
        writer.setColumnWidth(0, 30);
        writer.setColumnWidth(1, 80);
        writer.autoSizeColumn(1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.flush(out, true);
        writer.close();
        return out.toByteArray();
    }

    private void addReportRow(List<Map<String, Object>> rows, String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("项目", key);
        row.put("内容", value != null ? value.toString() : "");
        rows.add(row);
    }
}
