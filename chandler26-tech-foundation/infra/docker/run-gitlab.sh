#!/bin/bash
# ==============================================================================
# GitLab CE 本地极简内存调优与持久化启动脚本
# 持久化目录: /Users/chandler/docker-volumes/gitlab/{config,logs,data}
# 端口映射: 
#   HTTP:  8090 (http://localhost:8090)
#   SSH:   2222 (ssh://git@localhost:2222)
#   HTTPS: 8443
# ==============================================================================

set -e

BASE_DIR="/Users/chandler/docker-volumes/gitlab"
mkdir -p "${BASE_DIR}/config" "${BASE_DIR}/logs" "${BASE_DIR}/data"
chmod -R 777 "${BASE_DIR}"

# 检查是否已有旧容器
if docker ps -a --format '{{.Names}}' | grep -Eq '^gitlab$'; then
    echo "发现已存在的 gitlab 容器，正在停止并移除容器（持久化数据保留）..."
    docker stop gitlab && docker rm gitlab
fi

echo "正在启动 GitLab 容器 (已开启轻量化内存调优)..."

docker run -d \
  --name gitlab \
  --hostname gitlab.local \
  -p 8090:80 \
  -p 8443:443 \
  -p 2222:22 \
  --restart always \
  -v "${BASE_DIR}/config:/etc/gitlab" \
  -v "${BASE_DIR}/logs:/var/log/gitlab" \
  -v gitlab_data:/var/opt/gitlab \
  --shm-size 256m \
  -e GITLAB_OMNIBUS_CONFIG="
    external_url 'http://localhost:8090';
    gitlab_rails['gitlab_shell_ssh_port'] = 2222;
    # 内存深度调优：关闭无用的监控组件，节约 2.5G 内存
    prometheus['enable'] = false;
    prometheus_monitoring['enable'] = false;
    alertmanager['enable'] = false;
    node_exporter['enable'] = false;
    redis_exporter['enable'] = false;
    postgres_exporter['enable'] = false;
    gitlab_exporter['enable'] = false;
    puma['worker_processes'] = 2;
    puma['min_threads'] = 1;
    puma['max_threads'] = 4;
    sidekiq['max_concurrency'] = 5;
    postgresql['shared_buffers'] = '256MB';
  " \
  gitlab/gitlab-ce:latest

echo "===================================================================="
echo "GitLab 容器启动指令已下发！"
echo "由于 GitLab 初始化包含数据库迁移，首次启动通常需要 2~3 分钟。"
echo "可以使用以下命令查看启动日志："
echo "  docker logs -f gitlab"
echo ""
echo "访问入口: http://localhost:8090"
echo "初始 root 密码查看命令 (启动完成生成后):"
echo "  cat ${BASE_DIR}/config/initial_root_password"
echo "===================================================================="
