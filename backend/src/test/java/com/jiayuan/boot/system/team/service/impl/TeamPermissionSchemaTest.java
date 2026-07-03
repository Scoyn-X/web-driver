package com.jiayuan.boot.system.team.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 团队权限 SQL 初始化回归测试。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@DisplayName("团队权限 SQL 初始化测试")
class TeamPermissionSchemaTest {

    @Test
    @DisplayName("最新全量 schema 包含团队 RBAC 初始化数据")
    void fullSchemaSeedsTeamRbacData() throws IOException {
        String schema = Files.readString(Path.of("sql/mysql/jiayuan_boot.sql"));

        assertThat(schema)
                .contains("INSERT IGNORE INTO team_role")
                .contains("INSERT IGNORE INTO team_permission")
                .contains("INSERT IGNORE INTO team_role_permission")
                .contains("'file:upload'")
                .contains("'share:create'")
                .contains("'trash:list'");
    }

    @Test
    @DisplayName("最新 SQL 包含回收站删除人字段")
    void latestSqlContainsTrashDeletedByColumn() throws IOException {
        String schema = Files.readString(Path.of("sql/mysql/jiayuan_boot.sql"));
        String migration = Files.readString(Path.of("sql/mysql/migration_20260523.sql"));

        assertThat(schema).contains("deleted_by     BIGINT");
        assertThat(migration)
                .contains("ADD COLUMN deleted_by BIGINT")
                .contains("COLUMN_NAME = 'deleted_by'");
    }
}
