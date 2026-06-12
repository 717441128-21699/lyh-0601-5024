package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.entity.RiskAlertRuleConfig;
import com.housingfund.service.RiskAlertRuleConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "风险预警规则配置", description = "后台配置风险预警阈值、口径，支持按版本管理和灰度生效")
@RestController
@RequestMapping("/risk-alert/rule")
@RequiredArgsConstructor
public class RiskAlertRuleController {

    private final RiskAlertRuleConfigService ruleConfigService;

    @Operation(summary = "保存预警规则", description = "新增或修改预警规则，支持配置阈值、比较符、等级、生效时间、版本")
    @PostMapping("/save")
    public Result<RiskAlertRuleConfig> saveRule(@Valid @RequestBody RiskAlertRuleConfig config) {
        RiskAlertRuleConfig saved = ruleConfigService.saveRule(config);
        return Result.success("规则保存成功", saved);
    }

    @Operation(summary = "查询预警规则列表", description = "支持按预警类型、规则编码、状态筛选")
    @GetMapping("/list")
    public Result<Page<RiskAlertRuleConfig>> queryRules(
            @Parameter(description = "预警类型") @RequestParam(required = false) String alertType,
            @Parameter(description = "规则编码") @RequestParam(required = false) String ruleCode,
            @Parameter(description = "状态 1启用 0停用") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(ruleConfigService.queryRules(alertType, ruleCode, status, pageNum, pageSize));
    }

    @Operation(summary = "查询当前生效规则", description = "根据预警类型查询当前生效的规则（按时间窗口筛选）")
    @GetMapping("/effective")
    public Result<List<RiskAlertRuleConfig>> getEffectiveRules(
            @Parameter(description = "预警类型") @RequestParam String alertType) {
        return Result.success(ruleConfigService.getEffectiveRules(alertType));
    }

    @Operation(summary = "按规则编码和版本查询", description = "查询历史版本的规则，用于复盘旧预警事件命中的规则口径")
    @GetMapping("/version")
    public Result<RiskAlertRuleConfig> getRuleByCodeAndVersion(
            @Parameter(description = "规则编码") @RequestParam String ruleCode,
            @Parameter(description = "规则版本") @RequestParam String ruleVersion) {
        return Result.success(ruleConfigService.getRuleByCodeAndVersion(ruleCode, ruleVersion));
    }

    @Operation(summary = "查询当前生效阈值", description = "查询某个预警类型当前生效的阈值")
    @GetMapping("/threshold")
    public Result<java.math.BigDecimal> getEffectiveThreshold(
            @Parameter(description = "预警类型") @RequestParam String alertType) {
        return Result.success(ruleConfigService.getEffectiveThreshold(alertType));
    }

    @Operation(summary = "分页查询规则列表（按发布状态）", description = "支持按预警类型、发布状态筛选")
    @GetMapping("/list-by-status")
    public Result<Page<RiskAlertRuleConfig>> queryRulesByStatus(
            @Parameter(description = "预警类型") @RequestParam(required = false) String alertType,
            @Parameter(description = "发布状态: DRAFT/PENDING_RELEASE/PUBLISHED/REVOKED") @RequestParam(required = false) String publishStatus,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(ruleConfigService.queryRules(alertType, publishStatus, pageNum, pageSize));
    }

    @Operation(summary = "查询规则详情")
    @GetMapping("/detail/{id}")
    public Result<RiskAlertRuleConfig> getRuleDetail(@PathVariable Long id) {
        return Result.success(ruleConfigService.getRuleById(id));
    }

    @Operation(summary = "创建规则草稿", description = "新建规则，先进入草稿状态，可继续编辑调整阈值")
    @PostMapping("/draft")
    public Result<RiskAlertRuleConfig> createDraft(@RequestBody RiskAlertRuleConfig rule) {
        RiskAlertRuleConfig r = ruleConfigService.createDraft(rule);
        return Result.success("规则草稿已创建", r);
    }

    @Operation(summary = "提交待发布", description = "草稿确认无误后提交为待发布状态，等待发布审批")
    @PostMapping("/submit-release/{ruleId}")
    public Result<RiskAlertRuleConfig> submitForRelease(
            @PathVariable Long ruleId,
            @Parameter(description = "版本变更说明") @RequestParam(required = false) String changeLog,
            @Parameter(description = "提交人ID") @RequestParam(required = false) Long publisherId,
            @Parameter(description = "提交人姓名") @RequestParam(required = false) String publisherName) {
        RiskAlertRuleConfig r = ruleConfigService.submitForRelease(ruleId, changeLog, publisherId, publisherName);
        return Result.success("已提交待发布", r);
    }

    @Operation(summary = "发布规则", description = "待发布规则正式发布，同时停用同类型旧版本，新业务按新规则阈值生效")
    @PostMapping("/publish/{ruleId}")
    public Result<RiskAlertRuleConfig> publishRule(
            @PathVariable Long ruleId,
            @Parameter(description = "发布人ID") @RequestParam(required = false) Long publisherId,
            @Parameter(description = "发布人姓名") @RequestParam(required = false) String publisherName) {
        RiskAlertRuleConfig r = ruleConfigService.publishRule(ruleId, publisherId, publisherName);
        return Result.success("规则已发布，新业务将按此规则执行", r);
    }

    @Operation(summary = "停用规则", description = "停用已发布的规则")
    @PostMapping("/revoke/{ruleId}")
    public Result<RiskAlertRuleConfig> revokeRule(@PathVariable Long ruleId) {
        RiskAlertRuleConfig r = ruleConfigService.revokeRule(ruleId);
        return Result.success("规则已停用", r);
    }
}
