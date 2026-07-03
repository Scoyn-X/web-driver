import NProgress from "nprogress";
import "nprogress/nprogress.css";

/**
 * NProgress 进度条配置
 */
NProgress.configure({
  // 动画方式
  easing: "ease",
  // 递增进度条的速度
  speed: 200,
  // 是否显示加载 ico
  showSpinner: false,
  // 自动递增间隔
  trickleSpeed: 200,
  // 初始化时的最小百分比
  minimum: 0.2,
});

export default NProgress;
