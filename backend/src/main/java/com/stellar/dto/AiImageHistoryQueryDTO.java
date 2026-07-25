package com.stellar.dto;

import lombok.Data;

@Data
public class AiImageHistoryQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
