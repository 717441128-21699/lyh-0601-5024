package com.housingfund.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housingfund.entity.AccountTransaction;
import com.housingfund.entity.FundAccount;
import com.housingfund.enums.NotificationTypeEnum;
import com.housingfund.mapper.AccountTransactionMapper;
import com.housingfund.mapper.EmployeeMapper;
import com.housingfund.mapper.FundAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundAccountService {

    private final FundAccountMapper fundAccountMapper;
    private final AccountTransactionMapper transactionMapper;
    private final EmployeeMapper employeeMapper;
    private final NotificationService notificationService;

    public FundAccount getEmployeeFundAccount(Long employeeId) {
        LambdaQueryWrapper<FundAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundAccount::getEmployeeId, employeeId)
                .eq(FundAccount::getAccountType, "PERSONAL");
        return fundAccountMapper.selectOne(wrapper);
    }

    public FundAccount getCompanyFundAccount(Long companyId) {
        LambdaQueryWrapper<FundAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundAccount::getCompanyId, companyId)
                .eq(FundAccount::getAccountType, "COMPANY");
        return fundAccountMapper.selectOne(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public FundAccount createEmployeeAccount(Long employeeId, Long companyId, Long branchId) {
        FundAccount account = new FundAccount();
        account.setAccountNo("FA" + IdUtil.getSnowflakeNextIdStr());
        account.setAccountType("PERSONAL");
        account.setEmployeeId(employeeId);
        account.setCompanyId(companyId);
        account.setBalance(BigDecimal.ZERO);
        account.setFrozenAmount(BigDecimal.ZERO);
        account.setTotalContribution(BigDecimal.ZERO);
        account.setTotalWithdrawal(BigDecimal.ZERO);
        account.setCompanyContribution(BigDecimal.ZERO);
        account.setPersonalContribution(BigDecimal.ZERO);
        account.setInterestIncome(BigDecimal.ZERO);
        account.setStatus(1);
        fundAccountMapper.insert(account);
        return account;
    }

    @Transactional(rollbackFor = Exception.class)
    public void increaseContribution(Long fundAccountId, BigDecimal totalAmount,
                                     BigDecimal companyAmount, BigDecimal personalAmount,
                                     Long businessId, String businessType, String businessNo,
                                     String operator) {
        FundAccount account = fundAccountMapper.selectById(fundAccountId);
        if (account == null) throw new IllegalArgumentException("公积金账户不存在: " + fundAccountId);

        BigDecimal balanceBefore = account.getBalance();

        int rows = fundAccountMapper.increaseContribution(fundAccountId, totalAmount, companyAmount, personalAmount);
        if (rows == 0) throw new IllegalStateException("账户缴存更新失败");

        FundAccount updated = fundAccountMapper.selectById(fundAccountId);
        createTransaction(account, "CONTRIBUTION", "缴存入账", totalAmount,
                balanceBefore, updated.getBalance(), "IN", businessId, businessType, businessNo, operator);

        log.info("缴存入账成功: accountId={}, amount={}", fundAccountId, totalAmount);
    }

    @Transactional(rollbackFor = Exception.class)
    public void freezeAmount(Long fundAccountId, BigDecimal amount,
                             Long businessId, String businessType, String businessNo,
                             String operator) {
        FundAccount account = fundAccountMapper.selectById(fundAccountId);
        if (account == null) throw new IllegalArgumentException("公积金账户不存在: " + fundAccountId);

        BigDecimal available = account.getBalance().subtract(account.getFrozenAmount());
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException("账户可用余额不足，可用: " + available + "，需冻结: " + amount);
        }

        BigDecimal balanceBefore = account.getBalance();
        int rows = fundAccountMapper.freezeAmount(fundAccountId, amount);
        if (rows == 0) throw new IllegalStateException("账户冻结失败");

        createTransaction(account, "FREEZE", "资金冻结", amount,
                balanceBefore, account.getBalance(), "FROZEN", businessId, businessType, businessNo, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deductFrozenAmount(Long fundAccountId, BigDecimal amount,
                                   Long businessId, String businessType, String businessNo,
                                   String operator) {
        FundAccount account = fundAccountMapper.selectById(fundAccountId);
        if (account == null) throw new IllegalArgumentException("公积金账户不存在: " + fundAccountId);

        BigDecimal balanceBefore = account.getBalance();
        int rows = fundAccountMapper.deductFrozenAmount(fundAccountId, amount);
        if (rows == 0) throw new IllegalStateException("账户扣减失败");

        FundAccount updated = fundAccountMapper.selectById(fundAccountId);
        createTransaction(account, "WITHDRAWAL", "提取出账", amount,
                balanceBefore, updated.getBalance(), "OUT", businessId, businessType, businessNo, operator);

        sendAccountChangeNotification(account, "提取出账", amount, updated.getBalance());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfreezeAmount(Long fundAccountId, BigDecimal amount,
                               Long businessId, String businessType, String businessNo,
                               String operator) {
        FundAccount account = fundAccountMapper.selectById(fundAccountId);
        if (account == null) throw new IllegalArgumentException("公积金账户不存在: " + fundAccountId);

        int rows = fundAccountMapper.unfreezeAmount(fundAccountId, amount);
        if (rows == 0) throw new IllegalStateException("账户解冻失败");

        createTransaction(account, "UNFREEZE", "资金解冻", amount,
                account.getBalance(), account.getBalance(), "UNFROZEN", businessId, businessType, businessNo, operator);
    }

    private void createTransaction(FundAccount account, String type, String desc, BigDecimal amount,
                                   BigDecimal balanceBefore, BigDecimal balanceAfter, String direction,
                                   Long businessId, String businessType, String businessNo, String operator) {
        AccountTransaction tx = new AccountTransaction();
        tx.setTransactionNo("TX" + IdUtil.getSnowflakeNextIdStr());
        tx.setFundAccountId(account.getId());
        tx.setAccountNo(account.getAccountNo());
        tx.setEmployeeId(account.getEmployeeId());
        tx.setCompanyId(account.getCompanyId());
        tx.setBranchId(account.getBranchId() != null ? account.getBranchId() : 1L);
        tx.setTransactionType(type);
        tx.setTransactionDesc(desc);
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setChangeDirection(direction);
        tx.setBusinessId(businessId);
        tx.setBusinessType(businessType);
        tx.setBusinessNo(businessNo);
        tx.setTransactionTime(LocalDateTime.now());
        tx.setOperator(operator);
        tx.setStatus(1);
        transactionMapper.insert(tx);
    }

    private void sendAccountChangeNotification(FundAccount account, String action, BigDecimal amount, BigDecimal newBalance) {
        if (account.getEmployeeId() != null) {
            var employee = employeeMapper.selectById(account.getEmployeeId());
            if (employee != null) {
                String title = "公积金账户变动通知";
                String content = String.format("您的公积金账户发生变动：%s，变动金额：%.2f元，当前余额：%.2f元",
                        action, amount, newBalance);
                notificationService.pushNotification(
                        account.getEmployeeId(), "EMPLOYEE", employee.getName(), employee.getPhone(),
                        NotificationTypeEnum.ACCOUNT_CHANGE, title, content,
                        account.getId(), "ACCOUNT", account.getAccountNo(),
                        account.getCompanyId(), account.getBranchId()
                );
            }
        }
    }
}
