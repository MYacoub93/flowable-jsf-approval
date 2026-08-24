package com.example.approval.entity;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * One page of a server-side paginated result plus the total row count needed
 * to render a pager ({@code PAGE_SIZE} rows are ever materialised in memory).
 *
 * @param <T> row type
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<T> rows;
    private final long totalRows;
    private final int pageNumber;   // 1-based
    private final int pageSize;

    public PageResult(List<T> rows, long totalRows, int pageNumber, int pageSize) {
        this.rows = rows == null ? Collections.emptyList() : rows;
        this.totalRows = totalRows;
        this.pageNumber = Math.max(pageNumber, 1);
        this.pageSize = pageSize;
    }

    public List<T> getRows() {
        return rows;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** First 1-based page. */
    public int getFirstPage() {
        return 1;
    }

    /** Last 1-based page (at least 1, even for an empty result). */
    public int getLastPage() {
        return pageSize <= 0 ? 1 : (int) Math.max(1, (totalRows + pageSize - 1) / pageSize);
    }

    public boolean isHasNext() {
        return pageNumber < getLastPage();
    }

    public boolean isHasPrevious() {
        return pageNumber > 1;
    }

    public static <T> PageResult<T> empty(int pageNumber, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, pageNumber, pageSize);
    }
}