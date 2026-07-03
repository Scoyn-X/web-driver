# Docker Compose 安装中间件 MySQL、Redis、Minio

## 安装

```bash
docker-compose -f ./docker-compose.yml -p jiayuan-boot up -d
```

- `-p jiayuan-boot` 指定命名空间，避免与其他容器冲突，同时也便于统一管理和卸载

## 卸载

```bash
docker-compose -f ./docker-compose.yml -p jiayuan-boot down
```
