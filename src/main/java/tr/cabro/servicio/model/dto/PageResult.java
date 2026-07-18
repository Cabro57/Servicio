package tr.cabro.servicio.model.dto;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Veritabanından LIMIT/OFFSET ile çekilen bir sayfanın sonucunu taşır.
 */
@Getter
public class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final long totalItems;
    private final int totalPages;

    public PageResult(List<T> items, int page, int pageSize, long totalItems) {
        this.items = items != null ? items : Collections.emptyList();
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
        this.totalPages = (int) Math.max(1, Math.ceil((double) totalItems / pageSize));
    }
}
