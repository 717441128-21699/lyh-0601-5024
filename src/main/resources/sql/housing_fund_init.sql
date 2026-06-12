-- ============================================================
-- 智慧公积金综合管理系统 数据库初始化脚本
-- 数据库版本: MySQL 8.0+
-- 创建日期: 2026-06-13
-- ============================================================

CREATE DATABASE IF NOT EXISTS housing_fund
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE housing_fund;

-- ============================================================
-- 一、基础数据：分支机构、单位、职工
-- ============================================================

DROP TABLE IF EXISTS sys_branch;
CREATE TABLE sys_branch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    branch_code VARCHAR(32) NOT NULL UNIQUE COMMENT '机构编码',
    branch_name VARCHAR(100) NOT NULL COMMENT '机构名称',
    branch_type VARCHAR(20) COMMENT '机构类型',
    leader VARCHAR(50) COMMENT '负责人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(255) COMMENT '地址',
    parent_id BIGINT DEFAULT 0 COMMENT '上级机构ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0停用',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) ENGINE=InnoDB COMMENT='分支机构表';

DROP TABLE IF EXISTS sys_company;
CREATE TABLE sys_company (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    company_code VARCHAR(32) NOT NULL UNIQUE COMMENT '单位公积金账号',
    company_name VARCHAR(200) NOT NULL COMMENT '单位名称',
    unified_social_credit_code VARCHAR(32) COMMENT '统一社会信用代码',
    legal_person VARCHAR(50) COMMENT '法人姓名',
    legal_person_id_card VARCHAR(20) COMMENT '法人身份证',
    contact_person VARCHAR(50) COMMENT '经办人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(255) COMMENT '单位地址',
    branch_id BIGINT COMMENT '所属分支机构',
    employee_count INT DEFAULT 0 COMMENT '职工人数',
    contribution_ratio DECIMAL(6,4) COMMENT '缴存比例',
    status TINYINT DEFAULT 1 COMMENT '状态 1正常 0停用',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_company_code (company_code),
    INDEX idx_branch_id (branch_id)
) ENGINE=InnoDB COMMENT='缴存单位表';

DROP TABLE IF EXISTS sys_employee;
CREATE TABLE sys_employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    employee_no VARCHAR(32) NOT NULL UNIQUE COMMENT '职工公积金账号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    id_card VARCHAR(20) NOT NULL COMMENT '身份证号',
    phone VARCHAR(20) COMMENT '手机号',
    gender VARCHAR(10) COMMENT '性别',
    birth_date DATE COMMENT '出生日期',
    company_id BIGINT NOT NULL COMMENT '所属单位ID',
    company_code VARCHAR(32) COMMENT '单位公积金账号',
    branch_id BIGINT COMMENT '所属分支机构',
    contribution_base DECIMAL(12,2) COMMENT '缴存基数',
    company_ratio DECIMAL(6,4) COMMENT '单位缴存比例',
    personal_ratio DECIMAL(6,4) COMMENT '个人缴存比例',
    contribution_months INT DEFAULT 0 COMMENT '累计缴存月数',
    continuous_months INT DEFAULT 0 COMMENT '连续缴存月数',
    first_contribution_date DATE COMMENT '首次缴存日期',
    last_contribution_date DATE COMMENT '最近缴存日期',
    credit_score INT DEFAULT 650 COMMENT '信用评分(300-850)',
    status TINYINT DEFAULT 1 COMMENT '状态 1正常 0封存',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_employee_no (employee_no),
    INDEX idx_id_card (id_card),
    INDEX idx_company_id (company_id),
    INDEX idx_branch_id (branch_id)
) ENGINE=InnoDB COMMENT='缴存职工表';

-- ============================================================
-- 二、公积金账户与交易
-- ============================================================

DROP TABLE IF EXISTS fund_account;
CREATE TABLE fund_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    account_no VARCHAR(32) NOT NULL UNIQUE COMMENT '账户编号',
    account_type VARCHAR(20) NOT NULL COMMENT '账户类型: PERSONAL个人 COMPANY单位',
    employee_id BIGINT COMMENT '职工ID(个人账户)',
    company_id BIGINT COMMENT '单位ID',
    branch_id BIGINT COMMENT '所属分支机构',
    balance DECIMAL(16,2) DEFAULT 0 COMMENT '账户余额',
    frozen_amount DECIMAL(16,2) DEFAULT 0 COMMENT '冻结金额',
    total_contribution DECIMAL(16,2) DEFAULT 0 COMMENT '累计缴存',
    total_withdrawal DECIMAL(16,2) DEFAULT 0 COMMENT '累计提取',
    company_contribution DECIMAL(16,2) DEFAULT 0 COMMENT '单位缴存累计',
    personal_contribution DECIMAL(16,2) DEFAULT 0 COMMENT '个人缴存累计',
    interest_income DECIMAL(16,2) DEFAULT 0 COMMENT '利息收入',
    status TINYINT DEFAULT 1 COMMENT '状态 1正常 0冻结',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_account_no (account_no),
    INDEX idx_employee_id (employee_id),
    INDEX idx_company_id (company_id)
) ENGINE=InnoDB COMMENT='公积金账户表';

