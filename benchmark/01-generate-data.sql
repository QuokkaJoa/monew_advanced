-- 댓글 조회 성능 측정용 데이터 생성
--
-- === 파라미터는 전부 가정이다. 관측값이 아니다. ===
--
-- 아래 숫자 중 실제 서비스에서 측정한 값은 하나도 없다.
-- 업계에서 흔히 쓰는 어림값과 통념을 참고해 정한 합성 시나리오이며,
-- 면접이나 문서에서 관측 결과처럼 설명해서는 안 된다.
--
--   가입자     100,000명   가정 (중소 규모 서비스)
--   작성자 비율       5%   가정 (참여 불균등 통념 90-9-1 참고)
--   1인당 작성     100건   가정
--   동시 접속        3%    가정 (업계 어림값)
--   기사         1,000개   가정 (약 2주치 수집분)
--   논리 삭제       약 5%   가정
--
-- 결과:
--   댓글 500,000건
--     - 인기 기사 10개 : 각 20,000건  (합 200,000)
--     - 나머지 990개   : 각 약 303건  (합 300,000)
--
-- 파라미터가 임의값이어도 같은 데이터에서 구간을 비교하므로
-- 인덱스·캐시의 상대 효과 측정에는 영향이 없다.
-- 절대 수치를 "이 서비스는 이만큼 처리한다"로 읽어서는 안 된다.
--
-- 측정 대상 기사: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee (댓글 20,000건)
-- k6/test3.js 가 호출하는 articleId 와 동일하다.
--
-- UUID 는 전부 결정적으로 생성하므로 몇 번을 돌려도 같은 값이 나온다.
-- 비활성 판정에 소수 19를 쓴다. 기사 배정(10, 990)과 서로소라 특정 기사로 쏠리지 않는다.
--
-- 실행:
--   psql -h <host> -p <port> -U monew -d monew -f benchmark/01-generate-data.sql

\timing on

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
FROM generate_series(1, 100000) i;

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
FROM generate_series(1, 1000) i;

INSERT INTO comments_managements (
    comment_management_id, user_id, news_article_id,
    content, like_count, active, created_at, updated_at
)
SELECT
    ('20000000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
    ('00000000-0000-4000-8000-' || lpad(((i % 5000) + 1)::text, 12, '0'))::uuid,
    CASE
        WHEN i <= 200000 AND (i % 10) = 0
            THEN 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'::uuid
        WHEN i <= 200000
            THEN ('10000000-0000-4000-8000-' || lpad(((i % 10) + 1)::text, 12, '0'))::uuid
        ELSE ('10000000-0000-4000-8000-' || lpad(((i % 990) + 11)::text, 12, '0'))::uuid
    END,
    '측정용 댓글 ' || i,
    i % 50,
    (i % 19) <> 0,
    timestamp '2026-01-01 00:00:00' + ((i / 30)::int) * interval '1 second',
    timestamp '2026-01-01 00:00:00' + ((i / 30)::int) * interval '1 second'
FROM generate_series(1, 500000) i;

UPDATE news_articles a
SET comment_counts = c.cnt
FROM (
    SELECT news_article_id, count(*) AS cnt
    FROM comments_managements
    WHERE active = TRUE
    GROUP BY news_article_id
) c
WHERE a.news_article_id = c.news_article_id;

ANALYZE;
