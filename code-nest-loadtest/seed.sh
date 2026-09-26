#!/usr/bin/env bash
# 从空的压测环境开始：启动环境 → 造数 → 生成派生数据。用法见 README.md
set -euo pipefail
cd "$(dirname "$0")/.."

compose() {
    docker compose -f compose.yaml -f compose.loadtest.yaml "$@"
}

# 设置 MySQL 提交时是否等待 redo log 与 binlog 刷盘：1 为默认值，每次提交都刷；
# 传 0 时 redo log 每秒刷一次（innodb_flush_log_at_trx_commit=2），binlog 交给操作系统（sync_binlog=0）
sync_on_commit() {
    local redo=$([ "$1" = 1 ] && echo 1 || echo 2)
    compose exec -T mysql mysql -uroot -proot \
        -e "SET GLOBAL innodb_flush_log_at_trx_commit=$redo, GLOBAL sync_binlog=$1" 2>/dev/null
}

step() {
    echo "== $1（已用 ${SECONDS}s）"
}

# es 档启动：首次启动建好空的 ES 索引，之后由 search-rebuild 全量导入
export SEARCH_MODE=es

step "启动压测环境"
compose up -d --build --wait

# 造数与对账要逐行提交几十万次，期间不等刷盘；结束或中途失败都恢复默认值，压测时的 MySQL 配置不变
sync_on_commit 0
trap 'sync_on_commit 1' EXIT

step "造数"
./mvnw -q -pl code-nest-loadtest compile exec:java

step "清空 Redis 并重启应用：布隆过滤器与 Feed 发件箱在启动时重建"
compose exec -T redis redis-cli FLUSHALL
compose restart app-1 app-2
compose up -d --wait

step "计数对账"
curl -fsS -X POST http://localhost:8081/actuator/counter-reconcile
echo

sync_on_commit 1
trap - EXIT

step "重建搜索索引"
curl -fsS -X POST http://localhost:8081/actuator/search-rebuild
echo

step "完成"
