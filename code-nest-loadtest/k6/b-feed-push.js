// 场景 B 推送耗时：普通作者 author_4999（4999 个粉丝，比大 V 阈值少 1）发文，测量推送到全部粉丝收件箱的耗时。
// 只在 feed.mode=push-pull 下有意义。分两个阶段：
//   prepare：登录作者和全部粉丝，每个粉丝读一次 Feed，让收件箱都存在（推送会跳过收件箱不存在的冷用户）；
//   measure：作者发文，反复读最后一个粉丝的 Feed 直到出现这篇文章。推送按粉丝 ID 升序进行，
//            ID 最大的粉丝最后收到，这时推送完成。测完删除文章，数据保持不变。
import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { BASE_URL, PREPARE_OPTIONS, authHeaders, login, mustData, prepareWith } from './lib.js';

export { idle } from './lib.js';

const PAGE_SIZE = 50;
const TIMEOUT_MS = 60_000;

export const options = __ENV.PHASE === 'prepare' ? PREPARE_OPTIONS : { vus: 1, iterations: 1 };

const pushDuration = new Trend('push_duration', true);

export const setup = prepareWith(() => {
    const author = login(['author_4999'])[0];
    const fans = [];
    let cursor = null;
    do {
        const page = mustData(http.get(`${BASE_URL}/users/${author.userId}/followers?size=${PAGE_SIZE}`
            + (cursor ? `&cursor=${cursor}` : ''), authHeaders(author.token)));
        // 造数账号的昵称与用户名相同
        fans.push(...page.list.map((item) => item.user.nickname));
        cursor = page.hasMore ? page.nextCursor : null;
    } while (cursor);

    let last = null;
    for (let i = 0; i < fans.length; i += PAGE_SIZE) {
        const users = login(fans.slice(i, i + PAGE_SIZE));
        http.batch(users.map((user) => ['GET', `${BASE_URL}/feed?size=1`, null, authHeaders(user.token)]))
            .forEach(mustData);
        for (const user of users) {
            if (last === null || BigInt(user.userId) > BigInt(last.userId)) {
                last = user;
            }
        }
    }
    return { fans: fans.length, authorToken: author.token, lastFanToken: last.token };
});

export default function (data) {
    const author = authHeaders(data.authorToken);
    const article = mustData(http.post(`${BASE_URL}/articles`, JSON.stringify({
        title: '推送耗时测量',
        content: '压测脚本发布的文章，测完即删除。',
        categoryId: 1,
    }), author));
    mustData(http.post(`${BASE_URL}/articles/${article.id}/publish`, null, author));
    const start = Date.now();

    const fan = authHeaders(data.lastFanToken);
    while (mustData(http.get(`${BASE_URL}/feed?size=1`, fan)).list[0]?.id !== article.id) {
        if (Date.now() - start > TIMEOUT_MS) {
            throw new Error(`${TIMEOUT_MS}ms 内最后一个粉丝仍未收到文章 ${article.id}`);
        }
    }
    pushDuration.add(Date.now() - start);

    mustData(http.del(`${BASE_URL}/articles/${article.id}`, null, author));
}

export function handleSummary(summary) {
    if (__ENV.PHASE === 'prepare') {
        return { [__ENV.DATA_FILE]: JSON.stringify(summary.setup_data) };
    }
    if (!summary.metrics.push_duration) {
        throw new Error('没有测到推送耗时');
    }
    const result = { pushMs: summary.metrics.push_duration.values.max };
    return { [__ENV.SUMMARY_FILE]: JSON.stringify(result), stdout: `${JSON.stringify(result)}\n` };
}
