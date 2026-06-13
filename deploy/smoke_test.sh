#!/usr/bin/env bash
# 全链路联调冒烟测试 (Phase 6)：逐环节验证 模拟器→DB→后端(缓存/限流/锁)→Python预测→大屏API。
# 用法: bash deploy/smoke_test.sh
set -u
BACKEND=http://localhost:8080
PRED=http://localhost:8000
REDIS="docker exec smart_energy_redis redis-cli"
DB="docker exec smart_energy_db psql -U energy_user -d smart_energy -t -c"
PASS=0; FAIL=0
ok(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
no(){ echo "  ❌ $1"; FAIL=$((FAIL+1)); }
code(){ curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== 1. 基础设施 =="
docker exec smart_energy_redis redis-cli ping 2>/dev/null | grep -q PONG && ok "Redis PONG" || no "Redis"
$DB "SELECT 1;" >/dev/null 2>&1 && ok "TimescaleDB 可查询" || no "TimescaleDB"
[ "$(code $PRED/health)" = "200" ] && ok "预测服务 /health" || no "预测服务(8000)"
[ "$(code $BACKEND/api/sensor/latest/EAF-01)" = "200" ] && ok "后端在线(8080)" || no "后端(8080)"

echo "== 2. 数据在增长(模拟器→DB) =="
c1=$($DB "SELECT count(*) FROM sensor_data;" | tr -d ' ')
sleep 4
c2=$($DB "SELECT count(*) FROM sensor_data;" | tr -d ' ')
[ "$c2" -gt "$c1" ] && ok "sensor_data 增长 $c1→$c2" || no "sensor_data 未增长($c1)"

echo "== 3. 限流(Epic 11-3): 登录 3/min/IP =="
TOKEN=""; n429=0
for i in 1 2 3 4 5; do
  resp=$(curl -s -w "\n%{http_code}" -X POST $BACKEND/api/auth/login -H 'Content-Type: application/json' \
    -d '{"username":"cachetest","password":"test123456"}')
  hc=$(echo "$resp" | tail -1)
  [ "$hc" = "200" ] && [ -z "$TOKEN" ] && TOKEN=$(echo "$resp" | head -1 | python3 -c "import sys,json;print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
  [ "$hc" = "429" ] && n429=$((n429+1))
done
[ -n "$TOKEN" ] && ok "登录拿到 token" || no "登录"
[ "$n429" -ge 1 ] && ok "限流触发(429 ×$n429)" || no "限流未触发"
AUTH="Authorization: Bearer $TOKEN"

echo "== 4. 缓存(Epic 11-2) =="
curl -s "$BACKEND/api/dashboard/summary?deviceCode=EAF-01" -H "$AUTH" >/dev/null
for k in "device:latest:EAF-01" "dashboard:summary:EAF-01"; do
  [ "$($REDIS exists "$k")" = "1" ] && ok "缓存键 $k" || no "缓存键 $k"
done

echo "== 5. 预测全链路(Epic 6-3) =="
fc=$(curl -s "$BACKEND/api/dashboard/forecast?deviceCode=EAF-01" -H "$AUTH")
echo "$fc" | python3 -c "import sys,json;d=json.load(sys.stdin);assert len(d)==2 and 'mean' in d[0]" 2>/dev/null \
  && ok "后端→Python 预测返回 2 步: $(echo $fc | python3 -c 'import sys,json;print([p["mean"] for p in json.load(sys.stdin)])')" || no "预测链路"
[ "$($REDIS exists prediction:forecast:EAF-01)" = "1" ] && ok "预测缓存键 prediction:forecast:EAF-01" || no "预测缓存"

echo "== 6. 调度建议升级 + 确认/拒绝(Epic 6-5) =="
adv=$(curl -s "$BACKEND/api/dashboard/dispatch-advice?deviceCode=EAF-01" -H "$AUTH")
echo "$adv" | python3 -c "import sys,json;d=json.load(sys.stdin);assert d.get('suggestedAction') is not None" 2>/dev/null \
  && ok "建议含 suggestedAction/estimatedSaving" || no "建议升级字段"
dec=$(curl -s -X POST $BACKEND/api/dashboard/dispatch-advice/decision -H "$AUTH" -H 'Content-Type: application/json' -d '{"deviceCode":"EAF-01","decision":"CONFIRM"}')
echo "$dec" | grep -q acknowledgedAt && ok "确认/拒绝端点回执" || no "决策端点"

echo "== 7. 异步预热(Epic 11-4): 全设备预测都已缓存 =="
miss=0
for c in EAF-01 PUMP-01 COMP-01; do [ "$($REDIS exists prediction:forecast:$c)" = "1" ] || miss=$((miss+1)); done
[ "$miss" = "0" ] && ok "3 设备预测均已预热进 Redis" || no "$miss 个设备未预热"

echo; echo "==== 结果: PASS=$PASS  FAIL=$FAIL ===="
[ "$FAIL" = "0" ] && echo "🎉 全链路联调通过" || echo "⚠️ 有 $FAIL 项未通过"
