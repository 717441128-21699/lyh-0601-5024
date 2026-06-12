package com.housingfund.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.housingfund.common.Result;
import com.housingfund.dto.NotificationQueryDTO;
import com.housingfund.entity.FundAccount;
import com.housingfund.entity.NotificationRecord;
import com.housingfund.service.FundAccountService;
import com.housingfund.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "账户查询与实时推送", description = "公积金账户查询、通知消息、实时推送状态查询")
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final FundAccountService fundAccountService;
    private final NotificationService notificationService;

    @Operation(summary = "查询职工公积金账户")
    @GetMapping("/employee/{employeeId}")
    public Result<FundAccount> getEmployeeAccount(
            @Parameter(description = "职工ID") @PathVariable Long employeeId) {
        FundAccount account = fundAccountService.getEmployeeFundAccount(employeeId);
        if (account == null) {
            return Result.error("职工公积金账户不存在");
        }
        return Result.success(account);
    }

    @Operation(summary = "查询单位公积金账户")
    @GetMapping("/company/{companyId}")
    public Result<FundAccount> getCompanyAccount(
            @Parameter(description = "单位ID") @PathVariable Long companyId) {
        FundAccount account = fundAccountService.getCompanyFundAccount(companyId);
        return Result.success(account);
    }

    @Operation(summary = "创建职工公积金账户")
    @PostMapping("/employee/create")
    public Result<FundAccount> createEmployeeAccount(
            @RequestBody Map<String, Long> params) {
        Long employeeId = params.get("employeeId");
        Long companyId = params.get("companyId");
        Long branchId = params.getOrDefault("branchId", 1L);
        FundAccount account = fundAccountService.createEmployeeAccount(employeeId, companyId, branchId);
        return Result.success("公积金账户创建成功，账号：" + account.getAccountNo(), account);
    }

    @Operation(summary = "查询消息通知列表", description = "账户变动、审批状态、还款提醒实时推送记录")
    @PostMapping("/notifications")
    public Result<Page<NotificationRecord>> queryNotifications(
            @RequestBody NotificationQueryDTO query) {
        return Result.success(notificationService.queryNotifications(query));
    }

    @Operation(summary = "标记消息已读")
    @PostMapping("/notification/read/{notificationId}")
    public Result<Boolean> markNotificationRead(
            @Parameter(description = "通知ID") @PathVariable Long notificationId) {
        boolean result = notificationService.markAsRead(notificationId);
        return Result.success(result ? "已标记为已读" : "标记失败", result);
    }

    @Operation(summary = "查询未读消息数")
    @GetMapping("/notification/unread-count")
    public Result<Long> getUnreadCount(
            @Parameter(description = "接收人ID") @RequestParam Long receiverId,
            @Parameter(description = "接收人类型：EMPLOYEE/COMPANY/STAFF")
                @RequestParam(defaultValue = "EMPLOYEE") String receiverType) {
        long count = notificationService.countUnread(receiverId, receiverType);
        return Result.success(count);
    }
}
