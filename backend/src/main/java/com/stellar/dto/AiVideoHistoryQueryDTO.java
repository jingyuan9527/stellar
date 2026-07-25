package com.stellar.dto;

import lombok.Data;

@Data
public class AiVideoHistoryQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
