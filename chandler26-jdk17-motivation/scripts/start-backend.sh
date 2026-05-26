#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/service-common.sh"

pid_file="$(service_pid_file backend)"
log_file="$(service_log_file backend)"

if service_is_active backend; then
  echo "backend: 已在运行"
  exit 0
fi

if [[ -x "${BACKEND_DIR}/mvnw" ]]; then
  nohup bash -lc "cd '${BACKEND_DIR}' && exec ./mvnw -q -DskipTests spring-boot:run" > "${log_file}" 2>&1 &
else
  nohup bash -lc "cd '${BACKEND_DIR}' && exec mvn -q -DskipTests spring-boot:run" > "${log_file}" 2>&1 &
fi
echo $! > "${pid_file}"

sleep 1
if ! service_is_active backend; then
  echo "backend: 启动失败，查看日志 ${log_file}" >&2
  tail -n 20 "${log_file}" >&2 || true
  exit 1
fi

echo "backend: 已启动 (log: ${log_file})"
