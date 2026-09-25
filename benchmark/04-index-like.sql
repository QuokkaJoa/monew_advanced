-- 좋아요 조회 인덱스 제어
--
-- 기본은 켜둔 상태다. create_tables.sql 이 테이블과 함께 만든다.
-- 이 인덱스가 얼마나 값어치가 있는지 따로 재고 싶을 때만 껐다 켠다.
--
-- 끄기 : DROP 줄만 실행
-- 켜기 : 전체 실행
--
-- 실행:
--   psql ... -f benchmark/04-index-like.sql

DROP INDEX IF EXISTS idx_comments_like_user_comment;

CREATE INDEX idx_comments_like_user_comment
    ON comments_like (user_id, comment_management_id);

ANALYZE comments_like;
