// 场景 D 缓存：VUS 个 VU 匿名访问文章详情，文章按 Zipf 分布抽取，请求集中在排名前 10 的文章上
import http from 'k6/http';
import { BASE_URL, mustData, phaseOptions, prepareWith, track } from './lib.js';

export { handleSummary, idle } from './lib.js';

const VUS = 300;

/**
 * 候选文章数与 Zipf 指数：第 k 名的访问概率正比于 1/k^s，前 10 名约占 57% 的请求。
 */
const POOL = 1000;
const EXPONENT = 1.2;
const PAGE_SIZE = 50;

export const options = phaseOptions(VUS);

/**
 * 按排名累加的概率，抽样时二分查找。
 */
const cumulative = (() => {
    const weights = Array.from({ length: POOL }, (_, k) => 1 / Math.pow(k + 1, EXPONENT));
    const total = weights.reduce((sum, w) => sum + w, 0);
    let acc = 0;
    return weights.map((w) => (acc += w / total));
})();

/**
 * 候选文章取最新发布的 POOL 篇，按发布时间倒序排名。
 */
export const setup = prepareWith(() => {
    const ids = [];
    for (let page = 1; ids.length < POOL; page++) {
        const list = mustData(http.get(`${BASE_URL}/articles?page=${page}&size=${PAGE_SIZE}`)).list;
        ids.push(...list.map((article) => article.id));
    }
    return { articleIds: ids.slice(0, POOL) };
});

function zipfRank() {
    const u = Math.random();
    let low = 0;
    let high = POOL - 1;
    while (low < high) {
        const mid = (low + high) >> 1;
        if (cumulative[mid] < u) {
            low = mid + 1;
        } else {
            high = mid;
        }
    }
    return low;
}

export default function (data) {
    track(http.get(`${BASE_URL}/articles/${data.articleIds[zipfRank()]}`));
}
