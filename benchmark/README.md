# 댓글 조회 성능 측정

어떤 조건에서 어떻게 쟀는지 적어둔다. 결과는 재고 나서 여기에 붙인다.

## 측정 구간

| 구간 | 인덱스 | 캐시 | 분산락 | 보려는 것 |
|---|---|---|---|---|
| A | 없음 | 끔 | - | 원래 얼마나 느렸나 |
| B | 있음 | 끔 | - | 인덱스로 얼마나 해결되나 |
| C | 있음 | 켬 | 끔 | 캐시만으로 충분한가 |
| D | 있음 | 켬 | 켬 | 분산락이 무엇을 막았나 |

**분산락이 효과가 있다고 미리 정해두지 않는다.** 인덱스를 넣으면서 DB 조회가 빨라졌다. 그래서 C와 D가 같게 나올 수도 있다. 같게 나오면 같다고 적는다.

## 구간 바꾸는 법

인덱스는 SQL로 바꾼다. 캐시와 락은 환경변수로 바꾼다.

```bash
# 인덱스 지우기
psql ... -c "DROP INDEX IF EXISTS idx_comments_article_created"

# 인덱스 만들기
psql ... -f benchmark/03-index.sql

# 캐시와 락은 앱을 다시 띄우면서 바꾼다
CACHE_ENABLED=false                          # 구간 A, B
CACHE_ENABLED=true CACHE_LOCK_ENABLED=false  # 구간 C
CACHE_ENABLED=true CACHE_LOCK_ENABLED=true   # 구간 D (기본값)
```

## 로컬에서 돌리는 순서

### 1. DB 띄우기

```bash
docker run -d --name idxtest \
  -e POSTGRES_PASSWORD=test -e POSTGRES_USER=monew -e POSTGRES_DB=monew \
  -p 5433:5432 postgres:15

psql -h localhost -p 5433 -U monew -d monew -f create_tables.sql
psql -h localhost -p 5433 -U monew -d monew -f benchmark/01-generate-data.sql
```

### 2. Redis 띄우기

```bash
docker run -d --name redis-server -p 6379:6379 redis:7.0
```

### 3. 앱 띄우기

`.env`를 쓰되 아래 값은 바꿔서 쓴다.

```bash
set -a; source .env; set +a

export DB_PORT=5433 DB_REPLICA_PORT=5433 DB_PASSWORD=test
export SPRING_JPA_HIBERNATE_DDL_AUTO=none
export SPRING_SQL_INIT_MODE=never

./gradlew bootRun --args='--spring.profiles.active=local'
```

바꾸는 이유는 이렇다.

- `DB_PORT` — `.env`에 적힌 5432는 다른 컨테이너가 쓰고 있다. 측정용 DB는 5433이다.
- `DB_REPLICA_PORT` — `local` 프로필은 DB를 두 개 찾는다. 잴 때는 둘 다 같은 DB를 보게 한다.
- `DDL_AUTO`, `SQL_INIT_MODE` — 안 바꾸면 앱이 뜰 때 테이블을 지우고 새로 만든다. 넣어둔 50만 건이 날아간다.

앱이 떴는지 확인한다.

```bash
curl "http://127.0.0.1:8080/api/comments?articleId=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee&limit=10" \
  -H "Monew-Request-User-ID: 00000000-0000-4000-8000-000000000001"
```

### 4. 부하 주기

환경변수는 꼭 `-e`를 붙여서 넘긴다. 셸에 export만 해두면 k6가 못 읽는다.

```bash
# 짧게 확인만 (약 50초)
k6 run -e START_RATE=10 -e WRITE_RATE=1 -e RAMP=5s -e STEP_HOLD=5s k6/test3.js

# 제대로 재기 (약 12분 30초)
k6 run -e START_RATE=100 k6/test3.js
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://127.0.0.1:8080` | 때릴 주소. AWS에서는 ALB 주소를 넣는다 |
| `START_RATE` | 100 | 첫 계단에서 초당 몇 건을 보낼지. 여기서 1, 2, 4, 8, 16배로 다섯 계단 올린다 |
| `WRITE_RATE` | 0 | 초당 댓글을 몇 건 쓸지. 0이면 읽기만 한다 |
| `RAMP` | 30s | 다음 계단으로 올리는 데 걸리는 시간 |
| `STEP_HOLD` | 2m | 계단 하나를 버티는 시간 |
| `PRE_VUS` / `MAX_VUS` | 500 / 3000 | 미리 만들어둘 가상 사용자 수와 최대치 |

