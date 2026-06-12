package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.RiskAlertHandleDTO;
import com.housingfund.entity.RiskAlertEvent;
import com.housingfund.service.RiskAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "风险预警处置", description = "预审低分/负债异常/逾期上升/提前还款异常预警事件管理，支持标记已处理、误报或转人工复核")
@RestController
@RequestMapping("/risk-alert")
@RequiredArgsConstructor
public class RiskAlertController {

    private final RiskAlertService riskAlertService;

    @Operation(summary = "分页查询预警事件", description = "支持按类型、状态、等级、机构、业务单号、时间范围筛选")
    @GetMapping("/list")
    public Result<Page<RiskAlertEvent>> queryAlerts(
            @Parameter(description = "预警类型") @RequestParam(required = false) String alertType,
            @Parameter(description = "预警状态") @RequestParam(required = false) String alertStatus,
            @Parameter(description = "预警等级") @RequestParam(required = false) String alertLevel,
            @Parameter(description = "分支机构ID") @RequestParam(required = false) Long branchId,
            @Parameter(description = "业务单号") @RequestParam(required = false) String businessNo,
            @Parameter(description = "开始时间") @RequestParam(required = false) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) LocalDateTime endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(riskAlertService.queryAlerts(alertType, alertStatus, alertLevel,
                branchId, businessNo, startTime, endTime, pageNum, pageSize));
    }

    @Operation(summary = "处理预警事件", description = "标记为已处理/误报/转人工复核")
    @PostMapping("/handle")
    public Result<RiskAlertEvent> handleAlert(@Valid @RequestBody RiskAlertHandleDTO dto) {
        RiskAlertEvent event = riskAlertService.handleAlert(dto);
        return Result.success("预警处理完成", event);
    }

    @Operation(summary = "按业务单号查预警", description = "查某笔业务关联的所有预警事件")
    @GetMapping("/by-business/{businessNo}")
    public Result<List<RiskAlertEvent>> getByBusinessNo(
            @Parameter(description = "业务单号") @PathVariable String businessNo) {
        return Result.success(riskAlertService.getAlertsByBusinessNo(businessNo));
    }

    @Operation(summary = "查待处理预警列表", description = "返回所有待处理预警事件")
    @GetMapping("/pending")
    public Result<List<RiskAlertEvent>> getPendingAlerts() {
        return Result.success(riskAlertService.getPendingAlerts());
    }
}
