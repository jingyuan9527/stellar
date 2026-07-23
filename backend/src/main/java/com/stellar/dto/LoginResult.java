package com.stellar.dto;

import com.stellar.entity.SysUser;
import lombok.Data;

@Data
public class LoginResult {

    private String token;
    private SysUser userInfo;
}
