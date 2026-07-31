package com.stellar.system.dto;

import com.stellar.system.entity.SysUser;
import lombok.Data;

@Data
public class LoginResult {

    private String token;
    private SysUser userInfo;
}
