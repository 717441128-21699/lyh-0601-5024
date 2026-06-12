package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.DashboardDTO;
import com.housingfund.dto.FundReportQueryDTO;
import com.housingfund.entity.FundReport;
import com.housingfund.service.FundReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "资金运行报表", description = "缴存/提取/贷款/逾期统计，按时间与分支机构导出")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class FundReportController {

    private final FundReportService fundReportService;

    @Operation(summary = "手动生成日报", description = "每日凌晨2点自动生成前一日报表")
    @PostMapping("/generate")
    public Result<Integer> generateDailyReport(
            @Parameter(description = "报表日期") @RequestParam(required = false)
                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate reportDate) {
        LocalDate date = reportDate != null ? reportDate : LocalDate.now().minusDays(1);
        int count = fundReportService.generateDailyReport(date).size();
        return Result.success(String.format("资金报表生成完成，共%d份", count), count);
    }

    @Operation(summary = "查询资金报表列表")
    @PostMapping("/list")
    public Result<Page<FundReport>> queryReports(
            @RequestBody FundReportQueryDTO query) {
        return Result.success(fundReportService.queryReports(query));
    }

    @Operation(summary = "导出资金报表Excel", description = "支持按时间范围与分支机构筛选导出")
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportReports(
            @RequestBody FundReportQueryDTO query) {
        byte[] data = fundReportService.exportReports(query);
        String fileName = "资金运行报表_" + LocalDate.now() + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", encoded);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(data.length)
                .body(data);
    }

    @Operation(summary = "获取最新数据概览", description = "获取最近生成的汇总数据")
    @GetMapping("/summary")
    public Result<FundReport> getLatestSummary(
            @Parameter(description = "分支机构ID（不传则全辖汇总）")
                @RequestParam(required = false) Long branchId) {
        FundReport report = fundReportService.getLatestSummary(branchId);
        if (report == null) {
            return Result.error("暂无报表数据，请先生成日报");
        }
        return Result.success(report);
    }

    @Operation(summary = "运营驾驶舱", description = "按分支机构对比+同比环比+趋势图数据")
    @GetMapping("/dashboard")
    public Result<DashboardDTO> getDashboard(
            @Parameter(description = "分支机构ID") @RequestParam(required = false) Long branchId,
            @Parameter(description = "查询月数") @RequestParam(required = false, defaultValue = "12") Integer months) {
        return Result.success(fundReportService.getDashboard(branchId, months));
    }
}