DROP TABLE IF EXISTS account_transaction;
CREATE TABLE account_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    transaction_no VARCHAR(32) NOT NULL UNIQUE COMMENT '交易流水号',
    fund_account_id BIGINT NOT NULL COMMENT '账户ID',
    account_no VARCHAR(32) COMMENT '账户编号',
    employee_id BIGINT COMMENT '职工ID',
    company_id BIGINT COMMENT '单位ID',
    branch_id BIGINT COMMENT '所属分支机构',
    transaction_type VARCHAR(20) NOT NULL COMMENT '交易类型: CONTRIBUTION缴存 WITHDRAWAL提取 FREEZE冻结 UNFREEZE解冻',
    transaction_desc VARCHAR(100) COMMENT '交易描述',
    amount DECIMAL(16,2) NOT NULL COMMENT '交易金额',
    balance_before DECIMAL(16,2) COMMENT '变动前余额',
    balance_after DECIMAL(16,2) COMMENT '变动后余额',
    change_direction VARCHAR(10) COMMENT '方向: IN收入 OUT支出 FROZEN冻结 UNFROZEN解冻',
    business_id BIGINT COMMENT '关联业务ID',
    business_type VARCHAR(30) COMMENT '关联业务类型',
    business_no VARCHAR(32) COMMENT '关联业务编号',
    transaction_time DATETIME COMMENT '交易时间',
    operator VARCHAR(50) COMMENT '操作人',
    status TINYINT DEFAULT 1 COMMENT '状态 1成功 0失败',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_transaction_no (transaction_no),
    INDEX idx_fund_account_id (fund_account_id),
    INDEX idx_transaction_time (transaction_time)
) ENGINE=InnoDB COMMENT='账户交易流水表';

-- ============================================================
-- 三、缴存申报与明细
-- ============================================================

DROP TABLE IF EXISTS contribution_declaration;
CREATE TABLE contribution_declaration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    declaration_no VARCHAR(32) NOT NULL UNIQUE COMMENT '申报单编号',
    company_id BIGINT NOT NULL COMMENT '单位ID',
    company_code VARCHAR(32) COMMENT '单位公积金账号',
    company_name VARCHAR(200) COMMENT '单位名称',
    branch_id BIGINT COMMENT '所属分支机构',
    contribution_month DATE NOT NULL COMMENT '缴存月份',
    employee_count INT DEFAULT 0 COMMENT '职工人数',
    total_company_amount DECIMAL(16,2) DEFAULT 0 COMMENT '单位缴存总额',
    total_personal_amount DECIMAL(16,2) DEFAULT 0 COMMENT '个人缴存总额',
    total_amount DECIMAL(16,2) DEFAULT 0 COMMENT '缴存合计总额',
    approval_status TINYINT DEFAULT 0 COMMENT '审批状态 0待审批 1审批中 2通过 3驳回 4超时转办',
    current_approval_level INT DEFAULT 1 COMMENT '当前审批层级',
    submit_time DATETIME COMMENT '提交时间',
    approval_time DATETIME COMMENT '审批完成时间',
    reject_reason VARCHAR(500) COMMENT '驳回原因',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_declaration_no (declaration_no),
    INDEX idx_company_id (company_id),
    INDEX idx_contribution_month (contribution_month),
    INDEX idx_approval_status (approval_status)
) ENGINE=InnoDB COMMENT='单位缴存申报单';

DROP TABLE IF EXISTS contribution_detail;
CREATE TABLE contribution_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    declaration_id BIGINT COMMENT '申报单ID',
    declaration_no VARCHAR(32) COMMENT '申报单编号',
    employee_id BIGINT NOT NULL COMMENT '职工ID',
    employee_no VARCHAR(32) COMMENT '职工账号',
    employee_name VARCHAR(50) COMMENT '职工姓名',
    company_id BIGINT COMMENT '单位ID',
    branch_id BIGINT COMMENT '所属分支机构',
    contribution_month DATE NOT NULL COMMENT '缴存月份',
    contribution_base DECIMAL(12,2) NOT NULL COMMENT '缴存基数',
    company_ratio DECIMAL(6,4) NOT NULL COMMENT '单位比例',
    personal_ratio DECIMAL(6,4) NOT NULL COMMENT '个人比例',
    company_amount DECIMAL(12,2) NOT NULL COMMENT '单位缴存额',
    personal_amount DECIMAL(12,2) NOT NULL COMMENT '个人缴存额',
    total_amount DECIMAL(12,2) NOT NULL COMMENT '月缴额合计',
    business_type VARCHAR(20) COMMENT '业务类型: MONTHLY正常缴存 SUPPLEMENT补缴 ADJUST调整',
    status TINYINT DEFAULT 0 COMMENT '状态 0待入账 1已入账',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_declaration_id (declaration_id),
    INDEX idx_employee_id (employee_id),
    INDEX idx_contribution_month (contribution_month)
) ENGINE=InnoDB COMMENT='缴存明细表';

