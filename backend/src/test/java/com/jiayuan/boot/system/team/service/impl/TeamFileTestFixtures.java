package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.system.oss.model.entity.SysFile;

/**
 * 团队文件测试造数工具。
 *
 * @author charleslam
 * @since 2026/05/20
 */
final class TeamFileTestFixtures {

    private TeamFileTestFixtures() {
    }

    static SysFile teamFile(Long teamId, Long userId, Long id, Integer isDirectory) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setSpaceType("TEAM");
        file.setSpaceId(teamId);
        file.setUploaderId(userId);
        file.setOriginalName("demo.txt");
        file.setIsDirectory(isDirectory);
        file.setInRecycleBin(0);
        file.setRecycleRoot(0);
        file.setParentId(0L);
        file.setFileSize(0L);
        return file;
    }

    static SysFile teamDirectory(Long teamId, Long userId, Long id, Long parentId, String fullPath) {
        SysFile directory = teamFile(teamId, userId, id, 1);
        directory.setParentId(parentId);
        directory.setFullPath(fullPath);
        directory.setOriginalName("dir");
        return directory;
    }
}
