package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.LoanApplyDTO;
import com.housingfund.dto.LoanPreAuditResultDTO;
import com.housingfund.entity.LoanApplication;
import com.housingfund.entity.LoanRiskScoreDetail;
import com.housingfund.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "贷款申请", description = "连续缴存校验、信用评分、最高额度计算、预审报告生成")
@RestController
@RequestMapping("/loan")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @Operation(summary = "贷款预审", description = "根据连续缴存记录、信用评分和房屋评估价自动计算最高可贷额度与利率，生成预审报告")
    @PostMapping("/pre-audit")
    public Result<LoanPreAuditResultDTO> preAuditLoan(
            @Valid @RequestBody LoanApplyDTO dto) {
        LoanPreAuditResultDTO result = loanService.preAuditLoan(dto);
        String msg = result.getEligible()
                ? String.format("预审通过，最高可贷%.2f元，执行利率%.2f%%",
                    result.getMaxLoanableAmount(), result.getInterestRate().doubleValue() * 100)
                : "预审不通过：" + String.join("；", result.getReasons());
        return Result.success(msg, result);
    }

    @Operation(summary = "提交贷款申请", description = "自动发起多级审批流程")
    @PostMapping("/apply")
    public Result<LoanApplication> applyLoan(
            @Valid @RequestBody LoanApplyDTO dto) {
        LoanApplication application = loanService.applyLoan(dto);
        return Result.success("贷款申请提交成功，预审已通过，等待审批", application);
    }

    @Operation(summary = "查询贷款申请列表")
    @GetMapping("/list")
    public Result<Page<LoanApplication>> queryApplications(
            @Parameter(description = "职工ID") @RequestParam(required = false) Long employeeId,
            @Parameter(description = "单位ID") @RequestParam(required = false) Long companyId,
            @Parameter(description = "审批状态：0待审批 1审批中 2通过 3驳回")
                @RequestParam(required = false) Integer status,
            @Parameter(description = "贷款类型") @RequestParam(required = false) String loanType,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        Page<LoanApplication> page = loanService.queryApplications(
                employeeId, companyId, status, loanType, pageNum, pageSize);
        return Result.success(page);
    }

    @Operation(summary = "获取贷款申请详情", description = "包含预审报告全文")
    @GetMapping("/detail/{applicationId}")
    public Result<LoanApplication> getApplicationDetail(
            @Parameter(description = "申请ID") @PathVariable Long applicationId) {
        return Result.success(loanService.getApplicationDetail(applicationId));
    }

    @Operation(summary = "查询风控评分明细", description = "审批人员查看连续缴存、信用记录、负债情况、房屋估值各维度扣分明细，追溯额度和利率推算过程")
    @GetMapping("/risk-score/{applicationId}")
    public Result<List<LoanRiskScoreDetail>> getRiskScoreDetails(
            @Parameter(description = "贷款申请ID") @PathVariable Long applicationId) {
        return Result.success(loanService.getRiskScoreDetails(applicationId));
    }
}
