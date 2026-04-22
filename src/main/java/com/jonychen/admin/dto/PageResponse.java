package com.jonychen.admin.dto;

import java.util.List;

/**
 * 分页响应
 *
 * @param data 数据列表
 * @param total 总数
 * @param page 当前页
 * @param pageSize 每页大小
 * @param <T> 数据类型
 * @author jonychen
 */
public record PageResponse<T>(List<T> data, long total, int page, int pageSize) {
    /** 创建分页响应 */
    public static <T> PageResponse<T> of(List<T> data, long total, int page, int pageSize) {
        return new PageResponse<>(data, total, page, pageSize);
    }
}
