package com.user.management;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Twenty-five rows to a page, on every endpoint that returns a page.
 *
 * <p>The office has asked for this three times, and each time the screens that were
 * looked at were fixed and the rest were left: the number lived in each controller
 * and each screen separately, so there was nowhere to change it once. This test is
 * the place that notices. It does not check the screens the office named - it walks
 * <b>every</b> controller and fails on any {@code size} parameter that defaults to
 * anything else, including one added next month.</p>
 *
 * <p>The browser half has the same guard in {@code src/pageSize.js}: one constant
 * that every grid imports. This checks the server agrees with it, because a call
 * that leaves the size off gets the server's number.</p>
 */
class PageSizeTest {

    /** What a page holds. The same number as {@code PAGE_SIZE} in the browser. */
    private static final String ROWS_PER_PAGE = "25";

    @Test
    @DisplayName("every paged endpoint defaults to twenty-five rows")
    void everyControllerPagesAtTwentyFive() {
        List<String> wrong = new ArrayList<>();

        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    RequestParam annotation = parameter.getAnnotation(RequestParam.class);
                    if (annotation == null || !isPageSize(parameter)) {
                        continue;
                    }
                    if (!ROWS_PER_PAGE.equals(annotation.defaultValue())) {
                        wrong.add("%s.%s takes size=%s".formatted(
                                controller.getSimpleName(), method.getName(),
                                annotation.defaultValue()));
                    }
                }
            }
        }

        assertThat(wrong)
                .describedAs("These endpoints hand back a page of a different size. "
                        + "The office asked for %s rows everywhere.", ROWS_PER_PAGE)
                .isEmpty();
    }

    @Test
    @DisplayName("the check is actually looking at something")
    void theWalkFindsTheControllers() {
        // Without this, a package rename would turn the test above into one that
        // passes by examining nothing at all.
        assertThat(controllers()).hasSizeGreaterThan(5);
    }

    private boolean isPageSize(Parameter parameter) {
        RequestParam annotation = parameter.getAnnotation(RequestParam.class);
        String name = annotation.value().isBlank() ? parameter.getName() : annotation.value();
        return "size".equals(name) && parameter.getType() == int.class;
    }

    /** Every {@code @RestController} in the application, found on the class path. */
    private List<Class<?>> controllers() {
        Path root;
        try {
            root = Path.of(getClass().getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot locate the compiled classes", e);
        }
        Path pkg = root.resolve("com/user/management/controller");
        if (!Files.isDirectory(pkg)) {
            // Tests and classes are built to different roots; step across.
            pkg = root.getParent().resolve("classes/com/user/management/controller");
        }

        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(pkg)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                Class<?> type = Class.forName("com.user.management.controller."
                        + name.substring(0, name.length() - ".class".length()));
                if (type.isAnnotationPresent(RestController.class)) {
                    found.add(type);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Cannot read the controller package", e);
        }
        return found;
    }
}
