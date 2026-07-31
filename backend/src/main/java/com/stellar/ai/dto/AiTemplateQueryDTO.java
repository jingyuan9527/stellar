package com.stellar.ai.dto;

import lombok.Data;

@Data
public class AiTemplateQueryDTO {

    private String name;
    private String platform;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