-- ============================================================
-- 四、提取申请
-- ============================================================

DROP TABLE IF EXISTS withdrawal_application;
CREATE TABLE withdrawal_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    application_no VARCHAR(32) NOT NULL UNIQUE COMMENT '申请编号',
    employee_id BIGINT NOT NULL COMMENT '职工ID',
    employee_no VARCHAR(32) COMMENT '职工账号',
    employee_name VARCHAR(50) COMMENT '职工姓名',
    id_card VARCHAR(20) COMMENT '身份证号',
    phone VARCHAR(20) COMMENT '手机号',
    company_id BIGINT COMMENT '单位ID',
    branch_id BIGINT COMMENT '所属分支机构',
    withdrawal_type VARCHAR(30) NOT NULL COMMENT '提取类型编码',
    withdrawal_type_desc VARCHAR(50) COMMENT '提取类型描述',
    application_amount DECIMAL(16,2) NOT NULL COMMENT '申请金额',
    approved_amount DECIMAL(16,2) DEFAULT 0 COMMENT '审批金额',
    max_withdrawable_amount DECIMAL(16,2) COMMENT '可提额度',
    contribution_months INT COMMENT '已缴存月数',
    account_balance DECIMAL(16,2) COMMENT '申请时余额',
    approval_status TINYINT DEFAULT 0 COMMENT '审批状态 0待审批 1审批中 2通过 3驳回 4超时转办',
    current_approval_level INT DEFAULT 1 COMMENT '当前审批层级',
    submit_time DATETIME COMMENT '提交时间',
    approval_time DATETIME COMMENT '审批完成时间',
    reject_reason VARCHAR(500) COMMENT '驳回原因',
    arrival_date DATE COMMENT '预计到账日期',
    bank_account VARCHAR(30) COMMENT '收款银行账号',
    bank_name VARCHAR(50) COMMENT '开户银行',
    support_materials TEXT COMMENT '证明材料(JSON)',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_application_no (application_no),
    INDEX idx_employee_id (employee_id),
    INDEX idx_company_id (company_id),
    INDEX idx_approval_status (approval_status),
    INDEX idx_withdrawal_type (withdrawal_type)
) ENGINE=InnoDB COMMENT='提取申请表';

-- ============================================================
-- 五、贷款申请与账户
-- ============================================================

DROP TABLE IF EXISTS loan_application;
CREATE TABLE loan_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    application_no VARCHAR(32) NOT NULL UNIQUE COMMENT '申请编号',
    employee_id BIGINT NOT NULL COMMENT '职工ID',
    employee_no VARCHAR(32) COMMENT '职工账号',
    employee_name VARCHAR(50) COMMENT '职工姓名',
    id_card VARCHAR(20) COMMENT '身份证号',
    phone VARCHAR(20) COMMENT '手机号',
    company_id BIGINT COMMENT '单位ID',
    branch_id BIGINT COMMENT '所属分支机构',
    loan_type VARCHAR(30) NOT NULL COMMENT '贷款类型: FIRST首套 SECOND二套',
    application_amount DECIMAL(16,2) NOT NULL COMMENT '申请金额',
    loan_term INT NOT NULL COMMENT '贷款期限(年)',
    house_appraisal_value DECIMAL(16,2) COMMENT '房屋评估价',
    down_payment DECIMAL(16,2) COMMENT '首付金额',
    max_loanable_amount DECIMAL(16,2) COMMENT '最高可贷额度',
    approved_amount DECIMAL(16,2) DEFAULT 0 COMMENT '审批金额',
    interest_rate DECIMAL(8,6) COMMENT '执行年利率',
    repayment_method VARCHAR(30) COMMENT '还款方式: EQUAL_INSTALLMENT等额本息 EQUAL_PRINCIPAL等额本金',
    continuous_months INT COMMENT '连续缴存月数',
    credit_score INT COMMENT '信用评分',
    credit_level VARCHAR(20) COMMENT '信用等级: excellent/good/average/poor',
    pre_audit_report MEDIUMTEXT COMMENT '预审报告全文',
    approval_status TINYINT DEFAULT 0 COMMENT '审批状态 0待审批 1审批中 2通过 3驳回 4超时转办',
    current_approval_level INT DEFAULT 1 COMMENT '当前审批层级',
    submit_time DATETIME COMMENT '提交时间',
    approval_time DATETIME COMMENT '审批完成时间',
    reject_reason VARCHAR(500) COMMENT '驳回原因',
    house_address VARCHAR(255) COMMENT '房屋地址',
    house_type VARCHAR(30) COMMENT '房屋类型',
    guarantee_type VARCHAR(30) COMMENT '担保方式',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_application_no (application_no),
    INDEX idx_employee_id (employee_id),
    INDEX idx_approval_status (approval_status)
) ENGINE=InnoDB COMMENT='贷款申请表';

