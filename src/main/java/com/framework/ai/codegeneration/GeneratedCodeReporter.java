package com.framework.ai.codegeneration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates human review report (generation-report.md) and writes generated
 * draft Java code files strictly into target/ai-generated/.
 * Guarantees zero code is written to src/.
 */
public final class GeneratedCodeReporter {

    private static final Logger LOGGER = LogManager.getLogger(GeneratedCodeReporter.class);
    private static final Path DEFAULT_TARGET_DIR = Paths.get("target", "ai-generated");

    private GeneratedCodeReporter() {
    }

    /**
     * Writes all draft code and human review report to target/ai-generated/.
     */
    public static Path exportDraftFiles(GeneratedTestCodeResponse response, String scenarioTitle) {
        if (response == null || !response.isSuccess()) {
            return null;
        }

        try {
            Files.createDirectories(DEFAULT_TARGET_DIR);

            // 1. Write the draft Java test file
            String classFileName = (response.getTestClassName().isBlank() ? "DraftTest" : response.getTestClassName()) + ".java";
            Path javaFilePath = DEFAULT_TARGET_DIR.resolve(classFileName).normalize();
            guardAgainstPathTraversal(javaFilePath);
            Files.writeString(javaFilePath, response.getTestClassCode(), StandardCharsets.UTF_8);
            LOGGER.info("Exported draft Java test to: {}", javaFilePath);

            // 2. Write the Page Object suggestions if present
            if (!response.getPageObjectMethods().isEmpty()) {
                Path poFilePath = DEFAULT_TARGET_DIR.resolve("PageObjectDraftSuggestions.java").normalize();
                guardAgainstPathTraversal(poFilePath);
                StringBuilder poSb = new StringBuilder();
                poSb.append("// AI-GENERATED DRAFT PAGE OBJECT METHODS\n// HUMAN REVIEW REQUIRED\n\n");
                for (String method : response.getPageObjectMethods()) {
                    poSb.append(method).append("\n\n");
                }
                Files.writeString(poFilePath, poSb.toString(), StandardCharsets.UTF_8);
            }

            // 3. Write generation-report.md
            String reportMarkdown = buildMarkdownReport(response, scenarioTitle);
            Path reportPath = DEFAULT_TARGET_DIR.resolve("generation-report.md").normalize();
            guardAgainstPathTraversal(reportPath);
            Files.writeString(reportPath, reportMarkdown, StandardCharsets.UTF_8);
            LOGGER.info("Exported human review report to: {}", reportPath);

            return DEFAULT_TARGET_DIR;
        } catch (Exception e) {
            LOGGER.warn("Failed to export draft files: {}", e.getMessage());
            return null;
        }
    }

    public static String buildMarkdownReport(GeneratedTestCodeResponse response, String scenarioTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI Test Code Generation & Human Review Report\n\n");
        sb.append("**Scenario:** ").append(scenarioTitle != null ? scenarioTitle : "N/A").append("\n");
        sb.append("**Generated Class:** `").append(response.getTestClassName()).append(".java`\n");
        sb.append("**Target Package:** `").append(response.getPackageName()).append("`\n");
        sb.append("**Generated At:** ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");

        sb.append("> [!IMPORTANT]\n");
        sb.append("> **CRITICAL SAFETY NOTICE:**\n");
        sb.append("> This code was produced by AI as an unverified draft. It resides exclusively inside `target/ai-generated/`.\n");
        sb.append("> It MUST NOT be moved to `src/test/java` without explicit manual QA review.\n\n");

        sb.append("## Framework Classes Referenced\n\n");
        if (!response.getReferencedFrameworkClasses().isEmpty()) {
            for (String cls : response.getReferencedFrameworkClasses()) {
                sb.append("- `").append(cls).append("`\n");
            }
        } else {
            sb.append("- `BaseWebTest`, `PlaywrightManager`\n");
        }
        sb.append("\n");

        sb.append("## Locators Used\n\n");
        if (!response.getLocatorsUsed().isEmpty()) {
            for (String loc : response.getLocatorsUsed()) {
                sb.append("- `").append(loc).append("`\n");
            }
        } else {
            sb.append("*(None specified or handled via Page Object methods)*\n");
        }
        sb.append("\n");

        sb.append("## Test Data Used\n\n");
        if (!response.getTestDataUsed().isEmpty()) {
            for (String td : response.getTestDataUsed()) {
                sb.append("- `").append(td).append("`\n");
            }
        } else {
            sb.append("*(Standard default environment data)*\n");
        }
        sb.append("\n");

        if (!response.getWarnings().isEmpty()) {
            sb.append("## Warnings & Potential Risks\n\n");
            for (String w : response.getWarnings()) {
                sb.append("- ⚠️ ").append(w).append("\n");
            }
            sb.append("\n");
        }

        if (!response.getAssumptions().isEmpty()) {
            sb.append("## Assumptions Made\n\n");
            for (String a : response.getAssumptions()) {
                sb.append("- ").append(a).append("\n");
            }
            sb.append("\n");
        }

        if (!response.getAnalyticsSuggestions().isEmpty()) {
            sb.append("## Analytics & Tracking Validation Suggestions\n\n");
            for (String an : response.getAnalyticsSuggestions()) {
                sb.append("- ").append(an).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Human Review Checklist\n\n");
        sb.append("- [ ] Verify locators match real DOM in QA/staging\n");
        sb.append("- [ ] Verify test data placeholders are resolved\n");
        sb.append("- [ ] Verify expected AssertJ assertions are sufficient\n");
        sb.append("- [ ] Verify no Thread.sleep() or arbitrary delays exist\n");
        sb.append("- [ ] Verify existing Page Object methods are reused\n");
        sb.append("- [ ] Verify business logic matches product specs\n");
        sb.append("- [ ] Verify negative/boundary scenarios are covered\n");
        sb.append("- [ ] Verify analytics validation where applicable\n");
        sb.append("- [ ] Verify zero credentials, tokens, or PII exist in code\n");
        sb.append("- [ ] Run manually on QA/staging before promoting\n");
        sb.append("- [ ] Approve before merging into `src/test/java`\n\n");

        return sb.toString();
    }

    private static void guardAgainstPathTraversal(Path path) {
        Path normalized = path.normalize().toAbsolutePath();
        Path base = DEFAULT_TARGET_DIR.normalize().toAbsolutePath();
        if (!normalized.startsWith(base)) {
            throw new SecurityException("Path traversal attempt detected: " + path);
        }
    }
}