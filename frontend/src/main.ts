import { createApp } from "vue";
import App from "./App.vue";
import setupPlugins from "@/plugins";

// unocss
import "uno.css";

// 过渡动画
import "animate.css";

// 全局样式（含 Element Plus 重置样式）
import "@/styles/index.scss";

// 自动为某些默认事件（如 touchstart、wheel 等）添加 { passive: true }，提升滚动性能并消除控制台的非被动事件监听警告
import "default-passive-events";

const app = createApp(App);
// 注册插件
app.use(setupPlugins);
app.mount("#app");
