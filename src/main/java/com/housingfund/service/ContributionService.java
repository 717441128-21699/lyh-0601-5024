package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.BusinessException;
import com.housingfund.common.ErrorCode;
import com.housingfund.config.HousingFundConfig;
import com.housingfund.dto.ContributionDeclarationDTO;
import com.housingfund.dto.ContributionValidateResultDTO;
import com.housingfund.entity.*;
import com.housingfund.enums.ApprovalStatusEnum;
import com.housingfund.enums.ApplicationTypeEnum;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.mapper.CompanyMapper;
import com.housingfund.mapper.ContributionDeclarationMapper;
import com.housingfund.mapper.ContributionDetailMapper;
import com.housingfund.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionService {

    private final HousingFundConfig config;
    private final ContributionDeclarationMapper declarationMapper;
    private final ContributionDetailMapper detailMapper;
    private final CompanyMapper companyMapper;
    private final EmployeeMapper employeeMapper;
    private final FundAccountService fundAccountService;
    private final ApprovalService approvalService;
    private final NotificationService notificationService;
    private final BusinessAuditLogService auditLogService;

    public List<ContributionValidateResultDTO> validateContribution(ContributionDeclarationDTO dto) {
        List<ContributionValidateResultDTO> results = new ArrayList<>();
        HousingFundConfig.ContributionConfig cc = config.getContribution();

        for (ContributionDeclarationDTO.ContributionDetailItem item : dto.getEmployees()) {
            ContributionValidateResultDTO r = new ContributionValidateResultDTO();
            r.setValid(true);
            r.setEmployeeId(item.getEmployeeId());
            r.setContributionBase(item.getContributionBase());
            r.setCompanyRatio(item.getCompanyRatio());
            r.setPersonalRatio(item.getPersonalRatio());
            r.setCompanyAmount(item.getCompanyAmount());
            r.setPersonalAmount(item.getPersonalAmount());

            Employee emp = employeeMapper.selectById(item.getEmployeeId());
            if (emp != null) r.setEmployeeName(emp.getName());

            StringBuilder errors = new StringBuilder();

            if (item.getContributionBase().compareTo(cc.getMinBase()) < 0
                    || item.getContributionBase().compareTo(cc.getMaxBase()) > 0) {
                r.setValid(false);
                errors.append(String.format("缴存基数%.2f不在合规范围[%s,%s]内；",
                        item.getContributionBase(), cc.getMinBase(), cc.getMaxBase()));
            }

            if (item.getCompanyRatio().compareTo(cc.getMinRatio()) < 0
                    || item.getCompanyRatio().compareTo(cc.getMaxRatio()) > 0) {
                r.setValid(false);
                errors.append(String.format("单位比例%.4f不在合规范围[%s,%s]内；",
                        item.getCompanyRatio(), cc.getMinRatio(), cc.getMaxRatio()));
            }

            if (item.getPersonalRatio().compareTo(cc.getMinRatio()) < 0
                    || item.getPersonalRatio().compareTo(cc.getMaxRatio()) > 0) {
                r.setValid(false);
                errors.append(String.format("个人比例%.4f不在合规范围[%s,%s]内；",
                        item.getPersonalRatio(), cc.getMinRatio(), cc.getMaxRatio()));
            }

            BigDecimal expectedCompany = item.getContributionBase().multiply(item.getCompanyRatio())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal expectedPersonal = item.getContributionBase().multiply(item.getPersonalRatio())
                    .setScale(2, RoundingMode.HALF_UP);
            r.setExpectedCompanyAmount(expectedCompany);
            r.setExpectedPersonalAmount(expectedPersonal);

            if (item.getCompanyAmount().compareTo(expectedCompany) != 0) {
                r.setValid(false);
                errors.append(String.format("单位金额计算不符，应为%.2f，实际为%.2f；",
                        expectedCompany, item.getCompanyAmount()));
            }
            if (item.getPersonalAmount().compareTo(expectedPersonal) != 0) {
                r.setValid(false);
                errors.append(String.format("个人金额计算不符，应为%.2f，实际为%.2f；",
                        expectedPersonal, item.getPersonalAmount()));
            }

            if (!r.getValid()) {
                r.setErrorMessage(errors.toString());
            }
            results.add(r);
        }
        return results;
    }

    @Transactional(rollbackFor = Exception.class)
    public ContributionDeclaration submitDeclaration(ContributionDeclarationDTO dto) {
        List<ContributionValidateResultDTO> validations = validateContribution(dto);
        long invalidCount = validations.stream().filter(v -> !v.getValid()).count();
        if (invalidCount > 0) {
            StringBuilder msg = new StringBuilder("缴存数据校验失败：");
            validations.stream().filter(v -> !v.getValid())
                    .forEach(v -> msg.append(String.format("[%s]%s；", v.getEmployeeName(), v.getErrorMessage())));
            throw new BusinessException(ErrorCode.CONTRIBUTION_BASE_INVALID, msg.toString());
        }

        Company company = companyMapper.selectById(dto.getCompanyId());
        if (company == null) throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "单位信息不存在");

        String declarationNo = "CD" + IdUtil.getSnowflakeNextIdStr();
        BigDecimal totalCompany = BigDecimal.ZERO;
        BigDecimal totalPersonal = BigDecimal.ZERO;

        for (ContributionDeclarationDTO.ContributionDetailItem item : dto.getEmployees()) {
            BigDecimal total = item.getCompanyAmount().add(item.getPersonalAmount());
            totalCompany = totalCompany.add(item.getCompanyAmount());
            totalPersonal = totalPersonal.add(item.getPersonalAmount());

            ContributionDetail detail = new ContributionDetail();
            detail.setDeclarationId(null);
            detail.setDeclarationNo(declarationNo);
            detail.setEmployeeId(item.getEmployeeId());
            Employee emp = employeeMapper.selectById(item.getEmployeeId());
            if (emp != null) {
                detail.setEmployeeNo(emp.getEmployeeNo());
                detail.setEmployeeName(emp.getName());
                detail.setCompanyId(emp.getCompanyId());
                detail.setBranchId(emp.getBranchId());
                emp.setContributionBase(item.getContributionBase());
                emp.setCompanyRatio(item.getCompanyRatio());
                emp.setPersonalRatio(item.getPersonalRatio());
                emp.setLastContributionDate(dto.getContributionMonth());
                emp.setContributionMonths(emp.getContributionMonths() + 1);
                emp.setContinuousMonths(emp.getContinuousMonths() + 1);
                if (emp.getFirstContributionDate() == null) {
                    emp.setFirstContributionDate(dto.getContributionMonth());
                }
                employeeMapper.updateById(emp);
            }
            detail.setContributionMonth(dto.getContributionMonth());
            detail.setContributionBase(item.getContributionBase());
            detail.setCompanyRatio(item.getCompanyRatio());
            detail.setPersonalRatio(item.getPersonalRatio());
            detail.setCompanyAmount(item.getCompanyAmount());
            detail.setPersonalAmount(item.getPersonalAmount());
            detail.setTotalAmount(total);
            detail.setBusinessType("MONTHLY");
            detail.setStatus(0);
            detailMapper.insert(detail);
        }

        ContributionDeclaration declaration = new ContributionDeclaration();
        declaration.setDeclarationNo(declarationNo);
        declaration.setCompanyId(dto.getCompanyId());
        declaration.setCompanyCode(company.getCompanyCode());
        declaration.setCompanyName(company.getCompanyName());
        declaration.setBranchId(company.getBranchId());
        declaration.setContributionMonth(dto.getContributionMonth());
        declaration.setEmployeeCount(dto.getEmployees().size());
        declaration.setTotalCompanyAmount(totalCompany);
        declaration.setTotalPersonalAmount(totalPersonal);
        declaration.setTotalAmount(totalCompany.add(totalPersonal));
        declaration.setApprovalStatus(ApprovalStatusEnum.PENDING.getCode());
        declaration.setCurrentApprovalLevel(1);
        declaration.setSubmitTime(LocalDateTime.now());
        declaration.setStatus(1);
        declarationMapper.insert(declaration);

        Long declarationId = declaration.getId();
        LambdaQueryWrapper<ContributionDetail> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(ContributionDetail::getDeclarationNo, declarationNo);
        ContributionDetail updateDetail = new ContributionDetail();
        updateDetail.setDeclarationId(declarationId);
        detailMapper.update(updateDetail, updateWrapper);

        approvalService.initApprovalProcess(declarationId, ApplicationTypeEnum.CONTRIBUTION.getCode(),
                declarationNo, dto.getCompanyId(), company.getCompanyName(), company.getBranchId(),
                declaration.getTotalAmount());

        auditLogService.log(declarationNo, "contribution", declarationId,
                "SUBMIT", "提交缴存申报",
                dto.getCompanyId(), company.getCompanyName(), "单位经办人",
                BigDecimal.ZERO, declaration.getTotalAmount(), declaration.getTotalAmount(),
                String.format("单位申报总额%.2f元", declaration.getTotalAmount()),
                null, null, company.getBranchId(), null);

        sendDeclarationNotification(company, declaration, "缴存申报已提交");

        log.info("缴存申报提交成功: declarationNo={}, amount={}", declarationNo, declaration.getTotalAmount());
        return declaration;
    }

    @Transactional(rollbackFor = Exception.class)
    public void processContributionApproved(Long declarationId) {
        ContributionDeclaration declaration = declarationMapper.selectById(declarationId);
        if (declaration == null) return;

        LambdaQueryWrapper<ContributionDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContributionDetail::getDeclarationId, declarationId);
        List<ContributionDetail> details = detailMapper.selectList(wrapper);

        for (ContributionDetail detail : details) {
            FundAccount account = fundAccountService.getEmployeeFundAccount(detail.getEmployeeId());
            if (account == null) {
                Employee emp = employeeMapper.selectById(detail.getEmployeeId());
                account = fundAccountService.createEmployeeAccount(
                        detail.getEmployeeId(), emp.getCompanyId(), emp.getBranchId());
            }
            fundAccountService.increaseContribution(
                    account.getId(), detail.getTotalAmount(),
                    detail.getCompanyAmount(), detail.getPersonalAmount(),
                    declarationId, ApplicationTypeEnum.CONTRIBUTION.getCode(),
                    declaration.getDeclarationNo(), "system");

            detail.setStatus(1);
            detailMapper.updateById(detail);
        }

        auditLogService.log(declaration.getDeclarationNo(), "contribution", declarationId,
                "APPROVED", "缴存审批通过-资金入账",
                null, "SYSTEM", "系统",
                BigDecimal.ZERO, declaration.getTotalAmount(), declaration.getTotalAmount(),
                String.format("审批通过，%d名职工资金入账", details.size()),
                "YES", null, declaration.getBranchId(), null);

        Company company = companyMapper.selectById(declaration.getCompanyId());
        sendDeclarationNotification(company, declaration, "缴存申报已通过，资金已入账");
    }

    public Page<ContributionDeclaration> queryDeclarations(Long companyId, LocalDate startMonth,
                                                           LocalDate endMonth, Integer status,
                                                           int pageNum, int pageSize) {
        LambdaQueryWrapper<ContributionDeclaration> wrapper = new LambdaQueryWrapper<>();
        if (companyId != null) wrapper.eq(ContributionDeclaration::getCompanyId, companyId);
        if (startMonth != null) wrapper.ge(ContributionDeclaration::getContributionMonth, startMonth);
        if (endMonth != null) wrapper.le(ContributionDeclaration::getContributionMonth, endMonth);
        if (status != null) wrapper.eq(ContributionDeclaration::getApprovalStatus, status);
        wrapper.orderByDesc(ContributionDeclaration::getCreateTime);

        Page<ContributionDeclaration> page = new Page<>(pageNum, pageSize);
        return declarationMapper.selectPage(page, wrapper);
    }

    public List<ContributionDetail> getDeclarationDetails(Long declarationId) {
        LambdaQueryWrapper<ContributionDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContributionDetail::getDeclarationId, declarationId);
        return detailMapper.selectList(wrapper);
    }

    private void sendDeclarationNotification(Company company, ContributionDeclaration declaration, String action) {
        String title = "公积金缴存申报通知";
        String content = String.format("单位【%s】%s：缴存月份%s，人数%d人，总金额%.2f元",
                company.getCompanyName(), action,
                declaration.getContributionMonth(),
                declaration.getEmployeeCount(),
                declaration.getTotalAmount());

        notificationService.pushNotification(
                company.getId(), "COMPANY", company.getCompanyName(), company.getContactPhone(),
                NotificationTypeEnum.APPROVAL_STATUS, title, content,
                declaration.getId(), ApplicationTypeEnum.CONTRIBUTION.getCode(),
                declaration.getDeclarationNo(), company.getId(), company.getBranchId()
        );
    }
}