DROP TABLE IF EXISTS loan_account;
CREATE TABLE loan_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    loan_account_no VARCHAR(32) NOT NULL UNIQUE COMMENT '贷款账户号',
    loan_application_id BIGINT NOT NULL COMMENT '贷款申请ID',
    application_no VARCHAR(32) COMMENT '申请编号',
    employee_id BIGINT NOT NULL COMMENT '职工ID',
    company_id BIGINT COMMENT '单位ID',
    branch_id BIGINT COMMENT '所属分支机构',
    loan_amount DECIMAL(16,2) NOT NULL COMMENT '放款金额',
    paid_principal DECIMAL(16,2) DEFAULT 0 COMMENT '已还本金',
    paid_interest DECIMAL(16,2) DEFAULT 0 COMMENT '已还利息',
    remaining_principal DECIMAL(16,2) COMMENT '剩余本金',
    remaining_interest DECIMAL(16,2) COMMENT '剩余利息',
    interest_rate DECIMAL(8,6) NOT NULL COMMENT '执行年利率',
    loan_term INT NOT NULL COMMENT '贷款期限(月)',
    paid_term INT DEFAULT 0 COMMENT '已还期数',
    repayment_method VARCHAR(30) COMMENT '还款方式',
    first_repayment_date DATE COMMENT '首次还款日',
    last_repayment_date DATE COMMENT '最近还款日期',
    maturity_date DATE COMMENT '贷款到期日',
    total_penalty DECIMAL(16,2) DEFAULT 0 COMMENT '累计罚息',
    paid_penalty DECIMAL(16,2) DEFAULT 0 COMMENT '已缴罚息',
    overdue_days INT DEFAULT 0 COMMENT '逾期天数',
    overdue_times INT DEFAULT 0 COMMENT '逾期次数',
    repayment_status TINYINT DEFAULT 0 COMMENT '还款状态 0待还款 1正常 2结清 3逾期 4部分还款',
    settlement_time DATETIME COMMENT '结清时间',
    status TINYINT DEFAULT 1 COMMENT '状态',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_loan_account_no (loan_account_no),
    INDEX idx_employee_id (employee_id),
    INDEX idx_repayment_status (repayment_status)
) ENGINE=InnoDB COMMENT='贷款账户表';

DROP TABLE IF EXISTS repayment_plan;
CREATE TABLE repayment_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    loan_account_id BIGINT NOT NULL COMMENT '贷款账户ID',
    loan_account_no VARCHAR(32) COMMENT '贷款账户号',
    employee_id BIGINT COMMENT '职工ID',
    branch_id BIGINT COMMENT '所属分支机构',
    term_no INT NOT NULL COMMENT '期号',
    due_date DATE NOT NULL COMMENT '到期还款日',
    principal_amount DECIMAL(14,2) NOT NULL COMMENT '应还本金',
    interest_amount DECIMAL(14,2) NOT NULL COMMENT '应还利息',
    total_amount DECIMAL(14,2) NOT NULL COMMENT '应还合计',
    paid_principal DECIMAL(14,2) DEFAULT 0 COMMENT '已还本金',
    paid_interest DECIMAL(14,2) DEFAULT 0 COMMENT '已还利息',
    penalty_amount DECIMAL(14,2) DEFAULT 0 COMMENT '罚息金额',
    paid_penalty DECIMAL(14,2) DEFAULT 0 COMMENT '已缴罚息',
    actual_pay_time DATETIME COMMENT '实际还款时间',
    overdue_days INT DEFAULT 0 COMMENT '逾期天数',
    repayment_status TINYINT DEFAULT 0 COMMENT '还款状态 0待还款 1正常 2已结清 3逾期 4部分还款',
    reminder_sent TINYINT DEFAULT 0 COMMENT '提醒是否已发送 0否 1是',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_loan_account_id (loan_account_id),
    INDEX idx_due_date (due_date),
    INDEX idx_repayment_status (repayment_status)
) ENGINE=InnoDB COMMENT='还款计划表';

