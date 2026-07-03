/** Invitations 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const INVITATIONS_BASE_URL = `${API_BASE}/invitations`;

// ==================== 数据模型 ====================

/** 流程图表响应 */
export interface ProcessDiagramResponseVO {
  /** BPMN 2.0 流程图 XML */
  bpmnXml?: string;
  /** 当前活跃节点ID列表 */
  activeNodeIds?: string[];
  /** 已完成节点ID列表 */
  completedNodeIds?: string[];
  /** 流程实例ID */
  processInstanceId?: string;
  /** 流程是否已结束 */
  ended?: boolean;
  /** 各节点详细信息 */
  nodes?: ProcessNodeInfoResponseVO[];
}

/** 流程节点信息 */
export interface ProcessNodeInfoResponseVO {
  /** 节点ID */
  nodeId?: string;
  /** 节点名称 */
  nodeName?: string;
  /** 节点类型 */
  nodeType?: string;
  /** 操作人（assignee） */
  assignee?: string;
  /** 操作人用户ID */
  userId?: number;
  /** 操作人用户名 */
  username?: string;
  /** 操作人账户ID */
  accountId?: number;
  /** 操作人账户名 */
  accountName?: string;
  /** 节点开始时间 */
  startTime?: string;
  /** 节点结束时间 */
  endTime?: string;
  /** 节点经历时长（毫秒） */
  durationMillis?: number;
  /** 节点是否已完成 */
  completed?: boolean;
}

/** invitations 模块接口集合 */
export const InvitationsAPI = {
  /**
   * 获取邀请流程图表
   *
   * `GET /api/v1/invitations/{id}/process-diagram`
   * @param id 路径参数
   * @returns 流程图表响应
   */
  getProcessDiagram(id: number) {
    return request<ProcessDiagramResponseVO>({
      url: `${INVITATIONS_BASE_URL}/${id}/process-diagram`,
      method: "get",
    });
  },
};

export default InvitationsAPI;
