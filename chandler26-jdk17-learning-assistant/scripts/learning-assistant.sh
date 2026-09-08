#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FRONTEND_DIR="${LEARNING_FRONTEND_DIR:-${BACKEND_DIR}-web}"

BACKEND_HOST="${BACKEND_HOST:-127.0.0.1}"
BACKEND_PORT="${BACKEND_PORT:-16681}"
FRONTEND_HOST="${FRONTEND_HOST:-127.0.0.1}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

RUN_DIR="${LEARNING_RUN_DIR:-${BACKEND_DIR}/.run}"
LOG_DIR="${LEARNING_LOG_DIR:-${BACKEND_DIR}/logs/dev}"
BACKEND_PID_FILE="${RUN_DIR}/backend-${BACKEND_PORT}.pid"
FRONTEND_PID_FILE="${RUN_DIR}/frontend-${FRONTEND_PORT}.pid"
BACKEND_LOG="${LOG_DIR}/backend-console.log"
FRONTEND_LOG="${LOG_DIR}/frontend-console.log"

mkdir -p "${RUN_DIR}" "${LOG_DIR}"

usage() {
  cat <<EOF
Usage:
  scripts/learning-assistant.sh start [all|backend|frontend]
  scripts/learning-assistant.sh stop [all|backend|frontend]
  scripts/learning-assistant.sh restart [all|backend|frontend]
  scripts/learning-assistant.sh status
  scripts/learning-assistant.sh logs [backend|frontend|all]

Environment:
  BACKEND_PORT=16681
  FRONTEND_PORT=5173
  FRONTEND_HOST=127.0.0.1
  MVN_BIN=/path/to/mvn
  NODE_BIN=/path/to/node
  LEARNING_FRONTEND_DIR=${FRONTEND_DIR}

URLs:
  Backend:  http://${BACKEND_HOST}:${BACKEND_PORT}
  Frontend: http://${FRONTEND_HOST}:${FRONTEND_PORT}
EOF
}

info() {
  printf '[learning-assistant] %s\n' "$*"
}

warn() {
  printf '[learning-assistant] WARN: %s\n' "$*" >&2
}

die() {
  printf '[learning-assistant] ERROR: %s\n' "$*" >&2
  exit 1
}