-- ============================================================
-- 六、审批记录与催收任务
-- ============================================================

DROP TABLE IF EXISTS approval_record;
CREATE TABLE approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    approval_no VARCHAR(32) NOT NULL UNIQUE COMMENT '审批记录号',
    business_id BIGINT NOT NULL COMMENT '业务ID',
    business_type VARCHAR(30) NOT NULL COMMENT '业务类型: contribution/withdrawal/loan',
    business_no VARCHAR(32) COMMENT '业务编号',
    applicant_id BIGINT COMMENT '申请人ID',
    applicant_name VARCHAR(50) COMMENT '申请人姓名',
    approver_id BIGINT COMMENT '审批人ID',
    approver_name VARCHAR(50) COMMENT '审批人姓名',
    approver_role_id BIGINT COMMENT '审批人角色ID',
    approver_role_name VARCHAR(50) COMMENT '审批人角色名称',
    approval_level INT NOT NULL COMMENT '审批层级',
    total_levels INT NOT NULL COMMENT '审批总层级',
    approval_action TINYINT DEFAULT 0 COMMENT '审批动作 0待处理 1处理中',
    approval_result TINYINT DEFAULT 1 COMMENT '审批结果 1待处理 2通过 3驳回',
    approval_comment VARCHAR(500) COMMENT '审批意见',
    submit_time DATETIME COMMENT '提交时间',
    approval_time DATETIME COMMENT '审批时间',
    deadline_time DATETIME COMMENT '截止时间(超时自动转办)',
    timeout_escalated TINYINT DEFAULT 0 COMMENT '是否已超时转办 0否 1是',
    escalated_to_id BIGINT COMMENT '被转交的上级审批人ID',
    branch_id BIGINT COMMENT '所属分支机构',
    status TINYINT DEFAULT 0 COMMENT '状态 0待处理 1处理中 2已处理',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_business (business_id, business_type),
    INDEX idx_approver (approver_id),
    INDEX idx_deadline (deadline_time),
    INDEX idx_timeout (timeout_escalated, approval_result)
) ENGINE=InnoDB COMMENT='审批记录表';

DROP TABLE IF EXISTS approval_rule_config;
CREATE TABLE approval_rule_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    business_type VARCHAR(30) NOT NULL COMMENT '业务类型: contribution/withdrawal/loan',
    business_type_name VARCHAR(50) COMMENT '业务类型名称',
    approval_level INT NOT NULL COMMENT '审批层级',
    level_name VARCHAR(50) COMMENT '层级名称',
    approver_role_id BIGINT COMMENT '审批人角色ID',
    approver_role_name VARCHAR(50) COMMENT '审批人角色名称',
    amount_threshold DECIMAL(16,2) COMMENT '金额阈值',
    auto_escalation TINYINT DEFAULT 0 COMMENT '是否自动加签 0否 1是',
    escalation_threshold DECIMAL(16,2) COMMENT '加签金额阈值(超过此金额自动加签)',
    timeout_hours INT COMMENT '超时时间(小时)',
    escalation_type VARCHAR(30) COMMENT '加签类型',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0停用',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_business_type (business_type),
    INDEX idx_approval_level (business_type, approval_level)
) ENGINE=InnoDB COMMENT='审批规则配置表';

DROP TABLE IF EXISTS collection_task;
CREATE TABLE collection_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_no VARCHAR(32) NOT NULL UNIQUE COMMENT '任务编号',
    loan_account_id BIGINT NOT NULL COMMENT '贷款账户ID',
    loan_account_no VARCHAR(32) COMMENT '贷款账户号',
    employee_id BIGINT COMMENT '借款人ID',
    employee_name VARCHAR(50) COMMENT '借款人姓名',
    employee_phone VARCHAR(20) COMMENT '借款人电话',
    assignee_id BIGINT COMMENT '分配信贷员ID',
    assignee_name VARCHAR(50) COMMENT '分配信贷员姓名',
    branch_id BIGINT COMMENT '所属分支机构',
    overdue_days INT NOT NULL COMMENT '逾期天数',
    overdue_amount DECIMAL(16,2) NOT NULL COMMENT '逾期总金额',
    overdue_principal DECIMAL(16,2) COMMENT '逾期本金',
    overdue_interest DECIMAL(16,2) COMMENT '逾期利息',
    penalty_amount DECIMAL(16,2) COMMENT '罚息金额',
    task_level INT DEFAULT 1 COMMENT '任务等级 1初级 2中级 3高级',
    task_status TINYINT DEFAULT 0 COMMENT '任务状态 0待处理 1处理中 2已完成 3已关闭',
    assign_time DATETIME COMMENT '分配时间',
    deadline_time DATETIME COMMENT '截止时间',
    finish_time DATETIME COMMENT '完成时间',
    collection_method VARCHAR(30) COMMENT '催收方式: PHONE短信电话 VISIT上门 LEGAL法律',
    collection_result TEXT COMMENT '催收结果',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_task_no (task_no),
    INDEX idx_assignee (assignee_id),
    INDEX idx_task_status (task_status),
    INDEX idx_overdue_days (overdue_days)
) ENGINE=InnoDB COMMENT='催收任务表';

