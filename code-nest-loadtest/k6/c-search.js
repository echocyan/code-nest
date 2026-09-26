// 场景 C 搜索：VUS 个 VU 匿名搜索，关键词从造数用的技术词库中随机抽取
import http from 'k6/http';
import { BASE_URL, phaseOptions, prepareWith, track } from './lib.js';

export { handleSummary, idle } from './lib.js';

const VUS = 50;

export const options = phaseOptions(VUS);

const KEYWORDS = open('../src/main/resources/vocabulary.txt').split('\n').filter((line) => line.trim() !== '');

/**
 * 无需准备数据，保留空的准备函数让各场景走同样的阶段。
 */
export const setup = prepareWith(() => ({}));

export default function () {
    const keyword = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    track(http.get(`${BASE_URL}/search/articles?q=${encodeURIComponent(keyword)}`));
}
