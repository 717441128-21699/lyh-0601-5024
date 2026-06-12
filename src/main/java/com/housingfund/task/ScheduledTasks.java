package com.housingfund.task;

import com.housingfund.service.ApprovalService;
import com.housingfund.service.FundReportService;
import com.housingfund.service.RepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final FundReportService fundReportService;
    private final RepaymentService repaymentService;
    private final ApprovalService approvalService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void generateDailyFundReport() {
        log.info("========== 开始执行每日资金报表生成任务 ==========");
        try {
            LocalDate reportDate = LocalDate.now().minusDays(1);
            fundReportService.generateDailyReport(reportDate);
            log.info("每日资金报表生成成功，报告日期：{}", reportDate);
        } catch (Exception e) {
            log.error("每日资金报表生成失败", e);
        }
        log.info("========== 每日资金报表生成任务执行完毕 ==========");
    }

    @Scheduled(cron = "0 0 9 * * ?")
    public void sendRepaymentReminders() {
        log.info("========== 开始执行还款提醒推送任务 ==========");
        try {
            int count = repaymentService.processRepaymentReminders();
            log.info("还款提醒推送完成，共推送{}条", count);
        } catch (Exception e) {
            log.error("还款提醒推送失败", e);
        }
        log.info("========== 还款提醒推送任务执行完毕 ==========");
    }

    @Scheduled(cron = "0 10 0 * * ?")
    public void processOverdueLoans() {
        log.info("========== 开始执行逾期贷款及催收任务 ==========");
        try {
            int count = repaymentService.processOverdueAndCollection();
            log.info("逾期贷款及催收处理完成，共处理{}个逾期账户", count);
        } catch (Exception e) {
            log.error("逾期贷款及催收处理失败", e);
        }
        log.info("========== 逾期贷款及催收任务执行完毕 ==========");
    }

    @Scheduled(cron = "0 */30 * * * ?")
    public void processApprovalEscalations() {
        log.debug("开始执行审批超时转办检查");
        try {
            int count = approvalService.processTimeoutEscalations();
            if (count > 0) {
                log.info("审批超时转办处理完成，共转办{}条", count);
            }
        } catch (Exception e) {
            log.error("审批超时转办处理失败", e);
        }
    }

    @Scheduled(cron = "0 5 1 * * ?")
    public void monthlyAutoContribution() {
        log.info("========== 开始执行月度自动缴存任务 ==========");
        try {
            LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
            log.info("月度自动缴存任务准备就绪，缴存月份：{}", thisMonth);
        } catch (Exception e) {
            log.error("月度自动缴存任务失败", e);
        }
        log.info("========== 月度自动缴存任务执行完毕 ==========");
    }
}
