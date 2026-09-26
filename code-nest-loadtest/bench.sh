#!/usr/bin/env bash
# 跑一组压测：对场景的每一档开关，切换 → 准备数据 → 每轮预热、稳态压测并采集服务端指标差值，
# 最后取中位数写入 results/<日期>-<场景>.json。用法见 README.md
#   ./code-nest-loadtest/bench.sh <a|b|c|d> [轮数，默认 3]
set -euo pipefail
cd "$(dirname "$0")/.."

scenario=${1:?用法：bench.sh <a|b|c|d> [轮数，默认 3]}
runs=${2:-3}
[[ $runs =~ ^[1-9][0-9]*$ ]] || { echo "轮数应为正整数：$runs" >&2; exit 1; }
export WARMUP=${WARMUP:-30s} DURATION=${DURATION:-2m}

case $scenario in
    a) name=a-counter; switch=COUNTER_MODE; modes=(sync-db redis-async) ;;
    b) name=b-feed; switch=FEED_MODE; modes=(pull push-pull) ;;
    c) name=c-search; switch=SEARCH_MODE; modes=(mysql-like es) ;;
    d) name=d-cache; switch=CACHE_MODE; modes=(none redis two-level) ;;
    *) echo "未知场景：$scenario" >&2; exit 1 ;;
esac

# 中间文件：宿主机路径与 k6 容器内的路径
work=code-nest-loadtest/target/k6
k6_work=/loadtest/target/k6
mkdir -p "$work" code-nest-loadtest/results

compose() {
    docker compose -f compose.yaml -f compose.loadtest.yaml "$@"
}

step() {
    echo "== $1（已用 ${SECONDS}s）"
}

# 运行 k6 脚本的一个阶段；k6 以当前用户运行，写出的文件归当前用户所有
run_k6() {
    local script=$1 phase=$2
    rm -f "$work/$script.summary.json"
    compose run --rm --user "$(id -u):$(id -g)" \
        -e PHASE="$phase" -e WARMUP -e DURATION \
        -e DATA_FILE="$k6_work/$script.data.json" -e SUMMARY_FILE="$k6_work/$script.summary.json" \
        k6 run --quiet --no-usage-report "/loadtest/k6/$script.js"
}

sql() {
    compose exec -T mysql mysql -uroot -proot -N -e "$1" code_nest 2>/dev/null
}

# SHOW GLOBAL STATUS 中的数值项，输出 JSON 对象
mysql_status() {
    compose exec -T mysql mysql -uroot -proot -N -e "SHOW GLOBAL STATUS" 2>/dev/null \
        | jq -Rn '[inputs | split("\t") | select(.[1] | test("^[0-9]+$")) | {(.[0]): (.[1] | tonumber)}] | add'
}

# INFO commandstats 中各命令的调用次数，输出 JSON 对象
redis_calls() {
    compose exec -T redis redis-cli INFO commandstats | tr -d '\r' \
        | jq -Rn '[inputs | capture("^cmdstat_(?<cmd>[^:]+):calls=(?<calls>[0-9]+)") | {(.cmd): (.calls | tonumber)}]
            | add // {}'
}

# 两次快照的差值，只保留有变化的项
delta() {
    jq -n --argjson before "$1" --argjson after "$2" \
        '$after | with_entries(.value -= ($before[.key] // 0) | select(.value != 0))'
}

# 切换开关：停掉应用、清空 Redis 后以新的档启动，每一档都从同样的状态开始（与 seed.sh 相同，启动时重建布隆过滤器
# 与 Feed 发件箱）。token 存在 Redis 里，切换后要重新登录。应用容器重建后 IP 可能变化，重启 Nginx 重新解析
switch_mode() {
    compose stop app-1 app-2
    compose exec -T redis redis-cli FLUSHALL >/dev/null
    compose up -d --wait
    compose restart nginx
    curl -fs --retry 30 --retry-all-errors --retry-delay 1 -o /dev/null "http://localhost:8080/api/v1/articles?size=1" \
        || { echo "Nginx 未就绪" >&2; exit 1; }
}

