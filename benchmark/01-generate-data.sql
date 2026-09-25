-- 측정용 데이터 생성
--
-- 숫자 근거는 benchmark/README.md 의 "데이터 가정" 절에 있다.
--
-- 실행:
--   docker exec -i idxtest psql -U monew -d monew < benchmark/01-generate-data.sql

\set users          100000
\set articles       1000
\set hot_articles   10
\set hot_comments   3000
\set tail_comments  300
\set commenters     100000
\set skew           9
\set likes_spread   300000
\set likes_top      50000
\set top_n          11

TRUNCATE TABLE
    activity_details,
    comments_like,
    comments_managements,
    interests_news_articles,
    news_articles,
    users
RESTART IDENTITY CASCADE;

INSERT INTO users (user_id, nickname, email, password, active)
SELECT
    ('00000000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    '사용자' || i,
    'user' || i || '@benchmark.local',
    'benchmark',
    TRUE
FROM generate_series(1, :users) i;

INSERT INTO news_articles (
    news_article_id, source_in, source_url, title,
    published_date, summary, view_counts, comment_counts, is_deleted
)
SELECT
    CASE WHEN i = 1
         THEN 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'::uuid
         ELSE ('10000000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid
    END,
    'benchmark',
    'https://benchmark.local/article/' || i,
    '기사 ' || i,
    timestamp '2026-01-01 00:00:00',
    '측정용 기사 요약 ' || i,
    0, 0, FALSE
FROM generate_series(1, :articles) i;

INSERT INTO comments_managements (
    comment_management_id, user_id, news_article_id,
    content, like_count, active, created_at, updated_at
)
SELECT
    ('20000000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    ('00000000-0000-4000-8000-' || lpad(
        (1 + floor(:commenters * power(((i::bigint * 7919) % 1000000)::numeric / 1000000, :skew)))::bigint::text,
        12, '0'))::uuid,
    CASE WHEN a.n = 1
         THEN 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'::uuid
         ELSE ('10000000-0000-4000-8000-' || lpad(a.n::text, 12, '0'))::uuid
    END,
    '측정용 댓글 ' || i,
    i % 50,
    (i % 19) <> 0,
    timestamp '2026-01-01 00:00:00' + ((i / 30)::int) * interval '1 second',
    timestamp '2026-01-01 00:00:00' + ((i / 30)::int) * interval '1 second'
FROM generate_series(1, :hot_articles * :hot_comments + (:articles - :hot_articles) * :tail_comments) i
CROSS JOIN LATERAL (
    SELECT CASE
        WHEN i <= :hot_articles * :hot_comments
            THEN ((i - 1) % :hot_articles) + 1
        ELSE ((i - :hot_articles * :hot_comments - 1) % (:articles - :hot_articles)) + :hot_articles + 1
    END AS n
) a;

INSERT INTO comments_like (comment_like_id, user_id, comment_management_id, created_at, updated_at)
SELECT
    ('30000000-0000-4000-8000-' || lpad(j::text, 12, '0'))::uuid,
    ('00000000-0000-4000-8000-' || lpad((((j::bigint * 7919) % :users) + 1)::text, 12, '0'))::uuid,
    ('20000000-0000-4000-8000-' || lpad(j::text, 12, '0'))::uuid,
    timestamp '2026-01-01 01:00:00' + ((j / 100)::int) * interval '1 second',
    timestamp '2026-01-01 01:00:00' + ((j / 100)::int) * interval '1 second'
FROM generate_series(1, :likes_spread) j;

INSERT INTO comments_like (comment_like_id, user_id, comment_management_id, created_at, updated_at)
SELECT
    ('31000000-0000-4000-8000-' || lpad(j::text, 12, '0'))::uuid,
    ('00000000-0000-4000-8000-' || lpad(j::text, 12, '0'))::uuid,
    t.comment_management_id,
    timestamp '2026-01-01 02:00:00' + ((j / 100)::int) * interval '1 second',
    timestamp '2026-01-01 02:00:00' + ((j / 100)::int) * interval '1 second'
FROM generate_series(1, :likes_top) j
JOIN (
    SELECT comment_management_id,
           row_number() OVER (ORDER BY news_article_id, rn) AS slot
    FROM (
        SELECT comment_management_id, news_article_id,
               row_number() OVER (PARTITION BY news_article_id
                                  ORDER BY created_at DESC, comment_management_id DESC) AS rn
        FROM comments_managements
        WHERE active
          AND (news_article_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'::uuid
               OR (right(news_article_id::text, 12))::bigint BETWEEN 2 AND :hot_articles)
    ) ranked
    WHERE rn <= :top_n
) t ON t.slot = ((j % (:hot_articles * :top_n)) + 1);

UPDATE news_articles a
SET comment_counts = c.cnt
FROM (
    SELECT news_article_id, count(*) AS cnt
    FROM comments_managements
    WHERE active = TRUE
    GROUP BY news_article_id
) c
WHERE a.news_article_id = c.news_article_id;

ANALYZE users;
ANALYZE news_articles;
ANALYZE comments_managements;
ANALYZE comments_like;

SELECT '댓글 ' || (SELECT count(*) FROM comments_managements)
    || ' (활성 ' || (SELECT count(*) FROM comments_managements WHERE active) || ')'
    || ' | 좋아요 ' || (SELECT count(*) FROM comments_like)
    || ' | 기사 ' || (SELECT count(*) FROM news_articles)
    || ' | 사용자 ' || (SELECT count(*) FROM users) AS "규모";

WITH per AS (
  SELECT CASE WHEN news_article_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'::uuid THEN 1
              ELSE (right(news_article_id::text, 12))::bigint END AS num,
         count(*) AS total, count(*) FILTER (WHERE active) AS act
    FROM comments_managements GROUP BY 1
)
SELECT CASE WHEN num <= 10 THEN '화제 기사' ELSE '롱테일' END AS "구분",
       count(*) AS "기사 수",
       min(total) || '~' || max(total) AS "댓글",
       min(act) || '~' || max(act) AS "활성"
FROM per GROUP BY 1 ORDER BY 2;

WITH c AS (SELECT user_id, count(*) n FROM comments_managements GROUP BY 1)
SELECT CASE WHEN n = 1 THEN '댓글 1개만' WHEN n <= 10 THEN '2~10개'
            WHEN n <= 100 THEN '11~100개' ELSE '100개 넘게' END AS "작성자 구간",
       count(*) AS "사람 수",
       round(100.0 * sum(n) / (SELECT count(*) FROM comments_managements), 1) AS "이들이 쓴 댓글 비율"
FROM c GROUP BY 1 ORDER BY min(n);
