package com.stellar.dto;

import lombok.Data;

/**
 * 作品橱窗新增/编辑 DTO。
 */
@Data
public class ShowcaseDTO {

    private String type;

    private String title;

    private String summary;

    private String coverUrl;

    private String content;

    private String mediaUrl;

    private String link;

    private String tags;

    private Integer sortOrder;

    private Integer visible;
}
