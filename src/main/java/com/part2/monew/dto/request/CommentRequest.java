package com.part2.monew.dto.request;

import lombok.Builder;

import java.sql.Timestamp;
import java.util.UUID;

public record CommentRequest(
    UUID articleId,
    String orderBy,
    String direction,
    Integer limit,
    String cursor,
    Timestamp after,
    UUID requestUserId
) {
    @Builder
    public CommentRequest {
    }
}
