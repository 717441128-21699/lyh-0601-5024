package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.InspectionCaseDTO;
import com.housingfund.entity.InspectionCase;
import com.housingfund.entity.InspectionEvidence;
import com.housingfund.service.InspectionCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "稽核案件工作台", description = "跨业务异常自动归并，关联证据、处理进度和结论管理")
@RestController
@RequestMapping("/inspection")
@RequiredArgsConstructor
public class InspectionCaseController {

    private final InspectionCaseService inspectionCaseService;

    @Operation(summary = "自动扫描生成案件", description = "从风险预警、审计流水、资金差异中扫描异常，自动归并生成员待核查案件")
    @PostMapping("/auto-generate")
    public Result<Integer> autoGenerateCases() {
        List<InspectionCase> cases = inspectionCaseService.autoGenerateCases();
        return Result.success("自动生成稽核案件完成", cases.size());
    }

    @Operation(summary = "案件列表查询", description = "支持按状态、类型、等级、机构、职工、关键字筛选")
    @GetMapping("/list")
    public Result<Page<InspectionCase>> queryCases(
            @Parameter(description = "案件状态: PENDING待分配 PROCESSING处理中 CLOSED已结案") @RequestParam(required = false) String caseStatus,
            @Parameter(description = "案件类型: RISK_ALERT风险预警聚合 LARGE_AMOUNT大额操作") @RequestParam(required = false) String caseType,
            @Parameter(description = "案件等级: HIGH高 MEDIUM中 LOW低") @RequestParam(required = false) String caseLevel,
            @Parameter(description = "分支机构ID") @RequestParam(required = false) Long branchId,
            @Parameter(description = "关联职工ID") @RequestParam(required = false) Long employeeId,
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(inspectionCaseService.queryCases(caseStatus, caseType, caseLevel,
                branchId, employeeId, keyword, pageNum, pageSize));
    }

    @Operation(summary = "案件详情")
    @GetMapping("/detail/{caseId}")
    public Result<InspectionCase> getCaseDetail(@Parameter(description = "案件ID") @PathVariable Long caseId) {
        return Result.success(inspectionCaseService.getCaseDetail(caseId));
    }

    @Operation(summary = "案件关联证据列表", description = "案件下所有关联证据：风险预警、审计流水、对账差异等")
    @GetMapping("/evidence/{caseId}")
    public Result<List<InspectionEvidence>> getCaseEvidences(@Parameter(description = "案件ID") @PathVariable Long caseId) {
        return Result.success(inspectionCaseService.getCaseEvidences(caseId));
    }

    @Operation(summary = "处理案件", description = "分配核查人员、更新进度、填写最终结论结案")
    @PostMapping("/process")
    public Result<InspectionCase> processCase(@RequestBody InspectionCaseDTO dto) {
        InspectionCase ic = inspectionCaseService.processCase(dto);
        return Result.success("案件处理完成", ic);
    }
}
