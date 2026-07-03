# CHANGELOG

## 2026-05-24

### Team Account Identity

- 团队成员、Owner、邀请、团队权限、团队菜单和团队分享创建者有效性统一按 `accountId` 判断；个人文件、VIP、配额和私密空间仍保持 `userId` 作用域。
- `POST /api/v1/team/{teamId}/invitations` 请求体改为 `CreateInvitationRequestVO`，创建邀请时不再要求客户端传 `action`；旧统一动作接口仍使用 `InvitationActionRequestVO`。
- 团队相关响应补充账户维度字段：团队响应返回 `ownerAccountId`，成员和邀请响应返回成员/邀请账户 ID 与账户名；Owner 转让请求新增 `targetAccountId`，`targetMemberId` 仅作旧客户端兼容。
- 对齐 OpenAPI `operationId` 与 Controller 方法名，避免个人/团队文件接口重复方法名再次生成 `_1` / `_2` 后缀。

## 2026-05-22

### B17a Permission and Role Read APIs

- 新增 `GET /api/v1/permissions`：列出系统团队权限点定义，供前端按钮显隐和权限解释使用。
- 新增 `GET /api/v1/roles`：列出团队角色选项及各角色权限点。
- 新增 `GET /api/v1/team/{teamId}/permissions`：获取当前用户在团队内的角色、权限点、团队状态和配额/VIP 限制信息。

### B17b Directory Gap APIs

- 新增 `PUT /api/v1/personal/directories/{directoryId}/move`：移动个人目录，复用个人文件/目录移动语义。
- 新增 `PUT /api/v1/team/{teamId}/directories/{directoryId}/rename`：重命名团队目录，权限点为 `file:move`。

## 2026-05-21

- 对齐 OpenAPI `operationId` 与 Controller 方法名，移除重复方法名导致的 `_1` / `_2` 生成后缀；接口路径、请求响应和业务行为不变。

## 2026-05-20

### B14a Team Trash Foundation + List

- 新增 `GET /api/v1/team/{teamId}/trash`：列出团队回收站根节点，仅返回主动删除进入回收站的团队文件/目录。
- 响应体使用 `Result<List<TeamTrashItemResponseVO>>`，权限点为 `trash:list`。

### B14b Team Trash Restore

- 新增 `POST /api/v1/team/{teamId}/trash/{trashId}/restore`：恢复团队回收站根节点及其后代。
- 支持 `conflictPolicy=RENAME|OVERWRITE` 处理目标目录同名冲突，权限点为 `trash:restore`，响应体为 `Result<TeamFileResponseVO>`。
- 未指定 `conflictPolicy` 且目标目录存在同名项时返回非成功响应，并在 `data` 中携带冲突的 `TeamFileResponseVO`；过期回收站项拒绝恢复。

### B14c Team Trash Permanent Delete

- 新增 `DELETE /api/v1/team/{teamId}/trash/{trashId}`：永久删除团队回收站根节点及其后代。
- 权限点为 `trash:delete`，仅允许删除仍在团队回收站、`recycle_root=1` 且未逻辑删除的团队文件/目录；永久删除允许处理已过期但仍存在的回收站项。
- 永久删除会释放对应文件对象引用，并通过 Mapper XML 递归逻辑删除元数据；团队逻辑配额已在移入回收站时释放，此接口不再次扣减配额。

### B14d1 Personal-to-Team Transfer

- 新增 `POST /api/v1/team/{teamId}/files/from-personal`：转存当前用户个人空间活动文件或目录到团队目录。
- 请求体为 `TransferFromPersonalRequestVO`，`sourceFileId` 必填，`targetDirectoryId` 为空时默认根目录，`conflictPolicy` 可为空、`RENAME` 或 `OVERWRITE`；当前版本均按自动重命名处理目标同名冲突。
- 转存目录会递归复制活动后代，复用物理对象并增加引用计数；写入前按转存逻辑大小校验团队配额，成功后增加团队已用空间。

### B14d2 Team-to-Personal Transfer

- 新增 `POST /api/v1/team/{teamId}/files/{fileId}/save-to-personal`：转存团队活动文件或目录到当前用户个人空间。
- 请求体为 `TransferToPersonalRequestVO`，`targetDirectoryId` 为空或 `0` 时默认个人根目录，`conflictPolicy` 为兼容字段；当前版本始终按自动重命名处理同名冲突，不执行覆盖。
- 转存目录会递归复制活动后代，复用物理对象并按文件增加引用计数；写入前校验个人总配额和普通用户单文件限制，成功后增加个人已用空间。
