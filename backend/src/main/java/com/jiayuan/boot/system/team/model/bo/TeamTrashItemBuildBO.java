package com.jiayuan.boot.system.team.model.bo;

import com.jiayuan.boot.system.oss.model.entity.SysFile;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队回收站列表项构造参数。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Data
public class TeamTrashItemBuildBO {

    /**
     * 团队回收站文件元数据
     */
    private SysFile file;

    /**
     * 人类可读完整路径
     */
    private String path;

    /**
     * 删除者名称
     */
    private String deletedByName;

    /**
     * 放入回收站时间
     */
    private LocalDateTime deletedAt;

    /**
     * 回收站到期时间
     */
    private LocalDateTime expireAt;

    /**
     * 回收站状态
     */
    private String status;
}
