package com.alo.contracts.assessment;

import java.util.List;

public record ReportSection(
        String title,
        String summary,
        List<String> items
) {
}
