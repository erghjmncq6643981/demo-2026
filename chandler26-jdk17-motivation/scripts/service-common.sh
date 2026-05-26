#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FRONTEND_DIR="$(cd "${BACKEND_DIR}/../chandler26-jdk17-motivation-web" && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.runtime"

mkdir -p "${RUNTIME_DIR}"

service_pid_file() {
  local service_name="$1"
  printf '%s/%s.pid' "${RUNTIME_DIR}" "${service_name}"
}

service_log_file() {
  local service_name="$1"
  printf '%s/%s.log' "${RUNTIME_DIR}" "${service_name}"
}

service_pid() {
  local service_name="$1"
  local pid_file
  pid_file="$(service_pid_file "${service_name}")"
  if [[ -f "${pid_file}" ]]; then
    tr -d '[:space:]' < "${pid_file}"
  fi
}

service_is_running() {
  local pid="${1:-}"
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" 2>/dev/null; then
    return 1
  fi
  local state
  state="$(ps -o state= -p "${pid}" 2>/dev/null | tr -d '[:space:]')"
  [[ -z "${state}" || "${state}" != Z* ]]
}

service_port() {
  local service_name="$1"
  case "${service_name}" in
    backend) printf '%s' '17680' ;;
    frontend) printf '%s' '5174' ;;
    *) return 1 ;;
  esac
}

service_listening_pids() {
  local port
  port="$(service_port "$1")"
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"${port}" 2>/dev/null || true
  fi
}

service_is_active() {
  local service_name="$1"
  local pid
  pid="$(service_pid "${service_name}")"
  if [[ -n "$(service_listening_pids "${service_name}")" ]]; then
    return 0
  fi
  if service_is_running "${pid}"; then
    return 0
  fi
  return 1
}

service_is_ready() {
  [[ -n "$(service_listening_pids "$1")" ]]
}

service_refresh_pid_from_port() {
  local service_name="$1"
  local port_pids pid_file
  port_pids="$(service_listening_pids "${service_name}")"
  if [[ -n "${port_pids}" ]]; then
    pid_file="$(service_pid_file "${service_name}")"
    printf '%s\n' "${port_pids%%$'\n'*}" > "${pid_file}"
  fi
}

service_wait_until_ready() {
  local service_name="$1"
  local timeout_seconds="${2:-30}"
  local log_file
  log_file="$(service_log_file "${service_name}")"

  for ((i = 0; i < timeout_seconds; i++)); do
    if service_is_ready "${service_name}"; then
      service_refresh_pid_from_port "${service_name}"
      return 0
    fi
    sleep 1
  done

  echo "${service_name}: ${timeout_seconds}s 内没有监听端口 $(service_port "${service_name}")" >&2
  tail -n 40 "${log_file}" >&2 || true
  return 1
}

service_cleanup_pid() {
  local service_name="$1"
  rm -f "$(service_pid_file "${service_name}")"
}

service_wait_until_inactive() {
  local service_name="$1"
  local timeout_seconds="${2:-30}"

  for ((i = 0; i < timeout_seconds; i++)); do
    if ! service_is_active "${service_name}"; then
      service_cleanup_pid "${service_name}"
      return 0
    fi
    sleep 1
  done

  return 1
}

service_kill_all_processes() {
  local service_name="$1"
  local pids
  pids="$(
    {
      service_pid "${service_name}"
      service_listening_pids "${service_name}"
    } | sort -u
  )"

  while IFS= read -r pid; do
    [[ -n "${pid}" ]] || continue
    kill -9 "${pid}" 2>/dev/null || true
  done <<< "${pids}"
}

service_stop() {
  local service_name="$1"
  local pid
  pid="$(service_pid "${service_name}")"

  if ! service_is_running "${pid}"; then
    local port_pids
    port_pids="$(service_listening_pids "${service_name}")"
    if [[ -z "${port_pids}" ]]; then
      service_cleanup_pid "${service_name}"
      echo "${service_name}: 未运行"
      return 0
    fi
    pid="${port_pids%%$'\n'*}"
  fi

  kill "${pid}" 2>/dev/null || true
  if service_wait_until_inactive "${service_name}" 30; then
    echo "${service_name}: 已停止"
    return 0
  fi

  service_kill_all_processes "${service_name}"
  if service_wait_until_inactive "${service_name}" 10; then
    echo "${service_name}: 已强制停止"
    return 0
  fi

  echo "${service_name}: 停止超时，仍未完全退出" >&2
  return 1
}
