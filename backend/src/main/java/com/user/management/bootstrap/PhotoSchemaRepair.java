package com.user.management.bootstrap;

import com.user.management.entity.Photo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Repairs two early photo-table shapes. MySQL only, safe to run again, and it never
 * touches photo data - only the column type and the index.
 *
 * <ol>
 *   <li><b>{@code data} generated as {@code tinyblob}.</b> The entity did not declare
 *       a length, so Hibernate used the 255 byte default and MySQL gave the column a
 *       255 byte ceiling. Every real photo failed to insert with
 *       {@code Data too long for column 'data'}, and only a tiny test image could
 *       ever have been stored. {@code ddl-auto=update} adds columns but does not
 *       retype an existing one, so the widening has to happen here.</li>
 *   <li><b>{@code owner_id} unique by itself.</b> User id 1 and sewadar id 1 can
 *       legitimately both have a photo, so the unique key must include the owner
 *       type.</li>
 * </ol>
 */
@Slf4j
@Component
public class PhotoSchemaRepair implements ApplicationRunner {

    private static final String TABLE = "photos";
    private static final String DATA_COLUMN = "data";

    /** Blob types too small to hold a {@link Photo#MAX_DATA_BYTES} image. */
    private static final Set<String> UNDERSIZED_BLOB_TYPES = Set.of("tinyblob", "blob");

    private final DataSource dataSource;

    public PhotoSchemaRepair(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql")) {
                return;
            }

            widenDataColumn(connection);

            Map<String, List<String>> uniqueIndexes = uniqueIndexes(connection);
            for (Map.Entry<String, List<String>> entry : uniqueIndexes.entrySet()) {
                if (sameColumns(entry.getValue(), List.of("ownerId"))) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("DROP INDEX `" + entry.getKey() + "` ON `" + TABLE + "`");
                    }
                    log.info("Removed obsolete unique photos index {} on ownerId", entry.getKey());
                }
            }

            uniqueIndexes = uniqueIndexes(connection);
            boolean compositeOwnerKey = uniqueIndexes.values().stream()
                    .anyMatch(columns -> sameColumns(columns, List.of("ownerType", "ownerId")));
            if (!compositeOwnerKey) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE UNIQUE INDEX `uk_photo_owner` ON `" + TABLE
                            + "` (`ownerType`, `ownerId`)");
                }
                log.info("Created photos composite unique index uk_photo_owner");
            }
        }
    }

    /**
     * Grows a {@code tinyblob} or {@code blob} {@code data} column to
     * {@code mediumblob}, which is what the entity's declared length generates on a
     * new database. A column that is already {@code mediumblob} or {@code longblob}
     * is left exactly as it is - this only ever widens.
     */
    private void widenDataColumn(Connection connection) throws SQLException {
        String type = dataColumnType(connection);
        if (type == null || !UNDERSIZED_BLOB_TYPES.contains(type)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE `" + TABLE + "` MODIFY `" + DATA_COLUMN
                    + "` MEDIUMBLOB NOT NULL");
        }
        log.info("Widened {}.{} from {} to mediumblob - a {} column cannot hold a photo, "
                        + "so every upload was failing with 'Data too long'",
                TABLE, DATA_COLUMN, type, type);
    }

    /** Column-name comparison the way MySQL does it: same order, case ignored. */
    private boolean sameColumns(List<String> actual, List<String> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            if (!actual.get(i).equalsIgnoreCase(expected.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** The declared type of photos.data, lower case, or null if the table is new. */
    private String dataColumnType(Connection connection) throws SQLException {
        String sql = """
                select data_type from information_schema.columns
                where table_schema = ? and table_name = ? and column_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, connection.getCatalog());
            statement.setString(2, TABLE);
            statement.setString(3, DATA_COLUMN);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1).toLowerCase(Locale.ROOT) : null;
            }
        }
    }

    /**
     * Unique indexes on the photos table, index name to its columns.
     *
     * <p>The catalog is passed explicitly: MySQL's driver reads a null catalog as
     * "every database on this connection", so a second database with a photos table
     * would have its index names reported here and then dropped against the wrong
     * schema, which fails and stops startup.</p>
     */
    private Map<String, List<String>> uniqueIndexes(Connection connection) throws SQLException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (ResultSet rows = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), null, TABLE, true, false)) {
            while (rows.next()) {
                String indexName = rows.getString("INDEX_NAME");
                String columnName = rows.getString("COLUMN_NAME");
                if (indexName != null && columnName != null && !"PRIMARY".equalsIgnoreCase(indexName)) {
                    // Kept as the database reports it. MySQL matches column names
                    // case-insensitively, so the comparisons below do too rather than
                    // folding the case here and never matching a camelCase name.
                    result.computeIfAbsent(indexName, ignored -> new ArrayList<>())
                            .add(columnName);
                }
            }
        }
        return result;
    }
}
