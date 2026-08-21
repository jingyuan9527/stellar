package com.stellar.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人介绍更新 DTO
 */
@Data
public class ProfileDTO {

    @Size(max = 64, message = "昵称最长 64 字")
    private String nickname;

    /** 头像 URL（/file/{id} 或外链） */
    @Size(max = 500, message = "头像地址过长")
    private String avatar;

    /** TEXT 列，上限防滥用大文本写入 */
    @Size(max = 20000, message = "简介过长")
    private String bio;

    @Size(max = 500, message = "技能标签过长")
    private String skills;

    @Size(max = 20000, message = "链接内容过长")
    private String links;

    @Size(max = 100, message = "头衔过长")
    private String title;

    @Size(max = 100000, message = "关于我内容过长")
    private String about;

    @Size(max = 100, message = "所在地过长")
    private String location;
}
