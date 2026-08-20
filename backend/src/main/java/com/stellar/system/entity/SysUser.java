package com.stellar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String nickname;

    private String avatar;

    private Integer status;

    /** 是否强制改密：1=默认口令首次登录需强制改密，改密成功后清 0 */
    private Integer mustChangePassword;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
