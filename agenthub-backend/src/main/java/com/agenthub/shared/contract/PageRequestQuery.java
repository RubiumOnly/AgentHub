package com.agenthub.shared.contract;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Standardized pagination, sorting, and cursor query contract for API v1 endpoints.
 */
public final class PageRequestQuery implements ContextQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT_BY = "createdAt";
    public static final String DEFAULT_SORT_DIR = "DESC";

    private static final Set<String> ALLOWED_SORT_DIRECTIONS = Set.of("ASC", "DESC");

    private final int page;
    private final int size;
    private final String sortBy;
    private final String sortDirection;
    private final String cursor;
    private final BoundedContext context;

    public PageRequestQuery(Integer page,
                            Integer size,
                            String sortBy,
                            String sortDirection,
                            String cursor) {
        this(page, size, sortBy, sortDirection, cursor, BoundedContext.OPS);
    }

    public PageRequestQuery(Integer page,
                            Integer size,
                            String sortBy,
                            String sortDirection,
                            String cursor,
                            BoundedContext context) {
        this.page = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int requestedSize = (size == null || size < 1) ? DEFAULT_SIZE : size;
        this.size = Math.min(requestedSize, MAX_SIZE);
        this.sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy.trim();
        String normalizedDir = (sortDirection == null || sortDirection.isBlank())
                ? DEFAULT_SORT_DIR
                : sortDirection.trim().toUpperCase();
        this.sortDirection = ALLOWED_SORT_DIRECTIONS.contains(normalizedDir) ? normalizedDir : DEFAULT_SORT_DIR;
        this.cursor = (cursor == null || cursor.isBlank()) ? null : cursor.trim();
        this.context = context != null ? context : BoundedContext.OPS;
    }

    @Override
    public BoundedContext boundedContext() {
        return context;
    }

    public static PageRequestQuery of(Integer page, Integer size) {
        return new PageRequestQuery(page, size, DEFAULT_SORT_BY, DEFAULT_SORT_DIR, null);
    }

    public static PageRequestQuery of(Integer page, Integer size, String sortBy, String sortDir) {
        return new PageRequestQuery(page, size, sortBy, sortDir, null);
    }

    public static PageRequestQuery ofDefaults() {
        return new PageRequestQuery(DEFAULT_PAGE, DEFAULT_SIZE, DEFAULT_SORT_BY, DEFAULT_SORT_DIR, null);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getOffset() {
        return (page - 1) * size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public String getSortDir() {
        return sortDirection;
    }

    public boolean isAscending() {
        return "ASC".equalsIgnoreCase(sortDirection);
    }

    public String getCursor() {
        return cursor;
    }

    public <T> PageResultView<T> slice(List<T> allItems) {
        if (allItems == null || allItems.isEmpty()) {
            return new PageResultView<>(Collections.emptyList(), page, size, 0L, 0, false, null, sortBy, sortDirection);
        }
        long total = allItems.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int fromIndex = Math.min(getOffset(), allItems.size());
        int toIndex = Math.min(fromIndex + size, allItems.size());
        List<T> pageItems = allItems.subList(fromIndex, toIndex);
        boolean hasNext = toIndex < allItems.size();
        String nextCursor = hasNext ? String.valueOf(toIndex) : null;
        return new PageResultView<>(pageItems, page, size, total, totalPages, hasNext, nextCursor, sortBy, sortDirection);
    }
}
