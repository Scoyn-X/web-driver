import axios, { type AxiosRequestConfig, type AxiosResponse } from "axios";
import { ResultEnum } from "@/enums/api/result.enum";

/**
 * 后端响应的统一格式
 */
interface ApiResponse<T = any> {
  code: string;
  data: T;
  msg: string;
}

/**
 * 创建 Axios 实例
 */
const instance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 50000,
  headers: {
    "Content-Type": "application/json;charset=utf-8",
  },
});

/**
 * 请求拦截器：自动携带 token
 */
instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    const isNoAuthRequest = config.headers.Authorization === "no-auth";

    // FormData 请求时，移除默认 Content-Type 让浏览器自动设置
    if (config.data instanceof FormData) {
      delete config.headers["Content-Type"];
    }

    // 如果不是"免认证"请求且有 token，则添加认证头
    if (token && !isNoAuthRequest) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 移除"免认证"标记
    if (isNoAuthRequest) {
      delete config.headers.Authorization;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

/**
 * 响应拦截器：统一处理响应数据和错误
 */
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    // 文件下载（blob 响应）直接返回
    if (response.config.responseType === "blob") {
      return response.data;
    }

    const { code, data, msg } = response.data;

    // 请求成功
    if (code === ResultEnum.SUCCESS) {
      return data;
    }

    // 请求失败
    const errorMessage = msg || "请求失败，请稍后重试";
    ElMessage.error(errorMessage);
    return Promise.reject(new Error(errorMessage));
  },
  (error) => {
    // 401 未认证：清除登录状态并跳转登录页
    if (error.response?.status === 401) {
      localStorage.removeItem("token");
      localStorage.removeItem("userId");
      localStorage.removeItem("accountName");
      localStorage.removeItem("nickname");
      ElMessage.error("登录已过期，请重新登录");
      window.location.href = "/#/login";
      return Promise.reject(error);
    }

    // 网络错误或服务器错误
    const errorMessage =
      error.response?.data?.msg || error.message || "网络请求失败，请检查网络连接";

    ElMessage.error(errorMessage);
    return Promise.reject(error);
  }
);

/**
 * HTTP 请求工具函数
 *
 * @template T 响应数据类型
 * @param config Axios 请求配置
 * @returns Promise<T>
 *
 * @example
 * // GET 请求
 * request<FileInfo[]>({ url: '/files', method: 'get' })
 *
 * // POST 请求
 * request<FileInfo>({ url: '/files', method: 'post', data: formData })
 *
 * // DELETE 请求
 * request<void>({ url: '/files/1', method: 'delete' })
 */
function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return instance.request(config);
}

export default request;