# 场景 A：等待落库完成并断言计数表里的点赞数等于点赞行数。计数经 Outbox、MQ 进入 Redis，redis-async 每 5 秒
# 落库一次；每 6 秒查一次，连续两次相等才算落库完成，60 秒内做不到就报错退出
wait_counter() {
    local article_id count rows matched=0
    article_id=$(jq -r .articleId "$work/$name.data.json")
    for _ in $(seq 10); do
        sleep 6
        count=$(sql "SELECT COALESCE(MAX(like_count), 0) FROM article_stat WHERE article_id = $article_id")
        rows=$(sql "SELECT COUNT(*) FROM article_like WHERE article_id = $article_id")
        if [ "$count" = "$rows" ]; then
            matched=$((matched + 1))
        else
            matched=0
        fi
        if [ "$matched" = 2 ]; then
            echo "{\"articleId\": \"$article_id\", \"likeCount\": $count, \"likeRows\": $rows}"
            return
        fi
    done
    echo "计数不一致：文章 $article_id 的 like_count=$count，点赞行数=$rows" >&2
    exit 1
}

# 各轮结果的每个数值项各自取中位数；某一轮没有的项按 0 计（差值为 0 的项不输出）
medians='
def median: sort | if length % 2 == 1 then .[length / 2 | floor] else (.[length / 2 - 1] + .[length / 2]) / 2 end;
def medians: . as $runs
    | reduce ([$runs[] | paths(numbers)] | unique[]) as $path ({}; setpath($path; [$runs[] | getpath($path) // 0] | median));'

started_at=$(date -Iseconds)
modes_json='{}'
for mode in "${modes[@]}"; do
    export "$switch=$mode"
    step "$switch=$mode：切换并准备数据"
    switch_mode
    run_k6 "$name" prepare
    if [ "$mode" = push-pull ]; then
        step "准备推送耗时测量：登录 author_4999 的全部粉丝并建好收件箱"
        run_k6 b-feed-push prepare
    fi

    runs_file="$work/$name.$mode.runs.jsonl"
    : >"$runs_file"
    for run in $(seq "$runs"); do
        step "$switch=$mode 第 $run/$runs 轮：预热 $WARMUP"
        run_k6 "$name" warmup
        mysql_before=$(mysql_status)
        redis_before=$(redis_calls)
        step "$switch=$mode 第 $run/$runs 轮：稳态压测 $DURATION"
        run_k6 "$name" steady
        if [ "$scenario" = a ]; then
            # 落库完成后再采集，异步落库的写入也计入差值
            counter=$(wait_counter)
        fi
        # 采集结果先存入变量：命令替换直接作参数时，采集失败不会中止脚本
        mysql_after=$(mysql_status)
        redis_after=$(redis_calls)
        mysql_delta=$(delta "$mysql_before" "$mysql_after")
        redis_delta=$(delta "$redis_before" "$redis_after")
        result=$(jq -n --slurpfile k6 "$work/$name.summary.json" --argjson mysql "$mysql_delta" \
            --argjson redis "$redis_delta" '{k6: $k6[0], mysql: $mysql, redis: $redis}')
        if [ "$scenario" = a ]; then
            result=$(jq --argjson counter "$counter" '. + {counter: $counter}' <<<"$result")
        fi
        if [ "$mode" = push-pull ]; then
            step "测量推送耗时"
            run_k6 b-feed-push measure
            result=$(jq --slurpfile push "$work/b-feed-push.summary.json" '. + $push[0]' <<<"$result")
        fi
        jq -c . <<<"$result" >>"$runs_file"
        jq '{k6, innodbRowLockWaits: (.mysql.Innodb_row_lock_waits // 0), comSelect: (.mysql.Com_select // 0),
            redisCalls: ([.redis[]] | add // 0)}' <<<"$result"
    done
    mode_json=$(jq -s "$medians {median: medians, runs: .}" "$runs_file")
    modes_json=$(jq --arg mode "$mode" --argjson result "$mode_json" '. + {($mode): $result}' <<<"$modes_json")
done

output="code-nest-loadtest/results/$(date +%F)-$name.json"
jq -n --arg scenario "$name" --arg switch "$switch" --arg startedAt "$started_at" --arg warmup "$WARMUP" \
    --arg duration "$DURATION" --argjson runs "$runs" --argjson modes "$modes_json" \
    '{scenario: $scenario, switch: $switch, startedAt: $startedAt, warmup: $warmup, duration: $duration, runs: $runs,
      modes: $modes}' >"$output"
step "完成，结果写入 $output"
