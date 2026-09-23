import http from 'k6/http';
import { check } from 'k6';

const BASE_URL   = __ENV.BASE_URL   || 'http://127.0.0.1:8080';
const START_RATE = Number(__ENV.START_RATE || 100);
const WRITE_RATE = Number(__ENV.WRITE_RATE || 0);
const STEP_HOLD  = __ENV.STEP_HOLD || '2m';
const RAMP       = __ENV.RAMP || '30s';

const ARTICLE_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
const READER_ID  = '00000000-0000-4000-8000-000000000001';
const WRITER_ID  = '00000000-0000-4000-8000-000000000002';

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

export function readFirstPage() {
  const url = `${BASE_URL}/api/comments?articleId=${ARTICLE_ID}&limit=10&direction=DESC`;
  const res = http.get(url, {
    headers: {
      'Content-Type': 'application/json',
      'Monew-Request-User-ID': READER_ID,
    },
    tags: { op: 'read' },
  });
  check(res, { 'read 200': (r) => r.status === 200 });
}

export function writeComment() {
  const res = http.post(
    `${BASE_URL}/api/comments`,
    JSON.stringify({
      articleId: ARTICLE_ID,
      userId: WRITER_ID,
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