-- ============================================================
-- 七、资金报表与通知记录
-- ============================================================

DROP TABLE IF EXISTS fund_report;
CREATE TABLE fund_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    report_no VARCHAR(32) NOT NULL UNIQUE COMMENT '报表编号',
    report_date DATE NOT NULL COMMENT '报表日期',
    report_type VARCHAR(20) NOT NULL COMMENT '报表类型: TOTAL全辖 BRANCH分支',
    branch_id BIGINT COMMENT '分支机构ID',
    branch_name VARCHAR(100) COMMENT '分支机构名称',
    new_company_count INT DEFAULT 0 COMMENT '本期新增单位数',
    new_employee_count INT DEFAULT 0 COMMENT '本期新增职工数',
    active_company_count INT DEFAULT 0 COMMENT '活跃单位数',
    active_employee_count INT DEFAULT 0 COMMENT '活跃职工数',
    monthly_contribution DECIMAL(18,2) DEFAULT 0 COMMENT '本月缴存额',
    monthly_company_contribution DECIMAL(18,2) DEFAULT 0 COMMENT '本月单位缴存',
    monthly_personal_contribution DECIMAL(18,2) DEFAULT 0 COMMENT '本月个人缴存',
    total_contribution DECIMAL(18,2) DEFAULT 0 COMMENT '本年累计缴存',
    monthly_withdrawal DECIMAL(18,2) DEFAULT 0 COMMENT '本月提取额',
    total_withdrawal DECIMAL(18,2) DEFAULT 0 COMMENT '本年累计提取',
    monthly_loan_issue DECIMAL(18,2) DEFAULT 0 COMMENT '本月发放贷款',
    monthly_loan_count INT DEFAULT 0 COMMENT '本月贷款笔数',
    total_loan_balance DECIMAL(18,2) DEFAULT 0 COMMENT '贷款余额',
    overdue_loan_count INT DEFAULT 0 COMMENT '逾期贷款笔数',
    overdue_loan_amount DECIMAL(18,2) DEFAULT 0 COMMENT '逾期贷款金额',
    overdue_rate DECIMAL(10,6) DEFAULT 0 COMMENT '逾期率(%)',
    monthly_repayment DECIMAL(18,2) DEFAULT 0 COMMENT '本月还款额',
    monthly_penalty DECIMAL(18,2) DEFAULT 0 COMMENT '本月罚息收入',
    fund_balance DECIMAL(18,2) DEFAULT 0 COMMENT '资金池余额',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_report_date (report_date),
    INDEX idx_branch (branch_id),
    INDEX idx_report_type (report_type)
) ENGINE=InnoDB COMMENT='资金运行日报表';

DROP TABLE IF EXISTS notification_record;
CREATE TABLE notification_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    notification_no VARCHAR(32) NOT NULL UNIQUE COMMENT '通知编号',
    notification_type INT NOT NULL COMMENT '通知类型 1账户变动 2审批状态 3还款提醒 4逾期警示 5系统通知 6催收任务',
    notification_type_desc VARCHAR(30) COMMENT '通知类型描述',
    receiver_id BIGINT NOT NULL COMMENT '接收人ID',
    receiver_type VARCHAR(20) NOT NULL COMMENT '接收人类型: EMPLOYEE COMPANY STAFF',
    receiver_name VARCHAR(50) COMMENT '接收人姓名',
    receiver_phone VARCHAR(20) COMMENT '接收人手机号',
    company_id BIGINT COMMENT '所属单位ID',
    branch_id BIGINT COMMENT '所属分支机构',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content TEXT NOT NULL COMMENT '通知内容',
    business_id BIGINT COMMENT '关联业务ID',
    business_type VARCHAR(30) COMMENT '关联业务类型',
    business_no VARCHAR(32) COMMENT '关联业务编号',
    push_channel VARCHAR(50) COMMENT '推送渠道: WEBSOCKET,SMS,APP,EMAIL',
    push_time DATETIME COMMENT '推送时间',
    read_status TINYINT DEFAULT 0 COMMENT '读取状态 0未读 1已读',
    read_time DATETIME COMMENT '读取时间',
    push_status TINYINT DEFAULT 1 COMMENT '推送状态 0失败 1成功 2部分失败',
    fail_reason VARCHAR(500) COMMENT '失败原因',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_notification_no (notification_no),
    INDEX idx_receiver (receiver_id, receiver_type),
    INDEX idx_read_status (read_status),
    INDEX idx_push_time (push_time)
) ENGINE=InnoDB COMMENT='通知推送记录表';

