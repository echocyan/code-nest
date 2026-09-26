// 场景 A 计数：VUS 个 VU 各用一个普通用户账号，对同一篇文章反复点赞、取消
import http from 'k6/http';
import { BASE_URL, authHeaders, login, mustData, phaseOptions, prepareWith, track } from './lib.js';

export { handleSummary, idle } from './lib.js';

const VUS = 200;

export const options = phaseOptions(VUS);

/**
 * 被点赞的是最新发布的一篇文章；账号取不写文章的普通用户 user_20000 起的 VUS 个。
 */
export const setup = prepareWith(() => {
    const article = mustData(http.get(`${BASE_URL}/articles?size=1`)).list[0];
    const usernames = Array.from({ length: VUS }, (_, i) => `user_${20000 + i}`);
    return { articleId: article.id, tokens: login(usernames).map((user) => user.token) };
});

export default function (data) {
    const url = `${BASE_URL}/articles/${data.articleId}/like`;
    const params = authHeaders(data.tokens[__VU - 1]);
    track(http.put(url, null, params));
    track(http.del(url, null, params));
}
