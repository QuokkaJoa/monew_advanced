-- 측정 구간 전환용 인덱스 제어
--
-- 구간 A (인덱스 없음) : DROP 만 실행
-- 구간 B (인덱스만)    : CREATE 실행, 캐시는 끈 상태로 측정
-- 구간 C (인덱스+캐시) : 인덱스를 둔 채 애플리케이션 캐시를 켜고 측정
--
-- 실행:
--   psql ... -f benchmark/03-index.sql

DROP INDEX IF EXISTS idx_comments_article_created;

CREATE INDEX idx_comments_article_created
    ON comments_managements (news_article_id, created_at, comment_management_id)
    WHERE active = TRUE;

ANALYZE comments_managements;
