package com.part2.monew.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class CursorResponse {
    private List<CommentResponse> content = new ArrayList<>();
    private String nextCursor;
    private String nextAfter;
    private int size;
    private Long totalElements;
    private Boolean hasNext;

    @Builder
    protected CursorResponse(List<CommentResponse> content, String nextCursor, String nextAfter, int size, Long totalElements, Boolean hasNext) {
        this.content = content;
        this.nextCursor = nextCursor;
        this.nextAfter = nextAfter;
        this.size = size;
        this.totalElements = totalElements;
        this.hasNext = hasNext;
    }

    public static CursorResponse of(List<CommentResponse> comments, Long totalElements, int limit) {
        boolean hasNext = comments.size() > limit;
        List<CommentResponse> content = hasNext ? comments.subList(0, limit) : comments;

        return CursorResponse.builder()
                .content(content)
                .nextCursor(getNextCursor(content))
                .nextAfter(getNextCursor(content))
                .size(content.size())
                .totalElements(totalElements)
                .hasNext(hasNext)
                .build();
    }

    private static String getNextCursor(List<CommentResponse> content) {
        return content.isEmpty() ? null : content.get(content.size() - 1).getCreatedAt().toString();
    }


}
