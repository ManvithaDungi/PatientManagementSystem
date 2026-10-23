package com.securehealth.backend.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Bounds for list endpoints that used to return an entire table. */
public final class PageParams {
    public static final int DEFAULT_SIZE = 500;
    public static final int MAX_SIZE = 1000;

    private PageParams() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }
}
