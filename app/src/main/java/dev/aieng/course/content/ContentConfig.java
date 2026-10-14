package dev.aieng.course.content;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ContentConfig {

    /**
     * Course root = the directory holding {@code content/} and {@code lessons/}. Defaults to the current
     * directory or its parent, so both {@code java -jar} from the repo root and {@code spring-boot:run}
     * inside {@code app/} work without configuration.
     */
    @Bean
    Course course(@Value("${course.root:}") String configured) {
        Path root = configured.isBlank() ? detectRoot() : Path.of(configured);
        return new ContentLoader(root.toAbsolutePath().normalize()).load();
    }

    private static Path detectRoot() {
        for (Path candidate : new Path[] {Path.of("."), Path.of("..")}) {
            if (Files.isDirectory(candidate.resolve("content"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No content/ directory found; set course.root");
    }
}
