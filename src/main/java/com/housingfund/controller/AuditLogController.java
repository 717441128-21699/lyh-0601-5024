package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.entity.BusinessAuditLog;
import com.housingfund.service.BusinessAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "业务审计流水", description = "统一业务审计时间线查询，按业务单号追溯操作人、金额变化和触发消息")
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final BusinessAuditLogService auditLogService;

    @Operation(summary = "按业务单号查审计时间线", description = "传入业务单号，返回该单据所有操作的时间线（含操作人、前后金额、触发消息）")
    @GetMapping("/timeline/{businessNo}")
    public Result<List<BusinessAuditLog>> getTimelineByBusinessNo(
            @Parameter(description = "业务单号") @PathVariable String businessNo) {
        return Result.success(auditLogService.getTimelineByBusinessNo(businessNo));
    }

    @Operation(summary = "按业务类型和ID查审计时间线")
    @GetMapping("/timeline")
    public Result<List<BusinessAuditLog>> getTimelineByBusiness(
            @Parameter(description = "业务类型") @RequestParam String businessType,
            @Parameter(description = "业务ID") @RequestParam Long businessId) {
        return Result.success(auditLogService.getTimelineByBusiness(businessType, businessId));
    }

    @Operation(summary = "分页查询审计流水", description = "支持按业务单号、类型、操作人、动作、时间范围多维度筛选")
    @GetMapping("/list")
    public Result<Page<BusinessAuditLog>> queryAuditLogs(
            @Parameter(description = "业务单号") @RequestParam(required = false) String businessNo,
            @Parameter(description = "业务类型") @RequestParam(required = false) String businessType,
            @Parameter(description = "业务ID") @RequestParam(required = false) Long businessId,
            @Parameter(description = "操作人ID") @RequestParam(required = false) Long operatorId,
            @Parameter(description = "动作编码") @RequestParam(required = false) String actionCode,
            @Parameter(description = "开始时间") @RequestParam(required = false) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) LocalDateTime endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(auditLogService.queryAuditLogs(businessNo, businessType, businessId,
                operatorId, actionCode, startTime, endTime, pageNum, pageSize));
    }
}
