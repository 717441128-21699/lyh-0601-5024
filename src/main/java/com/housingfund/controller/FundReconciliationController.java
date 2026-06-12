package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.entity.FundReconciliation;
import com.housingfund.entity.FundReconciliationDiff;
import com.housingfund.service.FundReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "资金对账自动复核", description = "按月比对机构汇总数与业务明细数差异，自动归因分类，支持已确认/需调整/转人工复核")
@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class FundReconciliationController {

    private final FundReconciliationService reconciliationService;

    @Operation(summary = "执行月度资金对账", description = "按机构对比缴存/提取/贷款/还款4类业务的汇总vs明细，生成差异清单并自动归因")
    @PostMapping("/run")
    public Result<Integer> runMonthlyReconciliation(
            @Parameter(description = "对账月份(yyyy-MM)") @RequestParam String month) {
        List<FundReconciliation> list = reconciliationService.runMonthlyReconciliation(month);
        long diffCount = list.stream().filter(r -> (r.getDiffAmount() != null && r.getDiffAmount().compareTo(new java.math.BigDecimal("100")) > 0)).count();
        return Result.success(String.format("月度对账完成：共%d条，其中差异%d条", list.size(), diffCount), list.size());
    }

    @Operation(summary = "分页查询对账记录", description = "支持按月份、机构、业务类型、处理状态筛选")
    @GetMapping("/list")
    public Result<Page<FundReconciliation>> queryReconciliations(
            @Parameter(description = "对账月份(yyyy-MM)") @RequestParam(required = false) String month,
            @Parameter(description = "分支机构ID") @RequestParam(required = false) Long branchId,
            @Parameter(description = "业务类型: contribution/withdrawal/loan/repayment") @RequestParam(required = false) String reconType,
            @Parameter(description = "处理状态: AUTO_CONFIRMED自动确认 PENDING_REVIEW待人工 CONFIRMED已确认 NEED_ADJUST需调整 ESCALATED转人工复核") @RequestParam(required = false) String handleStatus,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(reconciliationService.queryReconciliations(month, branchId, reconType, handleStatus, pageNum, pageSize));
    }

    @Operation(summary = "某月对账概览")
    @GetMapping("/summary")
    public Result<List<FundReconciliation>> getReconciliations(
            @Parameter(description = "对账月份(yyyy-MM)") @RequestParam String month,
            @Parameter(description = "分支机构ID") @RequestParam(required = false) Long branchId) {
        return Result.success(reconciliationService.getReconciliations(month, branchId));
    }

    @Operation(summary = "对账记录详情")
    @GetMapping("/detail/{reconId}")
    public Result<FundReconciliation> getReconDetail(@PathVariable Long reconId) {
        return Result.success(reconciliationService.getReconDetail(reconId));
    }

    @Operation(summary = "差异明细清单", description = "点进对账记录查看具体差异明细")
    @GetMapping("/diff/{reconId}")
    public Result<List<FundReconciliationDiff>> getReconDiffs(@PathVariable Long reconId) {
        return Result.success(reconciliationService.getReconDiffs(reconId));
    }

    @Operation(summary = "处理对账差异", description = "标记为已确认/需调整/转人工复核，填写人工归因和备注")
    @PostMapping("/handle")
    public Result<FundReconciliation> handleReconciliation(
            @Parameter(description = "对账记录ID") @RequestParam Long reconId,
            @Parameter(description = "处理状态: CONFIRMED/NEED_ADJUST/ESCALATED") @RequestParam String handleStatus,
            @Parameter(description = "人工归因") @RequestParam(required = false) String manualCause,
            @Parameter(description = "处理备注") @RequestParam(required = false) String handleRemark,
            @Parameter(description = "处理人ID") @RequestParam(required = false) Long handlerId,
            @Parameter(description = "处理人姓名") @RequestParam(required = false) String handlerName) {
        FundReconciliation r = reconciliationService.handleReconciliation(
                reconId, handleStatus, manualCause, handleRemark, handlerId, handlerName);
        return Result.success("对账差异处理完成", r);
    }
}
