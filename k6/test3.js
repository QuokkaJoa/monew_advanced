import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL   = __ENV.BASE_URL   || 'http://127.0.0.1:8080';
const START_RATE = Number(__ENV.START_RATE || 100);
const WRITE_RATE = Number(__ENV.WRITE_RATE || 0);
const STEP_HOLD  = __ENV.STEP_HOLD || '2m';
const RAMP       = __ENV.RAMP || '30s';

const USER_COUNT = Number(__ENV.USER_COUNT || 100000);
const HOT_COUNT  = Number(__ENV.HOT_COUNT || 10);
const HOT_RATIO  = Number(__ENV.HOT_RATIO || 0.8);
const TAIL_FIRST = 11;
const TAIL_COUNT = 990;

function pad12(n) {
  let s = String(n);
  while (s.length < 12) s = '0' + s;
  return s;
}

const ALL_HOT = ['aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'];
for (let i = 2; i <= 10; i++) ALL_HOT.push('10000000-0000-4000-8000-' + pad12(i));
const HOT = ALL_HOT.slice(0, HOT_COUNT);

const readsHot  = new Counter('reads_hot');
const readsTail = new Counter('reads_tail');

const STEP_MULS = (__ENV.STEP_MULS || '1,2,4,8,16').split(',').map(Number);

const steps = STEP_MULS.flatMap((mul) => ([
  { target: START_RATE * mul, duration: RAMP },
  { target: START_RATE * mul, duration: STEP_HOLD },
]));

const scenarios = {
  reader: {
    executor: 'ramping-arrival-rate',
    startRate: START_RATE,
    timeUnit: '1s',
    preAllocatedVUs: Number(__ENV.PRE_VUS || 500),
    maxVUs: Number(__ENV.MAX_VUS || 3000),
    stages: steps,
    exec: 'readFirstPage',
  },
};

if (WRITE_RATE > 0) {
  scenarios.writer = {
    executor: 'constant-arrival-rate',
    rate: WRITE_RATE,
    timeUnit: '1s',
    duration: totalDuration(),
    preAllocatedVUs: 20,
    maxVUs: 100,
    exec: 'writeComment',
  };
}

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    dropped_iterations: ['count<100'],
  },
};

function randomUserId() {
  return '00000000-0000-4000-8000-' + pad12(1 + Math.floor(Math.random() * USER_COUNT));
}

function pickArticle() {
  if (Math.random() < HOT_RATIO) {
    return { id: HOT[Math.floor(Math.random() * HOT.length)], hot: true };
  }
  return {
    id: '10000000-0000-4000-8000-' + pad12(TAIL_FIRST + Math.floor(Math.random() * TAIL_COUNT)),
    hot: false,
  };
}

export function readFirstPage() {
  const article = pickArticle();
  const url = `${BASE_URL}/api/comments?articleId=${article.id}&limit=10&direction=DESC`;
  const res = http.get(url, {
    headers: {
      'Content-Type': 'application/json',
      'Monew-Request-User-ID': randomUserId(),
    },
    tags: { op: 'read' },
  });
  if (article.hot) {
    readsHot.add(1);
  } else {
    readsTail.add(1);
  }
  check(res, { 'read 200': (r) => r.status === 200 });
}

export function writeComment() {
  const article = pickArticle();
  const res = http.post(
    `${BASE_URL}/api/comments`,
    JSON.stringify({
      articleId: article.id,
      userId: randomUserId(),
      content: `부하 테스트 댓글 ${Date.now()}`,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { op: 'write' },
    },
  );
  check(res, { 'write 200': (r) => r.status === 200 });
}

function totalDuration() {
  const holdSec = toSeconds(STEP_HOLD);
  const rampSec = toSeconds(RAMP);
  return `${(holdSec + rampSec) * STEP_MULS.length}s`;
}

function toSeconds(v) {
  if (v.endsWith('m')) return Number(v.slice(0, -1)) * 60;
  if (v.endsWith('s')) return Number(v.slice(0, -1));
  return Number(v);
}
