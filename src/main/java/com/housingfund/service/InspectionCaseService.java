package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.dto.InspectionCaseDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.RiskAlertStatusEnum;
import com.housingfund.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionCaseService {

    private final InspectionCaseMapper caseMapper;
    private final InspectionEvidenceMapper evidenceMapper;
    private final BusinessAuditLogMapper auditLogMapper;
    private final RiskAlertEventMapper alertEventMapper;
    private final EmployeeMapper employeeMapper;
    private final CompanyMapper companyMapper;
    private final BranchMapper branchMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<InspectionCase> autoGenerateCases() {
        List<InspectionCase> generatedCases = new ArrayList<>();
        generatedCases.addAll(generateFromRiskAlerts());
        generatedCases.addAll(generateFromAuditLogs());
        log.info("自动生成稽核案件完成，共{}条", generatedCases.size());
        return generatedCases;
    }

    private List<InspectionCase> generateFromRiskAlerts() {
        List<InspectionCase> cases = new ArrayList<>();
        LambdaQueryWrapper<RiskAlertEvent> alertWrapper = new LambdaQueryWrapper<>();
        alertWrapper.eq(RiskAlertEvent::getAlertStatus, RiskAlertStatusEnum.PENDING.getCode())
                .orderByDesc(RiskAlertEvent::getCreateTime);
        List<RiskAlertEvent> pendingAlerts = alertEventMapper.selectList(alertWrapper);

        Map<String, List<RiskAlertEvent>> groupedByEmployee = new LinkedHashMap<>();
        for (RiskAlertEvent alert : pendingAlerts) {
            if (alert.getEmployeeId() != null) {
                String key = "EMP_" + alert.getEmployeeId();
                groupedByEmployee.computeIfAbsent(key, k -> new ArrayList<>()).add(alert);
            } else if (alert.getBranchId() != null) {
                String key = "BR_" + alert.getBranchId();
                groupedByEmployee.computeIfAbsent(key, k -> new ArrayList<>()).add(alert);
            }
        }

        for (Map.Entry<String, List<RiskAlertEvent>> entry : groupedByEmployee.entrySet()) {
            List<RiskAlertEvent> alerts = entry.getValue();
            if (alerts.isEmpty()) continue;

            RiskAlertEvent first = alerts.get(0);
            Employee emp = first.getEmployeeId() != null ? employeeMapper.selectById(first.getEmployeeId()) : null;
            Branch br = first.getBranchId() != null ? branchMapper.selectById(first.getBranchId()) : null;

            String caseType = "RISK_ALERT";
            String caseLevel = alerts.size() >= 3 ? "HIGH" : alerts.size() >= 2 ? "MEDIUM" : "LOW";

            InspectionCase ic = createCase(
                    "多风险预警聚合：" + first.getAlertTypeName() + "等" + alerts.size() + "项",
                    caseType, caseLevel,
                    first.getBranchId(), br != null ? br.getBranchName() : "",
                    first.getEmployeeId(), emp != null ? emp.getName() : "",
                    null, null,
                    buildAlertSummary(alerts),
                    alerts.stream().map(RiskAlertEvent::getAlertNo).toList(),
                    null
            );

            for (int i = 0; i < alerts.size(); i++) {
                RiskAlertEvent alert = alerts.get(i);
                addEvidence(ic.getId(), ic.getCaseNo(),
                        "RISK_ALERT", "风险预警", "RISK_ALERT_MODULE",
                        alert.getBusinessNo(), alert.getBusinessType(), alert.getBusinessId(),
                        alert.getAlertNo(), null,
                        alert.getAlertTitle(), alert.getAlertContent(),
                        alert.getAlertValue(), alert.getCreateTime(),
                        null, i + 1);
            }
            cases.add(ic);
        }
        return cases;
    }

    private List<InspectionCase> generateFromAuditLogs() {
        List<InspectionCase> cases = new ArrayList<>();
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<BusinessAuditLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.ge(BusinessAuditLog::getActionTime, start);
        logWrapper.and(w -> w.ge(BusinessAuditLog::getAmountChanged, new BigDecimal("500000"))
                .or().le(BusinessAuditLog::getAmountChanged, new BigDecimal("-500000")));
        logWrapper.orderByDesc(BusinessAuditLog::getActionTime);
        logWrapper.last("LIMIT 100");
        List<BusinessAuditLog> largeLogs = auditLogMapper.selectList(logWrapper);

        Map<Long, List<BusinessAuditLog>> byEmployee = new LinkedHashMap<>();
        for (BusinessAuditLog l : largeLogs) {
            if (l.getOperatorId() != null) {
                byEmployee.computeIfAbsent(l.getOperatorId(), k -> new ArrayList<>()).add(l);
            }
        }

        for (Map.Entry<Long, List<BusinessAuditLog>> entry : byEmployee.entrySet()) {
            List<BusinessAuditLog> logs = entry.getValue();
            if (logs.size() < 2) continue;
            BusinessAuditLog first = logs.get(0);
            Employee emp = employeeMapper.selectById(first.getOperatorId());
            Branch br = first.getBranchId() != null ? branchMapper.selectById(first.getBranchId()) : null;

            BigDecimal maxAmt = BigDecimal.ZERO;
            for (BusinessAuditLog l : logs) {
                if (l.getAmountChanged() != null) {
                    maxAmt = maxAmt.max(l.getAmountChanged().abs());
                }
            }

            InspectionCase ic = createCase(
                    "大额操作聚合：" + logs.size() + "笔超50万操作待核查",
                    "LARGE_AMOUNT", logs.size() >= 5 ? "HIGH" : "MEDIUM",
                    first.getBranchId(), br != null ? br.getBranchName() : "",
                    first.getOperatorId(), emp != null ? emp.getName() : first.getOperatorName(),
                    null, null,
                    buildAuditSummary(logs, maxAmt),
                    null,
                    logs.stream().map(BusinessAuditLog::getTraceNo).toList()
            );
            ic.setMaxAmountInvolved(maxAmt);
            caseMapper.updateById(ic);

            for (int i = 0; i < logs.size(); i++) {
                BusinessAuditLog l = logs.get(i);
                addEvidence(ic.getId(), ic.getCaseNo(),
                        "AUDIT_LOG", "审计流水", "AUDIT_MODULE",
                        l.getBusinessNo(), l.getBusinessType(), l.getBusinessId(),
                        null, l.getTraceNo(),
                        l.getActionName() + "-" + l.getChangeDescription(),
                        l.getChangeDescription() != null ? l.getChangeDescription() : "",
                        l.getAmountChanged(), l.getActionTime(),
                        l.getOperatorName(), i + 1);
            }
            cases.add(ic);
        }
        return cases;
    }

    private InspectionCase createCase(String title, String type, String level,
                                      Long branchId, String branchName,
                                      Long employeeId, String employeeName,
                                      Long companyId, String companyName,
                                      String summary,
                                      List<String> alertNos, List<String> diffIds) {
        InspectionCase ic = new InspectionCase();
        ic.setCaseNo("IC" + IdUtil.getSnowflakeNextIdStr());
        ic.setCaseTitle(title);
        ic.setCaseType(type);
        ic.setCaseLevel(level);
        ic.setCaseStatus("PENDING");
        ic.setBranchId(branchId);
        ic.setBranchName(branchName);
        ic.setEmployeeId(employeeId);
        ic.setEmployeeName(employeeName);
        ic.setCompanyId(companyId);
        ic.setCompanyName(companyName);
        if (alertNos != null && !alertNos.isEmpty()) ic.setRelatedAlertNos(String.join(",", alertNos));
        if (diffIds != null && !diffIds.isEmpty()) ic.setRelatedDiffIds(String.join(",", diffIds));
        ic.setCaseSummary(summary);
        ic.setProcessingProgress("待分配核查人员");
        ic.setResolveDeadline(LocalDateTime.now().plusDays(7));
        ic.setEvidenceCount(0);
        ic.setStatus(1);
        caseMapper.insert(ic);
        return ic;
    }

    private void addEvidence(Long caseId, String caseNo,
                              String type, String typeName, String source,
                              String businessNo, String businessType, Long businessId,
                              String alertNo, String diffId,
                              String title, String content,
                              BigDecimal amount, LocalDateTime eventTime,
                              String operator, int sort) {
        InspectionEvidence ev = new InspectionEvidence();
        ev.setCaseId(caseId);
        ev.setCaseNo(caseNo);
        ev.setEvidenceType(type);
        ev.setEvidenceTypeName(typeName);
        ev.setEvidenceSource(source);
        ev.setBusinessNo(businessNo);
        ev.setBusinessType(businessType);
        ev.setBusinessId(businessId);
        ev.setAlertNo(alertNo);
        ev.setDiffId(diffId);
        ev.setEvidenceTitle(title);
        ev.setEvidenceContent(content);
        ev.setAmountInvolved(amount);
        ev.setEventTime(eventTime);
        ev.setOperatorName(operator);
        ev.setSortOrder(sort);
        ev.setStatus(1);
        evidenceMapper.insert(ev);
        updateEvidenceCount(caseId);
    }

    private void updateEvidenceCount(Long caseId) {
        LambdaQueryWrapper<InspectionEvidence> w = new LambdaQueryWrapper<>();
        w.eq(InspectionEvidence::getCaseId, caseId);
        Long c = evidenceMapper.selectCount(w);
        if (c != null) {
            InspectionCase ic = new InspectionCase();
            ic.setId(caseId);
            ic.setEvidenceCount(c.intValue());
            caseMapper.updateById(ic);
        }
    }

    private String buildAlertSummary(List<RiskAlertEvent> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("共").append(alerts.size()).append("条预警。");
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (RiskAlertEvent a : alerts) {
            byType.merge(a.getAlertTypeName(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : byType.entrySet()) {
            sb.append(e.getKey()).append(":").append(e.getValue()).append("条; ");
        }
        return sb.toString();
    }

    private String buildAuditSummary(List<BusinessAuditLog> logs, BigDecimal max) {
        return "共" + logs.size() + "笔大额操作，单笔最大变化" + max.setScale(2) + "元";
    }

    public Page<InspectionCase> queryCases(String caseStatus, String caseType, String caseLevel,
                                            Long branchId, Long employeeId, String keyword,
                                            int pageNum, int pageSize) {
        LambdaQueryWrapper<InspectionCase> w = new LambdaQueryWrapper<>();
        if (caseStatus != null) w.eq(InspectionCase::getCaseStatus, caseStatus);
        if (caseType != null) w.eq(InspectionCase::getCaseType, caseType);
        if (caseLevel != null) w.eq(InspectionCase::getCaseLevel, caseLevel);
        if (branchId != null) w.eq(InspectionCase::getBranchId, branchId);
        if (employeeId != null) w.eq(InspectionCase::getEmployeeId, employeeId);
        if (keyword != null && !keyword.isEmpty()) {
            w.and(ww -> ww.like(InspectionCase::getCaseNo, keyword)
                    .or().like(InspectionCase::getCaseTitle, keyword)
                    .or().like(InspectionCase::getEmployeeName, keyword));
        }
        w.orderByDesc(InspectionCase::getCreateTime);
        return caseMapper.selectPage(new Page<>(pageNum, pageSize), w);
    }

    public InspectionCase getCaseDetail(Long caseId) {
        InspectionCase ic = caseMapper.selectById(caseId);
        if (ic == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "案件不存在");
        return ic;
    }

    public List<InspectionEvidence> getCaseEvidences(Long caseId) {
        return evidenceMapper.findByCaseId(caseId);
    }

    @Transactional(rollbackFor = Exception.class)
    public InspectionCase processCase(InspectionCaseDTO dto) {
        InspectionCase ic = caseMapper.selectById(dto.getCaseId());
        if (ic == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "案件不存在");

        if (dto.getAssigneeId() != null && !dto.getAssigneeId().equals(ic.getAssigneeId())) {
            ic.setAssigneeId(dto.getAssigneeId());
            ic.setAssigneeName(dto.getAssigneeName());
            ic.setAssignTime(LocalDateTime.now());
            if ("PENDING".equals(ic.getCaseStatus())) ic.setCaseStatus("PROCESSING");
        }
        if (dto.getProcessingProgress() != null) ic.setProcessingProgress(dto.getProcessingProgress());
        if (dto.getFinalConclusion() != null) ic.setFinalConclusion(dto.getFinalConclusion());
        if (dto.getFinalResult() != null) {
            ic.setFinalResult(dto.getFinalResult());
            if ("CONFIRMED".equals(dto.getFinalResult()) || "FALSE_POSITIVE".equals(dto.getFinalResult())
                    || "ESCALATED".equals(dto.getFinalResult())) {
                ic.setCaseStatus("CLOSED");
                ic.setCloseTime(LocalDateTime.now());
            }
        }
        if (dto.getRemark() != null) ic.setRemark(dto.getRemark());
        caseMapper.updateById(ic);
        return ic;
    }
}
