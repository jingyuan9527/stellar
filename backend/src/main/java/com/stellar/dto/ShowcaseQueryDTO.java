package com.stellar.dto;

import lombok.Data;

/**
 * 作品橱窗分页查询 DTO。
 */
@Data
public class ShowcaseQueryDTO {

    private String type;

    private String title;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
