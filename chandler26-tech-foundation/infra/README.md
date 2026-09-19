# 基础设施运行时状态与运维指南
> **数据根目录**: `/Users/chandler/docker-volumes` (严格持久化，容器/镜像/Pod 删除升级绝不丢数据)

---

## 一、 已就绪的基础设施核心组件

| 组件 | 运行方式 | 访问入口 | 默认账号/初始凭据 | 数据持久化宿主路径 | 核心职能 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **GitLab CE** | Docker 容器 (`gitlab`) | [http://localhost:8090](http://localhost:8090) | 账号: `root`<br>密码见初始密码文件 | `/Users/chandler/docker-volumes/gitlab` | 代码托管、分支合并 (MR) 与 Webhook 触发中心 |
| **Jenkins LTS** | Docker 容器 (`jenkins`) | [http://localhost:8080](http://localhost:8080) | 初始密码见下方指令 | `/Users/chandler/docker-volumes/jenkins/jenkins_home` | CI/CD 流水线构建引擎 (已挂载 docker.sock) |
| **Nacos 3.2.4** | K8s Pod (`infra` 命名空间) | [http://localhost:8849](http://localhost:8849) | 免密直通 / Next UI | `/Users/chandler/docker-volumes/nacos/data` | 微服务注册中心与动态配置中心 (OpenAPI: 8848, gRPC: 9848) |
| **Ingress-Nginx**| K8s 控制器 (`ingress-nginx`) | [http://localhost:80](http://localhost:80) | 无需认证 | K8s 内部管控 | 南北向流量网关、SSL 卸载与金丝雀权重分流 |
| **Argo Rollouts**| K8s 控制器 (`argo-rollouts`)| 集群 CRD 控制器 | 无需认证 | K8s 内部管控 | 云原生金丝雀灰度发版、指标门禁分析与自动回滚 |

---

## 二、 常用管理与运维指令

### 1. 查看组件运行状态
```bash
# 查看 K8s 基础设施 Pod 状态 (Nacos, Ingress, Argo Rollouts)
kubectl get pods -n infra
kubectl get pods -n ingress-nginx
kubectl get pods -n argo-rollouts

# 查看 Jenkins / GitLab 容器状态
docker ps -f name=jenkins
docker ps -f name=gitlab
```

### 2. Jenkins 初始管理员密码读取
```bash
cat /Users/chandler/docker-volumes/jenkins/jenkins_home/secrets/initialAdminPassword
# 当前初始密码: 145cff33f5364f8fb26948e47e3a4092
```

### 3. GitLab 初始 root 密码读取
```bash
cat /Users/chandler/docker-volumes/gitlab/config/initial_root_password
# 账号: root
# 当前密码: kvJk9klEYX6NobOnybEfjz7RnDfUtFT7gz7CPZf6hkc=
# (首次登录后建议在 Profile -> Password 中修改)
```

### 4. Nacos 本地通信端口转发 (若重启电脑后需要重新监听)
```bash
kubectl port-forward -n infra svc/nacos-service 8848:8848 8849:8849 9848:9848 &
```

### 5. 容器启动与升级模板（严格持久化，数据绝不丢失）
```bash
# GitLab CE (端口映射 8090/8443/2222，配置与日志宿主挂载，数据 volume 挂载)
docker run -d \
  --name gitlab \
  --hostname gitlab.local \
  -p 8090:8090 \
  -p 8443:443 \
  -p 2222:22 \
  --restart always \
  -v /Users/chandler/docker-volumes/gitlab/config:/etc/gitlab \
  -v /Users/chandler/docker-volumes/gitlab/logs:/var/log/gitlab \
  -v gitlab_data:/var/opt/gitlab \
  --shm-size 256m \
  -e GITLAB_OMNIBUS_CONFIG="
    external_url 'http://localhost:8090';
    gitlab_rails['gitlab_shell_ssh_port'] = 2222;
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

# Jenkins (JDK 21 LTS, 已挂载 docker.sock)
docker run -d \
  --name jenkins \
  -p 8080:8080 -p 50000:50000 \
  --restart always \
  -v /Users/chandler/docker-volumes/jenkins/jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -u root \
  jenkins/jenkins:lts-jdk21
```
