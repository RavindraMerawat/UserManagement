package com.user.management;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No column is named after a word MySQL reserves, unless it is quoted.
 *
 * <p>This is here because {@code grouping} slipped through: MySQL 8 reserves it, so
 * every insert into sewadars failed with a syntax error - while the whole suite
 * stayed green, because the tests run on H2 and H2 reserves nothing of the sort. A
 * test that only ever meets H2 cannot tell you this, so the check is on the mapping
 * itself rather than on a query.</p>
 *
 * <p>Naming a field after a reserved word is allowed; leaving it unquoted is not.
 * Backticks in {@code @Column(name = "`grouping`")} tell Hibernate to quote it
 * everywhere it writes SQL.</p>
 */
class ReservedColumnNamesTest {

    /**
     * MySQL 8.0 reserved words that could plausibly end up as a column name here.
     * Not the whole list - the whole list contains words nobody would name a field
     * after, and a shorter list is one people will keep up to date.
     */
    private static final Set<String> RESERVED = Set.of(
            "add", "all", "and", "as", "asc", "before", "between", "both", "by", "call",
            "cascade", "case", "change", "character", "check", "collate", "column",
            "condition", "constraint", "continue", "convert", "create", "cross", "cube",
            "cume_dist", "current_date", "current_time", "cursor", "database", "day_hour",
            "dec", "decimal", "declare", "default", "delayed", "delete", "dense_rank",
            "desc", "describe", "distinct", "div", "double", "drop", "dual", "each",
            "else", "empty", "enclosed", "escaped", "except", "exists", "exit", "explain",
            "false", "fetch", "first_value", "float", "for", "force", "foreign", "from",
            "fulltext", "function", "generated", "get", "grant", "group", "grouping",
            "groups", "having", "if", "ignore", "in", "index", "infile", "inner", "inout",
            "insert", "int", "integer", "interval", "into", "is", "iterate", "join",
            "key", "keys", "kill", "lag", "last_value", "lead", "leading", "leave",
            "left", "like", "limit", "lines", "load", "lock", "long", "loop", "match",
            "maxvalue", "member", "mod", "modifies", "natural", "not", "null", "numeric",
            "of", "on", "optimize", "option", "optionally", "or", "order", "out", "outer",
            "over", "partition", "percent_rank", "precision", "primary", "procedure",
            "purge", "range", "rank", "read", "reads", "real", "recursive", "references",
            "regexp", "release", "rename", "repeat", "replace", "require", "resignal",
            "restrict", "return", "revoke", "right", "rlike", "row", "row_number", "rows",
            "schema", "select", "sensitive", "separator", "set", "show", "signal",
            "spatial", "specific", "sql", "sqlstate", "ssl", "starting", "stored",
            "straight_join", "system", "table", "terminated", "then", "to", "trailing",
            "trigger", "true", "undo", "union", "unique", "unlock", "unsigned", "update",
            "usage", "use", "using", "values", "varchar", "varying", "virtual", "when",
            "where", "while", "window", "with", "write", "xor", "zerofill");

    @Test
    @DisplayName("a column named after a reserved word is quoted, or MySQL refuses the statement")
    void reservedColumnNamesAreQuoted() throws Exception {
        List<String> offenders = new ArrayList<>();

        for (Class<?> entity : entities()) {
            for (Field field : entity.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                String name = columnName(field);
                if (name == null || name.startsWith("`")) {
                    continue;            // quoted, or not a column of its own
                }
                if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
                    offenders.add(entity.getSimpleName() + "." + field.getName()
                            + " maps to " + name + " - wrap it in backticks: @Column(name = \"`"
                            + name + "`\")");
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    /** The name the column takes, or null when the field is not a column by itself. */
    private static String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null) {
            return column.name().isBlank() ? field.getName() : column.name();
        }
        JoinColumn join = field.getAnnotation(JoinColumn.class);
        if (join != null) {
            return join.name().isBlank() ? field.getName() : join.name();
        }
        // A plain field with no mapping annotation still becomes a column of its name,
        // unless it is a collection or an association, which live in their own tables.
        if (field.getType().getName().startsWith("java.")
                || field.getType().isEnum() || field.getType().isPrimitive()) {
            return field.getName();
        }
        return null;
    }

    private static List<Class<?>> entities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.user.management.entity")) {
            found.add(Class.forName(definition.getBeanClassName()));
        }
        assertThat(found).as("entities to check").isNotEmpty();
        return found;
    }
}
