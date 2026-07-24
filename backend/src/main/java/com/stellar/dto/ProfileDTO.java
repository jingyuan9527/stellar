package com.stellar.dto;

import lombok.Data;

/**
 * 个人介绍更新 DTO。
 */
@Data
public class ProfileDTO {

    private String nickname;

    private String avatar;

    private String bio;

    private String skills;

    private String links;

    private String title;

    private String about;

    private String location;
}
