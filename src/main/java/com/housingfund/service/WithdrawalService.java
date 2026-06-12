package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.dto.WithdrawalApplyDTO;
import com.housingfund.dto.WithdrawalValidateResultDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.ApprovalStatusEnum;
import com.housingfund.enums.ApplicationTypeEnum;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.enums.WithdrawalTypeEnum;
import com.housingfund.mapper.ContributionDetailMapper;
import com.housingfund.mapper.EmployeeMapper;
import com.housingfund.mapper.WithdrawalApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WithdrawalApplicationMapper applicationMapper;
    private final EmployeeMapper employeeMapper;
    private final ContributionDetailMapper contributionDetailMapper;
    private final FundAccountService fundAccountService;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;

    public WithdrawalValidateResultDTO validateWithdrawal(WithdrawalApplyDTO dto) {
        WithdrawalValidateResultDTO result = new WithdrawalValidateResultDTO();
        result.setValid(true);
        result.setWithdrawalType(dto.getWithdrawalType());
        result.setApplicationAmount(dto.getApplicationAmount());

        Employee employee = employeeMapper.selectById(dto.getEmployeeId());
        if (employee == null) {
            result.setValid(false);
            result.getReasons().add("员工信息不存在");
            return result;
        }

        WithdrawalTypeEnum typeEnum = WithdrawalTypeEnum.getByCode(dto.getWithdrawalType());
        if (typeEnum == null) {
            result.setValid(false);
            result.getReasons().add("无效的提取类型: " + dto.getWithdrawalType());
            return result;
        }
        result.setWithdrawalTypeDesc(typeEnum.getDesc());

        Integer months = contributionDetailMapper.countContributionMonths(dto.getEmployeeId());
        result.setContributionMonths(months != null ? months : 0);
        result.setRequiredMonths(typeEnum.getMinMonths());

        if (result.getContributionMonths() < typeEnum.getMinMonths()) {
            result.setValid(false);
            result.getReasons().add(String.format(
                    "缴存时长不足：当前已缴存%d个月，%s要求至少%d个月",
                    result.getContributionMonths(), typeEnum.getDesc(), typeEnum.getMinMonths()));
        }

        FundAccount account = fundAccountService.getEmployeeFundAccount(dto.getEmployeeId());
        BigDecimal balance = account != null ? account.getBalance() : BigDecimal.ZERO;
        result.setAccountBalance(balance);

        BigDecimal maxAmount;
        if (typeEnum.isRatioLimit()) {
            maxAmount = balance.multiply(typeEnum.getLimitValue()).setScale(2, RoundingMode.HALF_DOWN);
        } else {
            maxAmount = balance.min(typeEnum.getLimitValue());
        }
        result.setMaxWithdrawableAmount(maxAmount);

        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            result.setValid(false);
            result.getReasons().add("公积金账户余额为0，无法提取");
        }

        if (dto.getApplicationAmount().compareTo(maxAmount) > 0) {
            result.setValid(false);
            result.getReasons().add(String.format(
                    "提取金额超过可提额度：申请%.2f元，最高可提%.2f元",
                    dto.getApplicationAmount(), maxAmount));
        }

        if (dto.getApplicationAmount().compareTo(balance) > 0) {
            result.setValid(false);
            result.getReasons().add(String.format(
                    "提取金额超过账户余额：申请%.2f元，账户余额%.2f元",
                    dto.getApplicationAmount(), balance));
        }

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public WithdrawalApplication applyWithdrawal(WithdrawalApplyDTO dto) {
        WithdrawalValidateResultDTO validation = validateWithdrawal(dto);
        if (!validation.getValid()) {
            String reason = String.join("；", validation.getReasons());
            throw new BusinessException(ErrorCode.WITHDRAWAL_MIN_MONTHS_NOT_MET,
                    "提取申请校验失败：" + reason);
        }

        Employee employee = employeeMapper.selectById(dto.getEmployeeId());
        if (employee == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "员工信息不存在");

        FundAccount account = fundAccountService.getEmployeeFundAccount(dto.getEmployeeId());
        if (account == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "公积金账户不存在");

        String applicationNo = "WA" + IdUtil.getSnowflakeNextIdStr();

        fundAccountService.freezeAmount(account.getId(), dto.getApplicationAmount(),
                null, "PENDING", applicationNo, "system");

        WithdrawalApplication application = new WithdrawalApplication();
        application.setApplicationNo(applicationNo);
        application.setEmployeeId(dto.getEmployeeId());
        application.setEmployeeNo(employee.getEmployeeNo());
        application.setEmployeeName(employee.getName());
        application.setIdCard(employee.getIdCard());
        application.setPhone(employee.getPhone());
        application.setCompanyId(employee.getCompanyId());
        application.setBranchId(employee.getBranchId());
        application.setWithdrawalType(dto.getWithdrawalType());
        application.setWithdrawalTypeDesc(validation.getWithdrawalTypeDesc());
        application.setApplicationAmount(dto.getApplicationAmount());
        application.setApprovedAmount(BigDecimal.ZERO);
        application.setMaxWithdrawableAmount(validation.getMaxWithdrawableAmount());
        application.setContributionMonths(validation.getContributionMonths());
        application.setAccountBalance(validation.getAccountBalance());
        application.setApprovalStatus(ApprovalStatusEnum.PENDING.getCode());
        application.setCurrentApprovalLevel(1);
        application.setSubmitTime(LocalDateTime.now());
        application.setBankAccount(dto.getBankAccount());
        application.setBankName(dto.getBankName());
        application.setSupportMaterials(dto.getSupportMaterials());
        application.setRemark(dto.getRemark());
        application.setStatus(1);
        applicationMapper.insert(application);

        approvalService.initApprovalProcess(application.getId(), ApplicationTypeEnum.WITHDRAWAL.getCode(),
                applicationNo, employee.getId(), employee.getName(), employee.getBranchId(),
                dto.getApplicationAmount());

        sendApplicationNotification(employee, application, "提取申请已提交，等待审批");

        log.info("提取申请提交成功: applicationNo={}, amount={}", applicationNo, dto.getApplicationAmount());
        return application;
    }

    @Transactional(rollbackFor = Exception.class)
    public void processWithdrawalApproved(Long applicationId, BigDecimal approvedAmount) {
        WithdrawalApplication application = applicationMapper.selectById(applicationId);
        if (application == null) return;

        FundAccount account = fundAccountService.getEmployeeFundAccount(application.getEmployeeId());
        if (account == null) return;

        BigDecimal freezeDiff = application.getApplicationAmount().subtract(approvedAmount);
        if (freezeDiff.compareTo(BigDecimal.ZERO) > 0) {
            fundAccountService.unfreezeAmount(account.getId(), freezeDiff,
                    applicationId, ApplicationTypeEnum.WITHDRAWAL.getCode(),
                    application.getApplicationNo(), "system");
        }

        fundAccountService.deductFrozenAmount(account.getId(), approvedAmount,
                applicationId, ApplicationTypeEnum.WITHDRAWAL.getCode(),
                application.getApplicationNo(), "system");

        application.setApprovedAmount(approvedAmount);
        applicationMapper.updateById(application);

        Employee employee = employeeMapper.selectById(application.getEmployeeId());
        sendApplicationNotification(employee, application,
                String.format("提取申请已通过审批，审批金额%.2f元，预计3个工作日内到账", approvedAmount));
    }

    @Transactional(rollbackFor = Exception.class)
    public void processWithdrawalRejected(Long applicationId, String rejectReason) {
        WithdrawalApplication application = applicationMapper.selectById(applicationId);
        if (application == null) return;

        FundAccount account = fundAccountService.getEmployeeFundAccount(application.getEmployeeId());
        if (account != null) {
            fundAccountService.unfreezeAmount(account.getId(), application.getApplicationAmount(),
                    applicationId, ApplicationTypeEnum.WITHDRAWAL.getCode(),
                    application.getApplicationNo(), "system");
        }

        application.setRejectReason(rejectReason);
        applicationMapper.updateById(application);

        Employee employee = employeeMapper.selectById(application.getEmployeeId());
        sendApplicationNotification(employee, application,
                "提取申请被驳回，原因：" + rejectReason);
    }

    public Page<WithdrawalApplication> queryApplications(Long employeeId, Long companyId,
                                                         Integer status, Integer type,
                                                         int pageNum, int pageSize) {
        LambdaQueryWrapper<WithdrawalApplication> wrapper = new LambdaQueryWrapper<>();
        if (employeeId != null) wrapper.eq(WithdrawalApplication::getEmployeeId, employeeId);
        if (companyId != null) wrapper.eq(WithdrawalApplication::getCompanyId, companyId);
        if (status != null) wrapper.eq(WithdrawalApplication::getApprovalStatus, status);
        if (type != null) wrapper.eq(WithdrawalApplication::getWithdrawalType, type);
        wrapper.orderByDesc(WithdrawalApplication::getCreateTime);

        Page<WithdrawalApplication> page = new Page<>(pageNum, pageSize);
        return applicationMapper.selectPage(page, wrapper);
    }

    private void sendApplicationNotification(Employee employee, WithdrawalApplication application, String action) {
        String title = "公积金提取申请通知";
        String content = String.format("【%s】您好，您的%s。申请编号：%s，提取类型：%s，申请金额：%.2f元",
                employee.getName(), action,
                application.getApplicationNo(),
                application.getWithdrawalTypeDesc(),
                application.getApplicationAmount());

        notificationService.pushNotification(
                employee.getId(), "EMPLOYEE", employee.getName(), employee.getPhone(),
                NotificationTypeEnum.APPROVAL_STATUS, title, content,
                application.getId(), ApplicationTypeEnum.WITHDRAWAL.getCode(),
                application.getApplicationNo(), employee.getCompanyId(), employee.getBranchId()
        );
    }
}
