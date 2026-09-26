// 各场景脚本共用：阶段划分、登录、请求结果判定与结果导出。由 bench.sh 通过环境变量驱动，用法见 README.md
//
// 每个场景脚本分三个阶段运行，各是一次 k6 run：
//   prepare：setup() 登录账号、查出要访问的文章等，handleSummary 把 setup() 的返回值写入 DATA_FILE；
//   warmup、steady：各 VUS 个 VU 持续跑 WARMUP、DURATION，setup() 直接返回 DATA_FILE 的内容，不再发起准备请求，
//   服务端指标差值不含准备阶段的请求；steady 的指标写入 SUMMARY_FILE。
import http from 'k6/http';
import { Rate } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://nginx/api/v1';
export const PASSWORD = 'loadtest123';
export const PHASE = __ENV.PHASE;

/**
 * 业务失败率：HTTP 状态不是 200，或返回体的 code 不是 0。
 */
const errors = new Rate('errors');

/**
 * 准备阶段的 k6 选项：只执行 setup()，迭代跑空函数 {@link idle}，脚本需要重新导出它。
 * setup() 里用 http.batch 并发登录，登录要做 BCrypt 校验，数量多时耗时较长。
 */
export const PREPARE_OPTIONS = {
    scenarios: { prepare: { executor: 'shared-iterations', vus: 1, iterations: 1, exec: 'idle' } },
    setupTimeout: '20m',
    batch: 50,
    batchPerHost: 50,
};

export function idle() {
}

/**
 * 当前阶段的 k6 选项。
 *
 * @param vus 压测阶段的 VU 数
 */
export function phaseOptions(vus) {
    if (PHASE === 'prepare') {
        return PREPARE_OPTIONS;
    }
    return {
        scenarios: {
            [PHASE]: {
                executor: 'constant-vus',
                vus,
                duration: PHASE === 'warmup' ? __ENV.WARMUP : __ENV.DURATION,
            },
        },
        summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
    };
}

/**
 * 准备阶段写下的数据，其他阶段在 init 阶段读入。
 */
const preparedData = PHASE === 'prepare' ? null : open(__ENV.DATA_FILE);

/**
 * 包装场景的准备函数作为 setup()：k6 每次运行都会执行 setup()，准备阶段执行准备函数，
 * 其他阶段直接返回准备阶段写下的数据，不发请求。
 */
export function prepareWith(prepare) {
    return () => (PHASE === 'prepare' ? prepare() : JSON.parse(preparedData));
}

export function authHeaders(token) {
    return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

/**
 * 记录一次请求是否成功，并返回返回体的 data。
 */
export function track(res) {
    const ok = res.status === 200 && res.json('code') === 0;
    errors.add(!ok);
    return ok ? res.json('data') : null;
}

/**
 * 解析准备阶段的响应，失败时直接中止，避免带着不完整的数据开始压测。
 */
export function mustData(res) {
    if (res.status !== 200 || res.json('code') !== 0) {
        throw new Error(`${res.request.method} ${res.request.url} 失败：${res.status} ${res.body}`);
    }
    return res.json('data');
}

/**
 * 并发登录一批账号，返回与用户名一一对应的 {userId, token}。
 */
export function login(usernames) {
    const responses = http.batch(usernames.map((username) => ({
        method: 'POST',
        url: `${BASE_URL}/auth/login`,
        body: JSON.stringify({ username, password: PASSWORD }),
        params: { headers: { 'Content-Type': 'application/json' } },
    })));
    return responses.map(mustData);
}

/**
 * 按阶段导出结果：准备阶段导出 setup() 的数据，steady 阶段导出 QPS、延迟与错误率，warmup 不导出。
 */
export function handleSummary(data) {
    if (PHASE === 'prepare') {
        return { [__ENV.DATA_FILE]: JSON.stringify(data.setup_data) };
    }
    if (PHASE !== 'steady') {
        return {};
    }
    const duration = data.metrics.http_req_duration.values;
    const result = {
        requests: data.metrics.http_reqs.values.count,
        qps: data.metrics.http_reqs.values.rate,
        avgMs: duration.avg,
        p95Ms: duration['p(95)'],
        p99Ms: duration['p(99)'],
        errorRate: data.metrics.errors.values.rate,
    };
    return { [__ENV.SUMMARY_FILE]: JSON.stringify(result), stdout: `${JSON.stringify(result)}\n` };
}
