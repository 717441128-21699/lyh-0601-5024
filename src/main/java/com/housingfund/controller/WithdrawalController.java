package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.WithdrawalApplyDTO;
import com.housingfund.dto.WithdrawalValidateResultDTO;
import com.housingfund.entity.WithdrawalApplication;
import com.housingfund.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "个人提取申请", description = "提取条件校验、额度计算、申请提交、状态查询")
@RestController
@RequestMapping("/withdrawal")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @Operation(summary = "提取条件校验", description = "根据提取类型、缴存时长和余额自动计算可提额度并校验条件")
    @PostMapping("/validate")
    public Result<WithdrawalValidateResultDTO> validateWithdrawal(
            @Valid @RequestBody WithdrawalApplyDTO dto) {
        WithdrawalValidateResultDTO result = withdrawalService.validateWithdrawal(dto);
        String msg = result.getValid()
                ? String.format("校验通过，可提额度%.2f元", result.getMaxWithdrawableAmount())
                : "校验不通过：" + String.join("；", result.getReasons());
        return Result.success(msg, result);
    }

    @Operation(summary = "提交提取申请", description = "不符合条件直接退回并说明原因")
    @PostMapping("/apply")
    public Result<WithdrawalApplication> applyWithdrawal(
            @Valid @RequestBody WithdrawalApplyDTO dto) {
        WithdrawalApplication application = withdrawalService.applyWithdrawal(dto);
        return Result.success("提取申请提交成功，等待审批", application);
    }

    @Operation(summary = "查询提取申请列表")
    @GetMapping("/list")
    public Result<Page<WithdrawalApplication>> queryApplications(
            @Parameter(description = "职工ID") @RequestParam(required = false) Long employeeId,
            @Parameter(description = "单位ID") @RequestParam(required = false) Long companyId,
            @Parameter(description = "审批状态：0待审批 1审批中 2通过 3驳回")
                @RequestParam(required = false) Integer status,
            @Parameter(description = "提取类型编码") @RequestParam(required = false) Integer type,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        Page<WithdrawalApplication> page = withdrawalService.queryApplications(
                employeeId, companyId, status, type, pageNum, pageSize);
        return Result.success(page);
    }

    @Operation(summary = "获取可提取类型列表")
    @GetMapping("/types")
    public Result<java.util.List<java.util.Map<String, Object>>> getWithdrawalTypes() {
        java.util.List<java.util.Map<String, Object>> types = new java.util.ArrayList<>();
        for (com.housingfund.enums.WithdrawalTypeEnum t : com.housingfund.enums.WithdrawalTypeEnum.values()) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("code", t.getCode());
            m.put("desc", t.getDesc());
            m.put("minMonths", t.getMinMonths());
            m.put("limit", t.getLimitValue());
            m.put("limitType", t.isRatioLimit() ? "账户余额比例" : "固定金额上限");
            types.add(m);
        }
        return Result.success(types);
    }
}
