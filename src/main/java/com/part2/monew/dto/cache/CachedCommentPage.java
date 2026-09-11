package com.part2.monew.dto.cache;

import java.util.List;

public record CachedCommentPage(
    List<CachedComment> comments,
    long totalElements
) {
}
