package com.example.ledger.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PageResponse<T> {

    private final List<T> data;
    private final Pagination pagination;

    private PageResponse(List<T> data, Pagination pagination) {
        this.data = data;
        this.pagination = pagination;
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        List<T> items = page.getContent().stream().map(mapper).collect(Collectors.toList());
        Pagination pagination = new Pagination(page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
        return new PageResponse<>(items, pagination);
    }

    public List<T> getData() {
        return data;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public static class Pagination {
        private final int page;
        private final int limit;
        private final long totalRecords;
        private final int totalPages;

        Pagination(int page, int limit, long totalRecords, int totalPages) {
            this.page = page;
            this.limit = limit;
            this.totalRecords = totalRecords;
            this.totalPages = totalPages;
        }

        public int getPage() {
            return page;
        }

        public int getLimit() {
            return limit;
        }

        public long getTotalRecords() {
            return totalRecords;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public boolean isHasNext() {
            return page < totalPages;
        }

        public boolean isHasPrevious() {
            return page > 1;
        }
    }
}
