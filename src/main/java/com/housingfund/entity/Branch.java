package com.housingfund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_branch")
public class Branch extends BaseEntity {

    private String branchCode;

    private String branchName;

    private String branchType;

    private String leader;

    private String contactPhone;

    private String address;

    private Long parentId;

    private Integer sortOrder;

    private Integer status;
}
