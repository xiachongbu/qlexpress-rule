package com.bjjw.rule.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regenerates specific DDL blocks in schema.sql to keep them
 * in sync with the current table structure definitions.
 */
@Service
public class SchemaSyncService {

    @Value("${rule.schema.path:#{null}}")
    private String schemaPath;

    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile(
            "(-- =+\\s*\\n-- \\d+\\. %s -[^\n]*\\n-- =+\\s*\\n)(CREATE TABLE IF NOT EXISTS `%s`[\\s\\S]*?;)",
            Pattern.MULTILINE);

    /**
     * Read the current schema.sql and return its content.
     */
    public String readSchema() throws IOException {
        Path path = resolveSchemaPath();
        if (path != null && Files.exists(path)) {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        ClassPathResource resource = new ClassPathResource("sql/schema.sql");
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Replace a specific table's CREATE TABLE block in schema.sql.
     */
    public void updateTableBlock(String tableName, String newCreateStatement) throws IOException {
        Path path = resolveSchemaPath();
        if (path == null || !Files.exists(path)) return;

        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String escaped = Pattern.quote(tableName);
        Pattern pattern = Pattern.compile(
                "(CREATE TABLE IF NOT EXISTS `" + escaped + "` \\([\\s\\S]*?\\)[^;]*;)");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            content = content.substring(0, matcher.start()) + newCreateStatement + content.substring(matcher.end());
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Trigger a full schema sync — re-read and update all managed table blocks.
     * Currently this just verifies the file is readable; the actual DDL is maintained
     * by the schema.sql file directly, which was updated at migration time.
     */
    public String syncAndGetStatus() throws IOException {
        String content = readSchema();
        boolean hasDataObject = content.contains("rule_data_object");
        boolean hasObjectField = content.contains("rule_data_object_field");

        StringBuilder sb = new StringBuilder();
        sb.append("rule_data_object: ").append(hasDataObject ? "OK" : "MISSING").append("; ");
        sb.append("rule_data_object_field: ").append(hasObjectField ? "OK" : "MISSING");
        return sb.toString();
    }

    private Path resolveSchemaPath() {
        if (schemaPath != null && !schemaPath.isEmpty()) {
            return Paths.get(schemaPath);
        }
        String userDir = System.getProperty("user.dir");
        Path candidate = Paths.get(userDir, "src", "main", "resources", "sql", "schema.sql");
        if (Files.exists(candidate)) return candidate;
        candidate = Paths.get(userDir, "rule-engine-server", "src", "main", "resources", "sql", "schema.sql");
        if (Files.exists(candidate)) return candidate;
        return null;
    }
}