-- ============================================================
-- 八、风控评分明细表
-- ============================================================

DROP TABLE IF EXISTS loan_risk_score_detail;
CREATE TABLE loan_risk_score_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    loan_application_id BIGINT NOT NULL COMMENT '贷款申请ID',
    application_no VARCHAR(32) COMMENT '申请编号',
    employee_id BIGINT COMMENT '职工ID',
    dimension_code VARCHAR(30) NOT NULL COMMENT '维度编码: CONTRIBUTION/CREDIT/DEBT/HOUSE_VALUATION',
    dimension_name VARCHAR(50) NOT NULL COMMENT '维度名称',
    full_score DECIMAL(8,2) NOT NULL COMMENT '满分',
    actual_score DECIMAL(8,2) NOT NULL COMMENT '实际得分',
    deduction DECIMAL(8,2) NOT NULL COMMENT '扣分',
    score_rule VARCHAR(500) COMMENT '评分规则说明',
    deduction_reason VARCHAR(500) COMMENT '扣分原因',
    sort_order INT DEFAULT 0 COMMENT '排序',
    weight DECIMAL(6,4) COMMENT '权重',
    weighted_score DECIMAL(8,2) COMMENT '加权得分',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_loan_application_id (loan_application_id),
    INDEX idx_dimension_code (dimension_code)
) ENGINE=InnoDB COMMENT='贷款风控评分明细表';

-- ============================================================
-- 九、审批规则配置表
-- ============================================================

DROP TABLE IF EXISTS approval_rule_config;
CREATE TABLE approval_rule_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    business_type VARCHAR(30) NOT NULL COMMENT '业务类型: contribution/withdrawal/loan',
    business_type_name VARCHAR(50) NOT NULL COMMENT '业务类型名称',
    approval_level INT NOT NULL COMMENT '审批层级',
    level_name VARCHAR(50) COMMENT '层级名称: 如 初审/复审/终审',
    approver_role_id BIGINT COMMENT '审批人角色ID',
    approver_role_name VARCHAR(50) COMMENT '审批人角色名称',
    amount_threshold DECIMAL(16,2) COMMENT '金额阈值(该级审批的最低金额要求)',
    auto_escalation TINYINT DEFAULT 0 COMMENT '是否自动加签 0否 1是',
    escalation_threshold DECIMAL(16,2) COMMENT '加签阈值(金额超过此值时自动加签)',
    escalation_type VARCHAR(30) COMMENT '加签类型: SAME_LEVEL同级加签 UPPER_LEVEL上级加签',
    timeout_hours INT COMMENT '超时时间(小时)，null则使用系统默认',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0停用',
    create_time DATETIME COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    create_by VARCHAR(50) COMMENT '创建人',
    update_by VARCHAR(50) COMMENT '更新人',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_business_type (business_type),
    INDEX idx_approval_level (approval_level)
) ENGINE=InnoDB COMMENT='审批规则配置表';

-- 修改approval_record表，新增审批人角色字段
ALTER TABLE approval_record ADD COLUMN approver_role_id BIGINT COMMENT '审批人角色ID' AFTER approver_name;
ALTER TABLE approval_record ADD COLUMN approver_role_name VARCHAR(50) COMMENT '审批人角色名称' AFTER approver_role_id;

-- ============================================================
-- 十、审批规则初始化数据
-- ============================================================

INSERT INTO approval_rule_config (business_type, business_type_name, approval_level, level_name, approver_role_id, approver_role_name, amount_threshold, auto_escalation, escalation_threshold, escalation_type, timeout_hours, sort_order, status) VALUES
('contribution', '单位缴存申报', 1, '初审', 201, '缴存初审员', 0, 0, NULL, NULL, 4, 1, 1),
('contribution', '单位缴存申报', 2, '复审', 202, '缴存复审员', 50000, 1, 500000, 'UPPER_LEVEL', 4, 2, 1),
('contribution', '单位缴存申报', 3, '终审', 203, '缴存终审主管', 200000, 0, NULL, NULL, 4, 3, 1),
('withdrawal', '个人提取申请', 1, '初审', 301, '提取初审员', 0, 0, NULL, NULL, 4, 1, 1),
('withdrawal', '个人提取申请', 2, '复审', 302, '提取复审员', 30000, 1, 200000, 'UPPER_LEVEL', 4, 2, 1),
('withdrawal', '个人提取申请', 3, '终审', 303, '提取终审主管', 100000, 0, NULL, NULL, 4, 3, 1),
('loan', '贷款申请', 1, '初审', 401, '贷款初审员', 0, 0, NULL, NULL, 4, 1, 1),
('loan', '贷款申请', 2, '复审', 402, '贷款复审员', 200000, 1, 500000, 'UPPER_LEVEL', 4, 2, 1),
('loan', '贷款申请', 3, '终审', 403, '贷款终审主管', 500000, 1, 800000, 'UPPER_LEVEL', 4, 3, 1);

