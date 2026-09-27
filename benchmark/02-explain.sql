-- 댓글 조회 실행계획 측정
--
-- 요청 하나가 날리는 쿼리 셋을 모두 잰다.
--   1. 첫 페이지 목록   CommentRepositoryCustomImpl.findCommentsPage
--   2. 전체 개수        CommentRepositoryCustomImpl.totalCount
--   3. 좋아요 여부      CommentLikeRepository.findLikedCommentIds
--
-- 3번은 캐시가 맞아도 매 요청 실행된다. 사용자별로 달라 캐시에 넣을 수 없다.
--
-- 대상 기사는 benchmark/01-generate-data.sql 이 만든 인기 기사이며
-- k6/test3.js 가 호출하는 articleId 와 동일하다.
--
-- 실행:
--   psql -h <host> -p <port> -U monew -d monew -f benchmark/02-explain.sql

\timing on

EXPLAIN (ANALYZE, BUFFERS)
SELECT cm.comment_management_id, cm.content, cm.created_at, cm.like_count,
       u.nickname, na.title
FROM comments_managements cm
         JOIN users u ON u.user_id = cm.user_id
         JOIN news_articles na ON na.news_article_id = cm.news_article_id
WHERE cm.news_article_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
  AND cm.active = TRUE
ORDER BY cm.created_at DESC, cm.comment_management_id DESC
LIMIT 11;

EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*)
FROM comments_managements
WHERE news_article_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
  AND active = TRUE;

EXPLAIN (ANALYZE, BUFFERS)
SELECT cl.comment_management_id
FROM comments_like cl
WHERE cl.user_id = '00000000-0000-4000-8000-000000000001'
  AND cl.comment_management_id IN (
      SELECT comment_management_id
      FROM comments_managements
      WHERE news_article_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
        AND active = TRUE
      ORDER BY created_at DESC, comment_management_id DESC
      LIMIT 11);
