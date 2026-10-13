package com.agenthub.shared.contract;

import java.util.Collections;
import java.util.List;

/**
 * Standardized paginated response envelope for API v1 list queries.
 */
public class PageResultView<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private String nextCursor;
    private String sortBy;
    private String sortDirection;

    public PageResultView() {
        this.items = Collections.emptyList();
    }

    public PageResultView(List<T> items,
                          int page,
                          int size,
                          long totalElements,
                          int totalPages,
                          boolean hasNext,
                          String nextCursor,
                          String sortBy,
                          String sortDirection) {
        this.items = items != null ? items : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.nextCursor = nextCursor;
        this.sortBy = sortBy;
        this.sortDirection = sortDirection;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items != null ? items : Collections.emptyList();
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public static <T> PageResultView<T> slice(List<T> allItems, PageRequestQuery query) {
        PageRequestQuery effective = query != null ? query : PageRequestQuery.ofDefaults();
        return effective.slice(allItems);
    }
}