구간마다 시작값을 다르게 준다. 맥북에서 미리 재보고 정한 값이다.

```
구간 A  START_RATE=25     맥북에서 초당 128건에서 막혔다
구간 B  START_RATE=250    맥북에서 초당 1,900건에서 막혔다
구간 C  START_RATE=250    아직 안 재봤다. B 와 같은 값으로 시작한다
구간 D  START_RATE=250    아직 안 재봤다
```

AWS 는 장비가 달라서 숫자가 다르게 나온다. 위 값은 어디서부터 올릴지 정하는 용도다.

## 맥북에서 미리 재본 결과

AWS 본측정이 아니다. 시작값을 정하려고 돌려본 것이다. **AWS 결과와 같은 표에 넣지 않는다.**

| 구간 | p95 가 300ms 를 넘기 시작한 곳 | 더 못 받아내는 지점 |
|---|---|---|
| A 인덱스 없음 | 초당 100건과 200건 사이 | 초당 약 128건 |
| B 인덱스 | 초당 1,000건과 1,900건 사이 | 초당 약 1,900건 |

구간 A 는 초당 100건까지 p95 가 50ms 아래였다. 다음 계단에서 1,140ms 로 뛰었다.
구간 B 는 초당 1,000건에서 p95 가 4ms 였다. 초당 1,900건에서 425ms 가 됐다.

두 구간 모두 **처음 드롭이 생겼을 때 가상 사용자가 700명 아래였다.** 상한 3,000명에 한참 못 미친다.
그러니 k6 가 못 따라간 게 아니라 서버가 못 버틴 것이다. 이 결과는 쓸 수 있다.

맥북 자체는 초당 1,600건을 가상 사용자 7명으로 만들어냈다. 부하를 주는 쪽은 여유가 많았다.
AWS 에서는 구간 C 와 D 가 이보다 빨라질 수 있다. 그러면 맥북이 먼저 막힌다.
그래서 k6 를 EC2 에 두기로 했다.

## 결과 읽는 법

합격선은 두 개만 둔다.

```
http_req_failed    < 1%     에러가 나기 시작하는 지점
dropped_iterations < 100    부하를 못 만들어냈으면 그 결과는 못 쓴다
```

**`p95`는 합격선으로 쓰지 않는다.** 계단마다 값을 적어서 선으로 본다. 300ms를 넘기 시작하는 계단을 한계로 본다.

합격선으로 안 쓰는 이유가 있다. 나중에 기준을 바꾸고 싶어지면 다시 재야 한다. 값을 다 적어두면 기준만 바꿔서 다시 보면 된다.

300ms는 부트캠프 때 쓰던 값이다. 보통 API는 100~200ms를 본다. 그보다 느슨하다.

첫 계단은 빼고 본다. 앱이 막 떠서 아직 예열이 안 됐다. 자바가 코드를 빠르게 만들기 전이고 DB 연결도 다 안 만들어졌다.

## 캐시가 왜 비는지 확인하는 법

`[Cache Put]` 로그가 찍힌 횟수가 DB를 조회한 횟수다.

```bash
grep -c '\[Cache Put\]' <앱 로그>
grep -c '\[Cache Hit\]' <앱 로그>
```

읽기만 돌렸을 때와 쓰기를 섞었을 때를 비교하면 캐시가 왜 비는지 알 수 있다.

로컬에서 50초 동안 초당 1건씩 댓글을 쓰면서 재본 값이다.

```
댓글 생성      50건
Cache Put     51회
Cache Hit  2,667회
```

TTL이 1분이다. 그러니 시간이 지나서 캐시가 빈 건 많아야 한 번이다. 나머지 50번은 댓글을 쓸 때마다 캐시를 지웠기 때문이다.

캐시가 비는 주된 이유는 시간이 지나서가 아니라 글을 써서다. TTL을 조금씩 다르게 주는 방법(지터)으로는 이게 안 줄어든다. 지울 시점을 흩뿌려도 글 쓰는 건 그대로라서다.

## 조심할 것

- 데이터를 만들 때 쓴 숫자는 전부 내가 정한 가정이다. 어디서 재서 나온 값이 아니다. `benchmark/01-generate-data.sql` 맨 위에 적어뒀다.
- 나온 숫자를 "이 서비스는 초당 몇 건을 처리한다"로 읽으면 안 된다. 구간끼리 비교할 때만 쓴다.
- AWS에서 잴 때는 금요일에 일 끝내면서 남은 자원을 지운다.
