package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.EarlyRepaymentDTO;
import com.housingfund.dto.EarlyRepaymentResultDTO;
import com.housingfund.dto.RepaymentDTO;
import com.housingfund.dto.RepaymentPlanResultDTO;
import com.housingfund.entity.CollectionTask;
import com.housingfund.entity.LoanAccount;
import com.housingfund.entity.RepaymentPlan;
import com.housingfund.service.RepaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "还款计划与催收", description = "还款计划生成、还款处理、扣款提醒、逾期罚息、催收任务")
@RestController
@RequestMapping("/repayment")
@RequiredArgsConstructor
public class RepaymentController {

    private final RepaymentService repaymentService;

    @Operation(summary = "预览还款计划", description = "按等额本息或等额本金预览每期还款明细")
    @GetMapping("/plan/preview")
    public Result<RepaymentPlanResultDTO> previewPlan(
            @Parameter(description = "贷款本金") @RequestParam BigDecimal loanAmount,
            @Parameter(description = "年利率（如0.031表示3.1%）") @RequestParam BigDecimal annualRate,
            @Parameter(description = "贷款期限（月）") @RequestParam Integer termMonths,
            @Parameter(description = "还款方式：EQUAL_INSTALLMENT等额本息 EQUAL_PRINCIPAL等额本金")
                @RequestParam(defaultValue = "EQUAL_INSTALLMENT") String method,
            @Parameter(description = "首期还款日") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate) {
        return Result.success(repaymentService.previewRepaymentPlan(
                loanAmount, annualRate, termMonths, method, startDate));
    }

    @Operation(summary = "查询贷款账户明细")
    @GetMapping("/account/{loanAccountId}")
    public Result<LoanAccount> getLoanAccount(
            @Parameter(description = "贷款账户ID") @PathVariable Long loanAccountId) {
        return Result.success(repaymentService.getLoanAccountDetail(loanAccountId));
    }

    @Operation(summary = "查询还款计划明细")
    @GetMapping("/plan/list/{loanAccountId}")
    public Result<List<RepaymentPlan>> getRepaymentPlans(
            @Parameter(description = "贷款账户ID") @PathVariable Long loanAccountId) {
        return Result.success(repaymentService.getRepaymentPlans(loanAccountId));
    }

    @Operation(summary = "处理还款", description = "月度扣款、逾期自动计算罚息")
    @PostMapping("/pay")
    public Result<String> processRepayment(
            @RequestBody RepaymentDTO dto) {
        repaymentService.processRepayment(dto);
        return Result.success("还款处理成功");
    }

    @Operation(summary = "手动触发还款提醒", description = "每日9点自动推送前3天到期的还款提醒")
    @PostMapping("/send-reminders")
    public Result<Integer> sendRepaymentReminders() {
        int count = repaymentService.processRepaymentReminders();
        return Result.success(String.format("还款提醒推送完成，共%d条", count), count);
    }

    @Operation(summary = "手动触发逾期及催收处理", description = "每日凌晨自动执行")
    @PostMapping("/process-overdue")
    public Result<Integer> processOverdue() {
        int count = repaymentService.processOverdueAndCollection();
        return Result.success(String.format("逾期处理完成，共处理%d个账户", count), count);
    }

    @Operation(summary = "查询催收任务列表")
    @GetMapping("/collection/list")
    public Result<Page<CollectionTask>> queryCollectionTasks(
            @Parameter(description = "信贷员ID") @RequestParam(required = false) Long assigneeId,
            @Parameter(description = "任务状态：0待处理 1处理中 2已完成")
                @RequestParam(required = false) Integer taskStatus,
            @Parameter(description = "任务等级：1初级 2中级 3高级")
                @RequestParam(required = false) Integer taskLevel,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        Page<CollectionTask> page = repaymentService.queryCollectionTasks(
                assigneeId, taskStatus, taskLevel, pageNum, pageSize);
        return Result.success(page);
    }

    @Operation(summary = "提前还款", description = "全额提前还款或部分提前还款，自动重新计算剩余期数/月供/利息")
    @PostMapping("/early-repayment")
    public Result<EarlyRepaymentResultDTO> processEarlyRepayment(@RequestBody EarlyRepaymentDTO dto) {
        return Result.success(repaymentService.processEarlyRepayment(dto));
    }

    @Operation(summary = "提前还款预览", description = "预览提前还款后的新还款计划，不实际执行")
    @PostMapping("/early-repayment/preview")
    public Result<EarlyRepaymentResultDTO> previewEarlyRepayment(@RequestBody EarlyRepaymentDTO dto) {
        return Result.success(repaymentService.previewEarlyRepayment(dto));
    }
}