-- ============================================================
-- 十一、初始化数据
-- ============================================================

INSERT INTO sys_branch (branch_code, branch_name, branch_type, leader, contact_phone, address, sort_order) VALUES
('B001', '市公积金管理中心', '总部', '张主任', '0571-80000001', '杭州市西湖区文三路100号', 1),
('B002', '上城区分中心', '分支', '李主任', '0571-80000002', '杭州市上城区望江路100号', 2),
('B003', '下城区分中心', '分支', '王主任', '0571-80000003', '杭州市下城区庆春路100号', 3),
('B004', '西湖区分中心', '分支', '赵主任', '0571-80000004', '杭州市西湖区文二路100号', 4),
('B005', '滨江区分中心', '分支', '陈主任', '0571-80000005', '杭州市滨江区江南大道100号', 5);

INSERT INTO sys_company (company_code, company_name, unified_social_credit_code, legal_person, legal_person_id_card, contact_person, contact_phone, address, branch_id, employee_count, contribution_ratio, status) VALUES
('C0000001', '杭州阿里巴巴网络技术有限公司', '913300007161530000', '马云', '330100196409100001', '张三', '13800000001', '杭州市余杭区文一西路969号', 1, 20000, 0.12, 1),
('C0000002', '杭州网易有限公司', '913300007161530001', '丁磊', '330100197108150001', '李四', '13800000002', '杭州市滨江区网商路599号', 5, 8000, 0.12, 1),
('C0000003', '杭州海康威视数字技术股份有限公司', '913300007161530002', '陈宗年', '330100196501010001', '王五', '13800000003', '杭州市滨江区阡陌路555号', 5, 40000, 0.12, 1);

INSERT INTO sys_employee (employee_no, name, id_card, phone, gender, birth_date, company_id, company_code, branch_id, contribution_base, company_ratio, personal_ratio, contribution_months, continuous_months, first_contribution_date, last_contribution_date, credit_score, status) VALUES
('E0000001', '张三', '330100199001010001', '13900000001', '男', '1990-01-01', 1, 'C0000001', 1, 20000.00, 0.12, 0.12, 60, 60, '2021-06-01', '2026-05-01', 780, 1),
('E0000002', '李四', '330100199203150002', '13900000002', '女', '1992-03-15', 1, 'C0000001', 1, 18000.00, 0.12, 0.12, 48, 48, '2022-06-01', '2026-05-01', 720, 1),
('E0000003', '王五', '330100198807200003', '13900000003', '男', '1988-07-20', 2, 'C0000002', 5, 25000.00, 0.12, 0.12, 72, 72, '2020-06-01', '2026-05-01', 800, 1),
('E0000004', '赵六', '330100199511100004', '13900000004', '女', '1995-11-10', 2, 'C0000002', 5, 15000.00, 0.12, 0.12, 24, 24, '2024-06-01', '2026-05-01', 680, 1),
('E0000005', '孙七', '330100198512310005', '13900000005', '男', '1985-12-31', 3, 'C0000003', 5, 30000.00, 0.12, 0.12, 120, 120, '2016-06-01', '2026-05-01', 820, 1);

INSERT INTO fund_account (account_no, account_type, employee_id, company_id, branch_id, balance, frozen_amount, total_contribution, total_withdrawal, company_contribution, personal_contribution, interest_income, status) VALUES
('FA0000001', 'PERSONAL', 1, 1, 1, 345600.00, 0, 345600.00, 0, 172800.00, 172800.00, 0, 1),
('FA0000002', 'PERSONAL', 2, 1, 1, 248832.00, 0, 248832.00, 0, 124416.00, 124416.00, 0, 1),
('FA0000003', 'PERSONAL', 3, 2, 5, 518400.00, 0, 518400.00, 0, 259200.00, 259200.00, 0, 1),
('FA0000004', 'PERSONAL', 4, 2, 5, 103680.00, 0, 103680.00, 0, 51840.00, 51840.00, 0, 1),
('FA0000005', 'PERSONAL', 5, 3, 5, 864000.00, 0, 864000.00, 0, 432000.00, 432000.00, 0, 1);

COMMIT;
