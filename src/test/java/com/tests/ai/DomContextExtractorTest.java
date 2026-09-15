package com.tests.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.ai.extractor.DomContextExtractor;
import org.testng.annotations.Test;

public class DomContextExtractorTest {

    @Test
    public void testSafeTruncateWithinLimit() {
        String shortHtml = "<html><body><div>Short content</div></body></html>";
        String result = DomContextExtractor.truncateSafely(shortHtml, 1000);
        assertThat(result).isEqualTo(shortHtml);
    }

    @Test
    public void testSafeTruncateExceedingLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("<div>Item ").append(i).append("</div>\n");
        }
        String largeHtml = sb.toString();
        int maxBytes = 200;

        String result = DomContextExtractor.truncateSafely(largeHtml, maxBytes);
        assertThat(result).contains("... [DOM truncated: exceeded 200 bytes limit]");
        assertThat(result.length()).isLessThan(largeHtml.length());
    }

    @Test
    public void testExtractSafeDomWithoutPageReturnsFallback() {
        // In unit test context without Playwright init, it must return fallback and never throw
        String dom = DomContextExtractor.extractSafeDom();
        assertThat(dom).isEqualTo("[DOM context unavailable]");
    }
}