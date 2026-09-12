package com.part2.monew.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;

import java.sql.Timestamp;
import java.util.UUID;

public record CommentRequest(
    UUID articleId,
    String orderBy,
    String direction,
    @Min(1) @Max(100) Integer limit,
    String cursor,
    Timestamp after,
    UUID requestUserId
) {
    @Builder
    public CommentRequest {
    }
}
