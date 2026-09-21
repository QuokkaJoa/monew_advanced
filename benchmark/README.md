# 댓글 조회 성능 측정

측정 조건과 실행 절차를 기록한다. 결과는 측정 후 이 문서에 추가한다.

## 측정 구간

| 구간 | 인덱스 | 캐시 | 분산락 | 보려는 것 |
|---|---|---|---|---|
| A | 없음 | 끔 | - | 원래 얼마나 느렸나 |
| B | 있음 | 끔 | - | 인덱스로 얼마나 해결되나 |
| C | 있음 | 켬 | 끔 | 캐시만으로 충분한가 |
| D | 있음 | 켬 | 켬 | 분산락이 무엇을 막았나 |

**분산락 결과를 미리 가정하지 않는다.** 인덱스 적용으로 재생성 비용이 크게 줄어 C와 D가 차이 없을 수 있다. 차이가 없으면 그대로 기록한다.

## 구간 전환 방법

인덱스는 SQL로, 캐시와 락은 환경변수로 바꾼다.

```bash
# 인덱스 끄기
psql ... -c "DROP INDEX IF EXISTS idx_comments_article_created"

# 인덱스 켜기
psql ... -f benchmark/03-index.sql

# 캐시·락은 앱 재시작으로 전환
CACHE_ENABLED=false                          # 구간 A, B
CACHE_ENABLED=true CACHE_LOCK_ENABLED=false  # 구간 C
CACHE_ENABLED=true CACHE_LOCK_ENABLED=true   # 구간 D (기본값)
```

## 로컬 실행 절차

### 1. DB 컨테이너

```bash
docker run -d --name idxtest \
  -e POSTGRES_PASSWORD=test -e POSTGRES_USER=monew -e POSTGRES_DB=monew \
  -p 5433:5432 postgres:15

psql -h localhost -p 5433 -U monew -d monew -f create_tables.sql
psql -h localhost -p 5433 -U monew -d monew -f benchmark/01-generate-data.sql
```

### 2. Redis

```bash
docker run -d --name redis-server -p 6379:6379 redis:7.0
```

### 3. 애플리케이션

`.env`를 읽되 아래 값은 덮어쓴다.

```bash
set -a; source .env; set +a

export DB_PORT=5433 DB_REPLICA_PORT=5433 DB_PASSWORD=test
export SPRING_JPA_HIBERNATE_DDL_AUTO=none
export SPRING_SQL_INIT_MODE=never

./gradlew bootRun --args='--spring.profiles.active=local'
```

덮어쓰는 이유는 세 가지다.

- `DB_PORT` : `.env`의 5432는 다른 컨테이너가 쓴다. 측정용은 5433이다.
- `DB_REPLICA_PORT` : `local` 프로필이 master와 replica 두 DB를 요구한다. 측정에서는 둘 다 같은 DB를 보게 한다.
- `DDL_AUTO`, `SQL_INIT_MODE` : `local` 프로필 기본값이 `create-drop`이라 그대로 두면 생성한 데이터가 전부 지워진다.

앱이 떴는지 확인한다.

```bash
curl "http://127.0.0.1:8080/api/comments?articleId=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee&limit=10" \
  -H "Monew-Request-User-ID: 00000000-0000-4000-8000-000000000001"
```

### 4. 부하 테스트

환경변수는 반드시 `-e` 플래그로 넘긴다. 셸 변수로만 두면 k6가 읽지 않는다.

```bash
# 스모크 테스트 (약 50초)
k6 run -e START_RATE=10 -e WRITE_RATE=1 -e RAMP=5s -e STEP_HOLD=5s k6/test3.js

# 본 측정 (약 12분 30초)
k6 run -e START_RATE=100 k6/test3.js
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://127.0.0.1:8080` | 측정 대상 주소. AWS에서는 ALB 주소 |
| `START_RATE` | 100 | 첫 계단의 초당 요청 수. 1·2·4·8·16배로 5계단 |
| `WRITE_RATE` | 0 | 초당 댓글 생성 수. 0이면 읽기만 |
| `RAMP` | 30s | 계단 사이 증가 시간 |
| `STEP_HOLD` | 2m | 각 계단 유지 시간 |
| `PRE_VUS` / `MAX_VUS` | 500 / 3000 | 미리 확보할 VU와 상한 |

구간별 시작값은 예상 한계에 맞춘다.

```
구간 A  START_RATE=25    (예상 한계 초당 70건 부근)
구간 B  START_RATE=100   (예상 한계 초당 330건 부근)
구간 C  START_RATE=500
구간 D  START_RATE=500
```

## 결과를 읽는 방법

임계값은 두 개만 둔다.

```
http_req_failed    < 1%     에러가 나기 시작하는 지점
dropped_iterations < 100    부하 생성이 못 따라가면 측정 무효
```

**`p95`는 임계값이 아니라 곡선으로 읽는다.** 계단마다 p95를 기록하고 300ms를 넘는 지점을 한계로 본다. 기준이 바뀌어도 곡선은 그대로라 재측정 없이 다시 해석할 수 있다.

`p95 < 300ms`는 부트캠프 당시 사용한 값이다. 일반적인 API 목표(100~200ms)보다 느슨하다.

첫 계단은 워밍업(JIT 컴파일, 커넥션 풀 채우기)이므로 판정에서 제외한다.

## 캐시 미스 원인 확인

`[Cache Put]` 로그 횟수가 DB 조회 횟수다.

```bash
grep -c '\[Cache Put\]' <앱 로그>
grep -c '\[Cache Hit\]' <앱 로그>
```

읽기만 돌렸을 때와 쓰기를 섞었을 때를 비교하면 미스의 주 원인을 알 수 있다.

로컬 스모크 테스트(50초, 초당 쓰기 1건) 참고값:

```
댓글 생성      50건
Cache Put     51회
Cache Hit  2,667회
```

TTL이 1분이므로 만료로 인한 미스는 최대 1회다. 나머지 50회는 쓰기 무효화 때문이다. 미스의 주 원인이 TTL 만료가 아니라 무효화라는 뜻이며, TTL 지터로는 완화되지 않는다.

## 주의

- 데이터 생성기의 파라미터는 전부 가정이며 관측값이 아니다. `benchmark/01-generate-data.sql` 머리말 참조.
- 절대 수치를 "이 서비스는 이만큼 처리한다"로 읽지 않는다. 구간 간 상대 비교에만 쓴다.
- AWS에서 측정할 때는 금요일 종료 시점에 리소스가 남아 있으면 삭제한다.
