// 场景 B Feed：VUS 个 VU 以重度用户身份反复读 Feed 首页，重度用户共 100 个，VU 轮流使用
import http from 'k6/http';
import { BASE_URL, authHeaders, login, phaseOptions, prepareWith, track } from './lib.js';

export { handleSummary, idle } from './lib.js';

const VUS = 200;
const HEAVY_USERS = 100;

export const options = phaseOptions(VUS);

export const setup = prepareWith(() => {
    const usernames = Array.from({ length: HEAVY_USERS }, (_, i) => `heavy_${String(i).padStart(3, '0')}`);
    return { tokens: login(usernames).map((user) => user.token) };
});

export default function (data) {
    track(http.get(`${BASE_URL}/feed`, authHeaders(data.tokens[(__VU - 1) % HEAVY_USERS])));
}
