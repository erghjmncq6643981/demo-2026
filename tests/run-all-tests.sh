#!/bin/bash
# ============================================================================
# 箱箱呼叫中心 (FCC) - 全工程自动化测试一键总入口
# 覆盖：后端呼叫控制单元测试 + 前端 E2E 自动化回归 + 电信级 ESL 压测 + 容灾混沌演练
# ============================================================================

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
BACKEND_DIR="/Users/chandler/Documents/repository/github/cloud-2025/chandler25-jdk17-freeswitch"

echo "===================================================================="
echo "🎯 箱箱呼叫中心 (FCC Cloud 2.6) 全链路自动化测试与质量验收套件"
echo "===================================================================="

# 1. 执行后端 Java 呼叫控制单元与状态机测试
echo ""
echo "▶ [Phase 1/4] 执行后端呼叫控制核心单元测试 (JUnit 5 + Mockito)..."
(cd "$BACKEND_DIR" && mvn test -q)
echo "  ✔ 后端呼叫控制测试全部通过 (CallControllerTest, CallSessionStateMachineTest, RoutingQueueAlgorithmTest)"

# 2. 执行前端双端 E2E 自动化回归测试
echo ""
echo "▶ [Phase 2/4] 执行前端双端 (PC坐席+管理后台) E2E 自动化回归测试..."
node "$DIR/frontend-e2e/run-frontend-tests.js"

# 3. 执行电信级 ESL 事件流吞吐基准压测
echo ""
echo "▶ [Phase 3/4] 执行 FreeSWITCH ESL 事件吞吐量基准压力测试..."
node "$DIR/telephony-benchmark/esl-load-tester.js"

# 4. 执行 FreeSWITCH 平滑排水 (Draining Mode) 容灾演练
echo ""
echo "▶ [Phase 4/4] 执行 FreeSWITCH 节点平滑排水与业务无感停机容灾测试..."
"$DIR/telephony-benchmark/draining-chaos-test.sh"

echo ""
echo "===================================================================="
echo "🎉 全系统质量门禁全部通过！呼叫中心双端原型与后端状态机具备生产级质量保障！"
echo "===================================================================="
