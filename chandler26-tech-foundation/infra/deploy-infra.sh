#!/bin/bash
# ==============================================================================
# 新一代技术基座基础设施一键部署脚本 (Mac Docker Desktop K8s)
# 包含：
# 1. 宿主机持久化目录准备 (绝对数据防丢失)
# 2. K8s 命名空间 (infra, devops, prod)
# 3. Nacos 2.4.3 持久化部署 (注册与配置中心)
# 4. Ingress-Nginx 控制器 (南北向流量网关)
# 5. Argo Rollouts 控制器 (云原生金丝雀灰度发布引擎)
# 6. Jenkins LTS 持久化部署 (CI/CD 引擎)
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_VOLUME_DIR="/Users/chandler/docker-volumes"

echo "===================================================================="
echo "🚀 [1/5] 初始化宿主机数据持久化目录..."
echo "===================================================================="
mkdir -p "${BASE_VOLUME_DIR}/nacos/data"
mkdir -p "${BASE_VOLUME_DIR}/jenkins/jenkins_home"
mkdir -p "${BASE_VOLUME_DIR}/gitlab"
chmod -R 777 "${BASE_VOLUME_DIR}"
echo "✅ 宿主机目录准备完毕: ${BASE_VOLUME_DIR}"

echo ""
echo "===================================================================="
echo "📦 [2/5] 创建 Kubernetes 命名空间 (infra, devops, prod)..."
echo "===================================================================="
kubectl apply -f "${SCRIPT_DIR}/k8s/00-namespaces.yaml"

echo ""
echo "===================================================================="
echo "🌐 [3/5] 部署 Nacos 2.4.3 (注册与配置中心，持久化挂载)..."
echo "===================================================================="
kubectl apply -f "${SCRIPT_DIR}/k8s/01-nacos.yaml"

echo ""
echo "===================================================================="
echo "🚦 [4/5] 检查/安装 Ingress-Nginx 与 Argo Rollouts 控制器..."
echo "===================================================================="

# 安装 Ingress-Nginx (如果未安装)
if ! kubectl get ns ingress-nginx >/dev/null 2>&1; then
    echo "正在安装 Ingress-Nginx 控制器..."
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.2/deploy/static/provider/cloud/deploy.yaml
else
    echo "Ingress-Nginx 控制器已存在，跳过安装。"
fi

# 安装 Argo Rollouts (如果未安装)
if ! kubectl get ns argo-rollouts >/dev/null 2>&1; then
    echo "正在安装 Argo Rollouts 控制器..."
    kubectl create namespace argo-rollouts
    kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/download/v1.7.2/install.yaml
else
    echo "Argo Rollouts 控制器已存在，跳过安装。"
fi

echo ""
echo "===================================================================="
echo "🏗️ [5/5] 部署 Jenkins LTS (CI/CD 引擎，持久化挂载)..."
echo "===================================================================="
kubectl apply -f "${SCRIPT_DIR}/k8s/02-jenkins.yaml"

echo ""
echo "===================================================================="
echo "🎉 基础设施组件部署清单已全部提交！"
echo "===================================================================="
echo "可以通过以下命令查看启动状态："
echo "  kubectl get pods -n infra"
echo "  kubectl get pods -n devops"
echo "  kubectl get pods -n argo-rollouts"
echo "  kubectl get pods -n ingress-nginx"
echo ""
echo "访问入口信息："
echo "  • Nacos 控制台:    http://localhost:30848/nacos (默认账号 nacos / nacos)"
echo "  • Jenkins 控制台:  http://localhost:30080"
echo "  • 数据存储宿主路径: ${BASE_VOLUME_DIR}"
echo "===================================================================="