find_maven() {
  if [[ -n "${MVN_BIN:-}" && -x "${MVN_BIN}" ]]; then
    printf '%s\n' "${MVN_BIN}"
    return
  fi
  if [[ -x "${BACKEND_DIR}/mvnw" ]]; then
    printf '%s\n' "${BACKEND_DIR}/mvnw"
    return
  fi
  local candidate
  for candidate in "$(command -v mvn 2>/dev/null || true)" /opt/apache-maven-3.8.3/bin/mvn /opt/homebrew/bin/mvn /usr/local/bin/mvn; do
    if [[ -n "${candidate}" && -x "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return
    fi
  done
}

find_node() {
  if [[ -n "${NODE_BIN:-}" && -x "${NODE_BIN}" ]]; then
    printf '%s\n' "${NODE_BIN}"
    return
  fi
  local candidate
  for candidate in "$(command -v node 2>/dev/null || true)" /opt/homebrew/bin/node /usr/local/bin/node; do
    if [[ -n "${candidate}" && -x "${candidate}" ]]; then
      printf '%s\n' "${candidate}"
      return
    fi
  done
  if [[ -d "${HOME}/.nvm/versions/node" ]]; then
    local nvm_node
    nvm_node="$(find "${HOME}/.nvm/versions/node" -maxdepth 3 -name node -perm +111 2>/dev/null | sort -V | tail -n 1 || true)"
    if [[ -n "${nvm_node}" && -x "${nvm_node}" ]]; then
      printf '%s\n' "${nvm_node}"
      return
    fi
  fi
}

pid_alive() {
  local pid_file="$1"
  [[ -f "${pid_file}" ]] || return 1
  local pid
  pid="$(cat "${pid_file}" 2>/dev/null || true)"
  [[ -n "${pid}" ]] || return 1
  kill -0 "${pid}" >/dev/null 2>&1
}

pid_value() {
  local pid_file="$1"
  [[ -f "${pid_file}" ]] && cat "${pid_file}" 2>/dev/null || true
}

port_listening() {
  local port="$1"
  if command -v nc >/dev/null 2>&1; then
    nc -z -G 1 127.0.0.1 "${port}" >/dev/null 2>&1
    return
  fi
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
    return
  fi
  return 1
}

port_pids() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true
  fi
}

wait_for_port() {
  local name="$1"
  local port="$2"
  local seconds="$3"
  local pid_file="${4:-}"
  local log_file="${5:-}"
  local index=0

  info "Waiting for ${name} to listen on port ${port} (up to ${seconds}s)..."
  while (( index < seconds )); do
    if port_listening "${port}"; then
      info "${name} is listening on port ${port} (ready in ${index}s)"
      return 0
    fi
    if [[ -n "${pid_file}" ]] && ! pid_alive "${pid_file}"; then
      warn "${name} process exited prematurely before port ${port} was open!"
      if [[ -n "${log_file}" && -f "${log_file}" ]]; then
        warn "Last lines from ${log_file}:"
        tail -n 10 "${log_file}" >&2 || true
      fi
      return 1
    fi
    sleep 1
    index=$((index + 1))
    if (( index % 5 == 0 )); then
      info "Still waiting for ${name} on port ${port}... (${index}s/${seconds}s)"
    fi
  done
  warn "${name} did not start listening on port ${port} within ${seconds}s"
  return 1
}

start_backend() {
  if pid_alive "${BACKEND_PID_FILE}"; then
    info "Backend is already running, pid $(pid_value "${BACKEND_PID_FILE}")"
    return
  fi
  rm -f "${BACKEND_PID_FILE}"
  if port_listening "${BACKEND_PORT}"; then
    warn "Backend port ${BACKEND_PORT} is already in use by pid(s): $(port_pids "${BACKEND_PORT}" | tr '\n' ' ')"
    warn "Skip backend start. Use stop only for processes started by this script."
    return
  fi
  local mvn_bin
  mvn_bin="$(find_maven)"
  [[ -n "${mvn_bin}" ]] || die "Maven not found. Install Maven or set MVN_BIN=/path/to/mvn"
  info "Starting backend with ${mvn_bin}"
  : > "${BACKEND_LOG}"
  (
    cd "${BACKEND_DIR}"
    nohup "${mvn_bin}" spring-boot:run </dev/null >> "${BACKEND_LOG}" 2>&1 &
    local pid="$!"
    echo "${pid}" > "${BACKEND_PID_FILE}"
    disown "${pid}" >/dev/null 2>&1 || true
  )
  info "Backend pid $(pid_value "${BACKEND_PID_FILE}"), log ${BACKEND_LOG}"
  wait_for_port "Backend" "${BACKEND_PORT}" 90 "${BACKEND_PID_FILE}" "${BACKEND_LOG}" || true
}

start_frontend() {
  [[ -d "${FRONTEND_DIR}" ]] || die "Frontend directory not found: ${FRONTEND_DIR}"
  if pid_alive "${FRONTEND_PID_FILE}"; then
    info "Frontend is already running, pid $(pid_value "${FRONTEND_PID_FILE}")"
    return
  fi
  rm -f "${FRONTEND_PID_FILE}"
  if port_listening "${FRONTEND_PORT}"; then
    warn "Frontend port ${FRONTEND_PORT} is already in use by pid(s): $(port_pids "${FRONTEND_PORT}" | tr '\n' ' ')"
    warn "Skip frontend start. Use stop only for processes started by this script."
    return
  fi
  local node_bin
  node_bin="$(find_node)"
  [[ -n "${node_bin}" ]] || die "Node.js not found. Install Node.js or set NODE_BIN=/path/to/node"
  info "Starting frontend with ${node_bin}"
  : > "${FRONTEND_LOG}"
  (
    cd "${FRONTEND_DIR}"
    HOST="${FRONTEND_HOST}" PORT="${FRONTEND_PORT}" nohup "${node_bin}" server.mjs </dev/null >> "${FRONTEND_LOG}" 2>&1 &
    local pid="$!"
    echo "${pid}" > "${FRONTEND_PID_FILE}"
    disown "${pid}" >/dev/null 2>&1 || true
  )
  info "Frontend pid $(pid_value "${FRONTEND_PID_FILE}"), log ${FRONTEND_LOG}"
  wait_for_port "Frontend" "${FRONTEND_PORT}" 20 "${FRONTEND_PID_FILE}" "${FRONTEND_LOG}" || true
}

terminate_tree() {
  local pid="$1"
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" >/dev/null 2>&1; then
    return 0
  fi
  pkill -TERM -P "${pid}" >/dev/null 2>&1 || true
  kill -TERM "${pid}" >/dev/null 2>&1 || true
  local index=0
  while (( index < 20 )); do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    index=$((index + 1))
  done
  pkill -KILL -P "${pid}" >/dev/null 2>&1 || true
  kill -KILL "${pid}" >/dev/null 2>&1 || true
}

wait_for_port_free() {
  local name="$1"
  local port="$2"
  local seconds="${3:-10}"
  local index=0
  while (( index < seconds )); do
    if ! port_listening "${port}"; then
      return 0
    fi
    sleep 1
    index=$((index + 1))
  done
  return 1
}

stop_process() {
  local name="$1"
  local pid_file="$2"
  local port="${3:-}"
  local stopped=0

  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(pid_value "${pid_file}")"
    if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
      info "Stopping ${name}, pid ${pid}"
      terminate_tree "${pid}"
      stopped=1
    else
      info "${name} pid file is stale; removing ${pid_file}"
    fi
    rm -f "${pid_file}"
  fi

  if [[ -n "${port}" ]] && port_listening "${port}"; then
    local pids
    pids="$(port_pids "${port}" | tr '\n' ' ')"
    if [[ -n "${pids// }" ]]; then
      info "Releasing ${name} port ${port} occupied by pid(s): ${pids}"
      for p in ${pids}; do
        terminate_tree "${p}"
      done
      stopped=1
    fi
  fi

  if [[ -n "${port}" ]]; then
    wait_for_port_free "${name}" "${port}" 10 || warn "${name} port ${port} still in use after stop"
  fi

  if (( stopped == 0 )) && ! [[ -f "${pid_file}" ]]; then
    info "${name} is not running; nothing to stop"
  fi
}

status_line() {
  local name="$1"
  local pid_file="$2"
  local port="$3"
  if pid_alive "${pid_file}"; then
    info "${name}: running, pid $(pid_value "${pid_file}"), port ${port}"
  elif port_listening "${port}"; then
    info "${name}: port ${port} is in use by pid(s): $(port_pids "${port}" | tr '\n' ' ')"
  else
    info "${name}: stopped, port ${port} is free"
  fi
}

start_service() {
  case "${1:-all}" in
    all) start_frontend; start_backend ;;
    backend) start_backend ;;
    frontend) start_frontend ;;
    *) die "Unknown service: $1" ;;
  esac
}

