package com.jiayuan.boot.system.oss.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件列表 Mapper XML 回归测试。
 *
 * @author charleslam
 * @since 2026/05/24
 */
@DisplayName("SysFileMapper XML recursive directory size")
class SysFileMapperXmlRecursiveSizeTest {

    private static final Path MAPPER_XML = Path.of("src/main/resources/mapper/SysFileMapper.xml");

    @Test
    @DisplayName("列表查询递归汇总目录大小")
    void childListQueries_projectRecursiveDirectorySize() throws IOException {
        String xml = Files.readString(MAPPER_XML);

        assertRecursiveSizeFragment(xml);
        assertSingleFileQueryUsesRecursiveSize(xml);
        assertChildQueryUsesRecursiveSize(xml, "selectPersonalChildren");
        assertChildQueryUsesRecursiveSize(xml, "selectTeamChildren");
        assertChildQueryUsesRecursiveSize(xml, "selectChildrenInSpace");
    }

    private void assertRecursiveSizeFragment(String xml) {
        Pattern pattern = Pattern.compile(
                "<sql\\s+id=\"RecursiveChildSizeSelect\"[\\s\\S]*?</sql>", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(xml);

        assertThat(matcher.find())
                .as("recursive size SQL fragment should exist")
                .isTrue();
        String fragmentSql = matcher.group();
        assertThat(fragmentSql)
                .contains("directory_sizes")
                .contains("CASE WHEN child_nodes.is_directory = 1")
                .contains("COALESCE(directory_sizes.total_size, 0)");
        assertThat(xml)
                .contains("<sql id=\"RecursiveSingleFileSizeSelect\">")
                .contains("CASE WHEN target_node.is_directory = 1");
    }

    private void assertChildQueryUsesRecursiveSize(String xml, String selectId) {
        Pattern pattern = Pattern.compile(
                "<select\\s+id=\"" + selectId + "\"[\\s\\S]*?</select>", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(xml);

        assertThat(matcher.find())
                .as("mapper select %s should exist", selectId)
                .isTrue();
        String selectSql = matcher.group();
        assertThat(selectSql)
                .as("mapper select %s should recursively sum descendant file sizes for directories", selectId)
                .contains("WITH RECURSIVE child_nodes AS")
                .contains("<include refid=\"RecursiveChildSizeSelect\" />");
    }

    private void assertSingleFileQueryUsesRecursiveSize(String xml) {
        Pattern pattern = Pattern.compile(
                "<select\\s+id=\"selectSpaceFileWithRecursiveSize\"[\\s\\S]*?</select>", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(xml);

        assertThat(matcher.find())
                .as("mapper select selectSpaceFileWithRecursiveSize should exist")
                .isTrue();
        String selectSql = matcher.group();
        assertThat(selectSql)
                .contains("WITH RECURSIVE target_node AS")
                .contains("<include refid=\"RecursiveSingleFileSizeSelect\" />");
    }
}
