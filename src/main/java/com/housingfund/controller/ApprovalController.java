package com.housingfund.controller;

import com.housingfund.common.Result;
import com.housingfund.dto.ApprovalActionDTO;
import com.housingfund.entity.ApprovalRecord;
import com.housingfund.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "多级审批流程", description = "审批处理、超时转办、催办、审批历史查询")
@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @Operation(summary = "处理审批", description = "审批通过/驳回/转办下一级，每级超时4小时自动转交上级")
    @PostMapping("/action")
    public Result<ApprovalRecord> processApproval(
            @RequestBody ApprovalActionDTO dto) {
        ApprovalRecord record = approvalService.processApproval(dto);
        String resultMsg = switch (dto.getApprovalResult()) {
            case 2 -> "审批通过";
            case 3 -> "审批驳回";
            default -> "已转办下一级审批";
        };
        return Result.success(resultMsg, record);
    }

    @Operation(summary = "查询审批历史", description = "按业务ID和类型查询全部审批记录")
    @GetMapping("/history")
    public Result<List<ApprovalRecord>> getApprovalHistory(
            @Parameter(description = "业务ID") @RequestParam Long businessId,
            @Parameter(description = "业务类型：contribution/withdrawal/loan")
                @RequestParam String businessType) {
        return Result.success(approvalService.getApprovalHistory(businessId, businessType));
    }

    @Operation(summary = "手动触发超时转办检查", description = "每30分钟系统自动执行，也可手动触发")
    @PostMapping("/check-timeout")
    public Result<Integer> checkTimeoutEscalations() {
        int count = approvalService.processTimeoutEscalations();
        return Result.success(String.format("超时转办检查完成，处理%d条", count), count);
    }
}
