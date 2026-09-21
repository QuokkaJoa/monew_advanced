-- 댓글 첫 페이지 조회 실행계획 측정
--
-- CommentRepositoryCustomImpl.findCommentsPage 가 생성하는 쿼리와 같은 형태다.
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
