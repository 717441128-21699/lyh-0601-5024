package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.config.HousingFundConfig;
import com.housingfund.dto.ApprovalActionDTO;
import com.housingfund.entity.ApprovalRecord;
import com.housingfund.entity.ApprovalRuleConfig;
import com.housingfund.entity.ContributionDeclaration;
import com.housingfund.entity.LoanApplication;
import com.housingfund.entity.WithdrawalApplication;
import com.housingfund.enums.ApprovalStatusEnum;
import com.housingfund.enums.ApplicationTypeEnum;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.mapper.ApprovalRecordMapper;
import com.housingfund.mapper.ApprovalRuleConfigMapper;
import com.housingfund.mapper.ContributionDeclarationMapper;
import com.housingfund.mapper.LoanApplicationMapper;
import com.housingfund.mapper.WithdrawalApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final HousingFundConfig config;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalRuleConfigMapper approvalRuleConfigMapper;
    private final ContributionDeclarationMapper declarationMapper;
    private final WithdrawalApplicationMapper withdrawalMapper;
    private final LoanApplicationMapper loanMapper;
    private final NotificationService notificationService;
    private final ContributionService contributionService;
    private final WithdrawalService withdrawalService;
    private final LoanService loanService;
    private final BusinessAuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public void initApprovalProcess(Long businessId, String businessType, String businessNo,
                                    Long applicantId, String applicantName, Long branchId, BigDecimal amount) {
        List<ApprovalRuleConfig> rules = approvalRuleConfigMapper.findByBusinessType(businessType, LocalDateTime.now());

        if (rules != null && !rules.isEmpty()) {
            initRuleBasedApproval(businessId, businessType, businessNo, applicantId, applicantName, branchId, amount, rules);
        } else {
            initDefaultApproval(businessId, businessType, businessNo, applicantId, applicantName, branchId);
        }

        log.info("审批流程初始化完成: businessId={}, type={}, amount={}", businessId, businessType, amount);
    }

    private void initRuleBasedApproval(Long businessId, String businessType, String businessNo,
                                       Long applicantId, String applicantName, Long branchId,
                                       BigDecimal amount, List<ApprovalRuleConfig> rules) {
        List<ApprovalRuleConfig> levelConfigs = new ArrayList<>();
        for (ApprovalRuleConfig rule : rules) {
            levelConfigs.add(rule);
            if (Boolean.TRUE.equals(rule.getAutoEscalation())
                    && rule.getEscalationThreshold() != null
                    && amount != null && amount.compareTo(rule.getEscalationThreshold()) > 0) {
                levelConfigs.add(rule);
            }
        }

        int totalLevels = levelConfigs.size();
        int level = 1;
        for (ApprovalRuleConfig cfg : levelConfigs) {
            ApprovalRecord record = new ApprovalRecord();
            record.setApprovalNo("AR" + IdUtil.getSnowflakeNextIdStr());
            record.setBusinessId(businessId);
            record.setBusinessType(businessType);
            record.setBusinessNo(businessNo);
            record.setApplicantId(applicantId);
            record.setApplicantName(applicantName);
            record.setApprovalLevel(level);
            record.setTotalLevels(totalLevels);
            record.setApprovalAction(level == 1 ? 1 : 0);
            record.setApprovalResult(1);
            record.setSubmitTime(LocalDateTime.now());
            int timeoutHours = cfg.getTimeoutHours() != null ? cfg.getTimeoutHours() : config.getApproval().getTimeoutHours();
            record.setDeadlineTime(LocalDateTime.now().plusHours((long) timeoutHours * level));
            record.setTimeoutEscalated(false);
            record.setBranchId(branchId);
            record.setStatus(level == 1 ? 1 : 0);
            record.setApproverRoleId(cfg.getApproverRoleId());
            record.setApproverRoleName(cfg.getApproverRoleName());
            record.setRuleVersion(cfg.getRuleVersion());
            record.setRuleSnapshot(buildRuleSnapshot(cfg));
            approvalRecordMapper.insert(record);
            level++;
        }

        log.info("规则驱动审批初始化: businessId={}, type={}, totalLevels={}", businessId, businessType, totalLevels);
    }

    private void initDefaultApproval(Long businessId, String businessType, String businessNo,
                                     Long applicantId, String applicantName, Long branchId) {
        int totalLevels = config.getApproval().getLevels();
        int timeoutHours = config.getApproval().getTimeoutHours();

        for (int level = 1; level <= totalLevels; level++) {
            ApprovalRecord record = new ApprovalRecord();
            record.setApprovalNo("AR" + IdUtil.getSnowflakeNextIdStr());
            record.setBusinessId(businessId);
            record.setBusinessType(businessType);
            record.setBusinessNo(businessNo);
            record.setApplicantId(applicantId);
            record.setApplicantName(applicantName);
            record.setApprovalLevel(level);
            record.setTotalLevels(totalLevels);
            record.setApprovalAction(level == 1 ? 1 : 0);
            record.setApprovalResult(1);
            record.setSubmitTime(LocalDateTime.now());
            record.setDeadlineTime(LocalDateTime.now().plusHours((long) timeoutHours * level));
            record.setTimeoutEscalated(false);
            record.setBranchId(branchId);
            record.setStatus(level == 1 ? 1 : 0);
            approvalRecordMapper.insert(record);
        }

        log.info("默认审批初始化: businessId={}, type={}, levels={}", businessId, businessType, totalLevels);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecord processApproval(ApprovalActionDTO dto) {
        ApprovalRecord record = approvalRecordMapper.findCurrentApproval(
                dto.getBusinessId(), dto.getBusinessType(), dto.getApprovalLevel());
        if (record == null) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED, "当前审批节点不存在或已处理");
        }
        if (record.getApprovalResult() != null && record.getApprovalResult() != 1) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_PROCESSED, "该审批节点已处理完毕");
        }

        record.setApproverId(dto.getApproverId());
        record.setApproverName(dto.getApproverName());
        record.setApprovalResult(dto.getApprovalResult());
        record.setApprovalComment(dto.getApprovalComment());
        record.setApprovalTime(LocalDateTime.now());
        record.setStatus(2);
        approvalRecordMapper.updateById(record);

        auditLogService.log(record.getBusinessNo(), record.getBusinessType(), record.getBusinessId(),
                "APPROVAL_" + dto.getApprovalResult(),
                dto.getApprovalResult() == 2 ? "审批通过" : dto.getApprovalResult() == 3 ? "审批驳回" : "审批转办",
                dto.getApproverId(), dto.getApproverName(), record.getApproverRoleName(),
                null, null, null,
                String.format("第%d级审批(%d/%d)，意见：%s", dto.getApprovalLevel(), dto.getApprovalLevel(), record.getTotalLevels(),
                        dto.getApprovalComment() != null ? dto.getApprovalComment() : "无"),
                "YES", null, record.getBranchId(), null);

        if (dto.getApprovalResult() == 2) {
            processApprovalPassed(dto);
        } else if (dto.getApprovalResult() == 3) {
            processApprovalRejected(dto);
        } else {
            activateNextLevel(dto.getBusinessId(), dto.getBusinessType(), dto.getApprovalLevel() + 1);
        }

        log.info("审批处理完成: businessId={}, level={}, result={}",
                dto.getBusinessId(), dto.getApprovalLevel(), dto.getApprovalResult());
        return record;
    }

    private void activateNextLevel(Long businessId, String businessType, int nextLevel) {
        ApprovalRecord nextRecord = approvalRecordMapper.findCurrentApproval(businessId, businessType, nextLevel);
        if (nextRecord != null) {
            nextRecord.setApprovalAction(1);
            nextRecord.setStatus(1);
            nextRecord.setSubmitTime(LocalDateTime.now());
            nextRecord.setDeadlineTime(LocalDateTime.now().plusHours(config.getApproval().getTimeoutHours()));
            approvalRecordMapper.updateById(nextRecord);

            updateBusinessStatus(businessId, businessType, ApprovalStatusEnum.APPROVING.getCode(), nextLevel, null);
        }
    }

    private void processApprovalPassed(ApprovalActionDTO dto) {
        updateBusinessStatus(dto.getBusinessId(), dto.getBusinessType(),
                ApprovalStatusEnum.APPROVED.getCode(), dto.getApprovalLevel() + 1, null);

        switch (ApplicationTypeEnum.valueOf(dto.getBusinessType().toUpperCase())) {
            case CONTRIBUTION -> contributionService.processContributionApproved(dto.getBusinessId());
            case WITHDRAWAL -> {
                WithdrawalApplication wa = withdrawalMapper.selectById(dto.getBusinessId());
                if (wa != null) {
                    withdrawalService.processWithdrawalApproved(dto.getBusinessId(), wa.getApplicationAmount());
                }
            }
            case LOAN -> loanService.processLoanApproved(dto.getBusinessId());
        }
    }

    private void processApprovalRejected(ApprovalActionDTO dto) {
        updateBusinessStatus(dto.getBusinessId(), dto.getBusinessType(),
                ApprovalStatusEnum.REJECTED.getCode(), dto.getApprovalLevel(), dto.getApprovalComment());

        if (ApplicationTypeEnum.WITHDRAWAL.getCode().equals(dto.getBusinessType())) {
            withdrawalService.processWithdrawalRejected(dto.getBusinessId(), dto.getApprovalComment());
        }
    }

    private void updateBusinessStatus(Long businessId, String businessType,
                                      Integer status, Integer currentLevel, String rejectReason) {
        switch (businessType) {
            case "contribution" -> {
                ContributionDeclaration d = declarationMapper.selectById(businessId);
                if (d != null) {
                    d.setApprovalStatus(status);
                    d.setCurrentApprovalLevel(currentLevel);
                    if (status.equals(ApprovalStatusEnum.APPROVED.getCode())
                            || status.equals(ApprovalStatusEnum.REJECTED.getCode())) {
                        d.setApprovalTime(LocalDateTime.now());
                    }
                    if (rejectReason != null) d.setRejectReason(rejectReason);
                    declarationMapper.updateById(d);
                }
            }
            case "withdrawal" -> {
                WithdrawalApplication w = withdrawalMapper.selectById(businessId);
                if (w != null) {
                    w.setApprovalStatus(status);
                    w.setCurrentApprovalLevel(currentLevel);
                    if (status.equals(ApprovalStatusEnum.APPROVED.getCode())
                            || status.equals(ApprovalStatusEnum.REJECTED.getCode())) {
                        w.setApprovalTime(LocalDateTime.now());
                    }
                    if (rejectReason != null) w.setRejectReason(rejectReason);
                    withdrawalMapper.updateById(w);
                }
            }
            case "loan" -> {
                LoanApplication l = loanMapper.selectById(businessId);
                if (l != null) {
                    l.setApprovalStatus(status);
                    l.setCurrentApprovalLevel(currentLevel);
                    if (status.equals(ApprovalStatusEnum.APPROVED.getCode())
                            || status.equals(ApprovalStatusEnum.REJECTED.getCode())) {
                        l.setApprovalTime(LocalDateTime.now());
                    }
                    if (rejectReason != null) l.setRejectReason(rejectReason);
                    loanMapper.updateById(l);
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int processTimeoutEscalations() {
        List<ApprovalRecord> timeouts = approvalRecordMapper.findTimeoutApprovals(LocalDateTime.now());
        int count = 0;
        for (ApprovalRecord record : timeouts) {
            try {
                Long escalatedToId = findSupervisorApprover(record);
                approvalRecordMapper.markTimeoutEscalated(record.getId(), escalatedToId);
                activateNextLevel(record.getBusinessId(), record.getBusinessType(), record.getApprovalLevel() + 1);
                sendEscalationNotification(record);
                count++;
            } catch (Exception e) {
                log.error("超时转办处理失败: recordId={}, error={}", record.getId(), e.getMessage());
            }
        }
        if (count > 0) {
            log.info("审批超时转办处理完成，共{}条", count);
        }
        return count;
    }

    private Long findSupervisorApprover(ApprovalRecord record) {
        return 100L + record.getApprovalLevel();
    }

    private void sendEscalationNotification(ApprovalRecord record) {
        String title = "审批超时自动转办通知";
        String content = String.format("审批单【%s】已超过%d小时未处理，已自动转交上级审批。业务类型：%s",
                record.getBusinessNo(), config.getApproval().getTimeoutHours(), record.getBusinessType());
        notificationService.pushNotification(
                record.getApplicantId(), "EMPLOYEE", record.getApplicantName(), null,
                NotificationTypeEnum.SYSTEM_NOTICE, title, content,
                record.getBusinessId(), record.getBusinessType(), record.getBusinessNo(),
                null, record.getBranchId()
        );
    }

    public List<ApprovalRecord> getApprovalHistory(Long businessId, String businessType) {
        return approvalRecordMapper.findByBusiness(businessId, businessType);
    }

    private String buildRuleSnapshot(ApprovalRuleConfig cfg) {
        return String.format("层级%d|%s|%s|阈值%.2f|加签%s|加签阈值%.2f|超时%dh|版本%s",
                cfg.getApprovalLevel(),
                cfg.getLevelName() != null ? cfg.getLevelName() : "",
                cfg.getApproverRoleName() != null ? cfg.getApproverRoleName() : "",
                cfg.getAmountThreshold() != null ? cfg.getAmountThreshold() : BigDecimal.ZERO,
                Boolean.TRUE.equals(cfg.getAutoEscalation()) ? "是" : "否",
                cfg.getEscalationThreshold() != null ? cfg.getEscalationThreshold() : BigDecimal.ZERO,
                cfg.getTimeoutHours() != null ? cfg.getTimeoutHours() : 4,
                cfg.getRuleVersion() != null ? cfg.getRuleVersion() : "DEFAULT");
    }
}