stop_service() {
  case "${1:-all}" in
    all) stop_process "Frontend" "${FRONTEND_PID_FILE}" "${FRONTEND_PORT}"; stop_process "Backend" "${BACKEND_PID_FILE}" "${BACKEND_PORT}" ;;
    backend) stop_process "Backend" "${BACKEND_PID_FILE}" "${BACKEND_PORT}" ;;
    frontend) stop_process "Frontend" "${FRONTEND_PID_FILE}" "${FRONTEND_PORT}" ;;
    *) die "Unknown service: $1" ;;
  esac
}

show_logs() {
  case "${1:-all}" in
    backend) touch "${BACKEND_LOG}"; tail -f "${BACKEND_LOG}" ;;
    frontend) touch "${FRONTEND_LOG}"; tail -f "${FRONTEND_LOG}" ;;
    all) touch "${BACKEND_LOG}" "${FRONTEND_LOG}"; tail -f "${BACKEND_LOG}" "${FRONTEND_LOG}" ;;
    *) die "Unknown log target: $1" ;;
  esac
}

command_name="${1:-help}"
service_name="${2:-all}"

case "${command_name}" in
  start)
    start_service "${service_name}"
    info "Frontend URL: http://${FRONTEND_HOST}:${FRONTEND_PORT}"
    info "Backend URL:  http://${BACKEND_HOST}:${BACKEND_PORT}"
    ;;
  stop)
    stop_service "${service_name}"
    ;;
  restart)
    stop_service "${service_name}"
    start_service "${service_name}"
    ;;
  status)
    status_line "Backend" "${BACKEND_PID_FILE}" "${BACKEND_PORT}"
    status_line "Frontend" "${FRONTEND_PID_FILE}" "${FRONTEND_PORT}"
    ;;
  logs)
    show_logs "${service_name}"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage
    die "Unknown command: ${command_name}"
    ;;
esac
