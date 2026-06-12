package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.ContributionDeclarationDTO;
import com.housingfund.dto.ContributionValidateResultDTO;
import com.housingfund.entity.ContributionDeclaration;
import com.housingfund.entity.ContributionDetail;
import com.housingfund.service.ContributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "单位缴存申报", description = "单位缴存基数比例校验、申报提交、明细查询")
@RestController
@RequestMapping("/contribution")
@RequiredArgsConstructor
public class ContributionController {

    private final ContributionService contributionService;

    @Operation(summary = "校验缴存数据", description = "根据缴存基数与比例自动校验金额合规性")
    @PostMapping("/validate")
    public Result<List<ContributionValidateResultDTO>> validateContribution(
            @RequestBody ContributionDeclarationDTO dto) {
        List<ContributionValidateResultDTO> results = contributionService.validateContribution(dto);
        long validCount = results.stream().filter(ContributionValidateResultDTO::getValid).count();
        return Result.success(String.format("校验完成：%d条通过，%d条不通过",
                validCount, results.size() - validCount), results);
    }

    @Operation(summary = "提交缴存申报", description = "提交单位月度缴存申报，自动校验并发起审批流程")
    @PostMapping("/submit")
    public Result<ContributionDeclaration> submitDeclaration(
            @RequestBody ContributionDeclarationDTO dto) {
        ContributionDeclaration declaration = contributionService.submitDeclaration(dto);
        return Result.success("缴存申报提交成功", declaration);
    }

    @Operation(summary = "查询缴存申报列表")
    @GetMapping("/list")
    public Result<Page<ContributionDeclaration>> queryDeclarations(
            @Parameter(description = "单位ID") @RequestParam(required = false) Long companyId,
            @Parameter(description = "开始月份") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startMonth,
            @Parameter(description = "结束月份") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endMonth,
            @Parameter(description = "审批状态：0待审批 1审批中 2通过 3驳回")
                @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        Page<ContributionDeclaration> page = contributionService.queryDeclarations(
                companyId, startMonth, endMonth, status, pageNum, pageSize);
        return Result.success(page);
    }

    @Operation(summary = "查询申报明细", description = "获取申报单下所有职工缴存明细")
    @GetMapping("/detail/{declarationId}")
    public Result<List<ContributionDetail>> getDeclarationDetails(
            @Parameter(description = "申报单ID") @PathVariable Long declarationId) {
        return Result.success(contributionService.getDeclarationDetails(declarationId));
    }
}
