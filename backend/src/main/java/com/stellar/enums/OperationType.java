package com.stellar.enums;

/**
 * 操作类型枚举，用于日志记录。
 */
public enum OperationType {

    LOGIN("登录"),
    LOGOUT("登出"),
    INSERT("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    QUERY("查询"),
    EXPORT("导出"),
    OTHER("其他");

    private final String description;

    OperationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
