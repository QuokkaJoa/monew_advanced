package com.part2.monew.global.event;

import java.util.UUID;

public record ArticleCommentsChanged(
    UUID articleId
) {

}
