package com.housingfund.controller;

import com.housingfund.common.Result;
import com.housingfund.dto.AuditReviewReportDTO;
import com.housingfund.service.AuditReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "审批复盘稽核报告", description = "按业务单号生成完整稽核报告（预审+审批+还款+消息+风险），支持下载留档")
@RestController
@RequestMapping("/audit-report")
@RequiredArgsConstructor
public class AuditReportController {

    private final AuditReportService auditReportService;

    @Operation(summary = "生成完整稽核报告", description = "按业务单号反查完整链路：预审结论、审批规则版本、各级意见、超时转交、还款计划、消息触达、风险预警")
    @GetMapping("/generate/{businessNo}")
    public Result<AuditReviewReportDTO> generateAuditReport(
            @Parameter(description = "业务单号") @PathVariable String businessNo) {
        return Result.success(auditReportService.generateAuditReport(businessNo));
    }

    @Operation(summary = "下载稽核报告Excel", description = "生成稽核报告并导出xlsx，供审计人员留档")
    @GetMapping("/download/{businessNo}")
    public ResponseEntity<byte[]> downloadAuditReport(
            @Parameter(description = "业务单号") @PathVariable String businessNo) {
        byte[] data = auditReportService.exportAuditReportExcel(businessNo);
        String fileName = "稽核报告_" + businessNo + ".xlsx";
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
}
