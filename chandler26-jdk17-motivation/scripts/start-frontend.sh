#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/service-common.sh"

pid_file="$(service_pid_file frontend)"
log_file="$(service_log_file frontend)"

if service_is_active frontend; then
  echo "frontend: 已在运行"
  exit 0
fi

nohup bash -lc "cd '${FRONTEND_DIR}' && exec node server.mjs" > "${log_file}" 2>&1 &
echo $! > "${pid_file}"

if ! service_wait_until_ready frontend 15; then
  service_cleanup_pid frontend
  echo "frontend: 启动失败，查看日志 ${log_file}" >&2
  exit 1
fi

echo "frontend: 已启动 (log: ${log_file})"
