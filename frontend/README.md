# 前端项目文档

## 项目启动

- **环境准备**

  | 环境类型     | 名称                                                                  |
  | ------------ | --------------------------------------------------------------------- |
  | **开发工具** | [Visual Studio Code](https://code.visualstudio.com/Download)          |
  | **运行环境** | Node 18 + (推荐[22.9.0](https://npmmirror.com/mirrors/node/v22.9.0/)) |
  > ⚠️ 注意：Node.js 20.6.0 版本存在兼容性问题，请勿使用

- **快速开始**

  ```bash
  # 克隆代码
  git clone <repo_url>

  # 切换目录
  cd frontend

  # 安装 pnpm
  npm install pnpm -g

  # 设置镜像源（可选）
  pnpm config set registry https://registry.npmmirror.com

  # 安装依赖
  pnpm install

  # 启动运行
  pnpm run dev
  ```

## 项目部署

执行 `pnpm run build` 命令后，项目将被打包并生成 `dist` 目录。接下来，将 `dist` 目录下的文件上传到服务器 `/usr/share/nginx/html` 目录下，并配置 Nginx 进行反向代理。

```bash
pnpm run build
```

以下是 Nginx 的配置示例：

```nginx
server {
    listen      80;
    server_name localhost;

    location / {
        root   /usr/share/nginx/html;
        index  index.html index.htm;
    }

    # 反向代理配置
    location /prod-api/ {
        # 请将 api.demo.com 替换为您的后端 API 地址，并注意保留后面的斜杠 /
        proxy_pass http://api.demo.com/;
    }
}
```

更多详细信息，请参考这篇文章：[Nginx 安装和配置](https://blog.csdn.net/u013737132/article/details/145667694)。

## 代码提交规范

暂存更改后，在终端执行 `pnpm run commit` 命令唤起 git commit 交互，根据提示完成信息的输入和选择。

## 系统功能介绍

1. **文件管理**
   - 支持文件的上传、下载、删除和列表展示。
   - 文件信息包括文件名、大小、类型、上传时间等。
   - 文件操作界面友好，支持批量操作。

2. **访问计数器**
   - 展示系统被访问的总次数。
   - 用户可主动点击按钮增加访问次数，实时查看计数变化。

3. **页面与导航**
   - 顶部导航栏可切换“文件管理”、“访问计数”等主要功能页面。
   - 提供 404 错误页面，访问不存在的路由时自动跳转。

4. **权限与路由守卫**
   - 未登录用户访问受限页面会自动跳转到登录页。
   - 路由守卫实现页面访问控制和页面标题动态设置。
