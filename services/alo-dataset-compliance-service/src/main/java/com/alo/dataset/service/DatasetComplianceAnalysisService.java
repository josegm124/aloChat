package com.alo.dataset.service;

import com.alo.contracts.assessment.ApplicableFramework;
import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.assessment.CompliancePillar;
import com.alo.contracts.assessment.ControlFinding;
import com.alo.contracts.assessment.ControlFindingStatus;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.FindingSeverity;
import com.alo.contracts.events.DatasetAnalysisRequestedEvent;
import com.alo.dataset.api.DatasetAnalysisRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class DatasetComplianceAnalysisService {
    private final Clock clock;

    public DatasetComplianceAnalysisService() {
        this(Clock.systemUTC());
    }

    DatasetComplianceAnalysisService(Clock clock) {
        this.clock = clock;
    }

    public DatasetAnalysisResult analyze(DatasetAnalysisRequest request) {
        DatasetSnapshot snapshot = snapshot(request);
        Instant now = clock.instant();

        List<String> observations = new ArrayList<>();
        observations.add("Detected format: " + snapshot.format());
        observations.add("Estimated columns: " + snapshot.columnCount());
        observations.add("Estimated rows: " + snapshot.rowCount());
        if (containsSensitiveSignals(snapshot.columnNames())) {
            observations.add("Sensitive or personal data signals detected in column names.");
        }
        if (snapshot.columnCount() == 0) {
            observations.add("No columns were detected from the uploaded dataset.");
        }

        List<ControlFinding> findings = List.of(
                new ControlFinding(
                        UUID.randomUUID().toString(),
                        request.assessmentId(),
                        ApplicableFramework.GDPR,
                        CompliancePillar.DATA_GOVERNANCE,
                        "DS-001",
                        "Dataset structure and governance visibility",
                        snapshot.columnCount() > 0 ? ControlFindingStatus.PARTIAL : ControlFindingStatus.GAP,
                        snapshot.columnCount() > 0 ? FindingSeverity.MEDIUM : FindingSeverity.HIGH,
                        snapshot.columnCount() > 0
                                ? "The dataset structure is visible and can be profiled further in later iterations."
                                : "The dataset structure could not be profiled from the uploaded file.",
                        List.of(),
                        List.of("Add schema documentation and explicit data governance details for the dataset.")
                ),
                new ControlFinding(
                        UUID.randomUUID().toString(),
                        request.assessmentId(),
                        ApplicableFramework.GDPR,
                        CompliancePillar.DATA_GOVERNANCE,
                        "DS-002",
                        "Sensitive data exposure indicators",
                        containsSensitiveSignals(snapshot.columnNames()) ? ControlFindingStatus.PARTIAL : ControlFindingStatus.COMPLIANT,
                        containsSensitiveSignals(snapshot.columnNames()) ? FindingSeverity.HIGH : FindingSeverity.LOW,
                        containsSensitiveSignals(snapshot.columnNames())
                                ? "Column names suggest the presence of personal or sensitive data that requires governance review."
                                : "No direct sensitive-data indicators were found in dataset column names.",
                        List.of(),
                        List.of("Review lawful basis, minimization, retention, and access controls for dataset fields.")
                )
        );

        return new DatasetAnalysisResult(
                request.assessmentId(),
                request.artifactId(),
                request.preferredLanguage(),
                request.sector(),
                request.regulatoryProfileId(),
                snapshot.format(),
                request.file().getSize(),
                snapshot.rowCount(),
                snapshot.columnCount(),
                snapshot.columnNames(),
                observations,
                findings,
                now
        );
    }

    public DatasetAnalysisResult analyze(DatasetAnalysisRequestedEvent event) {
        Instant now = clock.instant();
        ArtifactRef artifact = event.artifact();
        String format = formatFromArtifact(artifact);
        List<String> observations = new ArrayList<>();
        observations.add("Dataset artifact registered for analysis.");
        observations.add("Storage location: " + artifact.s3Bucket() + "/" + artifact.s3Key());
        observations.add("Format inferred from artifact type: " + format);

        List<ControlFinding> findings = List.of(
                new ControlFinding(
                        UUID.randomUUID().toString(),
                        event.assessment().assessmentId(),
                        ApplicableFramework.GDPR,
                        CompliancePillar.DATA_GOVERNANCE,
                        "DS-INGEST-001",
                        "Dataset metadata registered for analysis",
                        ControlFindingStatus.PARTIAL,
                        FindingSeverity.MEDIUM,
                        "The dataset artifact is registered and queued. Full profiling requires the S3 retrieval step.",
                        List.of(),
                        List.of("Fetch the dataset from S3 and run schema, privacy, and representativeness profiling.")
                )
        );

        return new DatasetAnalysisResult(
                event.assessment().assessmentId(),
                artifact.artifactId(),
                event.assessment().preferredLanguage(),
                event.assessment().sector(),
                event.regulatoryProfile().regulatoryProfileId(),
                format,
                artifact.sizeBytes(),
                0,
                0,
                List.of(),
                observations,
                findings,
                now
        );
    }

    private DatasetSnapshot snapshot(DatasetAnalysisRequest request) {
        String format = detectFormat(request);
        return switch (format) {
            case "csv" -> snapshotCsv(request);
            case "xlsx" -> snapshotXlsx(request);
            case "parquet" -> snapshotParquet(request);
            default -> throw new IllegalArgumentException("Unsupported dataset format: " + format);
        };
    }

    private DatasetSnapshot snapshotCsv(DatasetAnalysisRequest request) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.file().getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            List<String> columnNames = splitCsvHeader(header);
            int rows = 0;
            while (reader.readLine() != null && rows < 1000) {
                rows++;
            }
            return new DatasetSnapshot("csv", rows, columnNames.size(), columnNames);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to profile CSV dataset", exception);
        }
    }

    private DatasetSnapshot snapshotXlsx(DatasetAnalysisRequest request) {
        try (InputStream inputStream = request.file().getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                return new DatasetSnapshot("xlsx", 0, 0, List.of());
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            List<String> columnNames = header == null ? List.of() : readHeaderCells(header);
            int rows = Math.max(sheet.getPhysicalNumberOfRows() - 1, 0);
            return new DatasetSnapshot("xlsx", rows, columnNames.size(), columnNames);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to profile XLSX dataset", exception);
        }
    }

    private DatasetSnapshot snapshotParquet(DatasetAnalysisRequest request) {
        String originalName = request.file().getOriginalFilename();
        List<String> observations = originalName == null ? List.of() : inferColumnHintsFromFileName(originalName);
        return new DatasetSnapshot("parquet", 0, observations.size(), observations);
    }

    private String detectFormat(DatasetAnalysisRequest request) {
        String fileName = request.file().getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            throw new IllegalArgumentException("Dataset file must include an extension.");
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private List<String> splitCsvHeader(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private List<String> readHeaderCells(Row header) {
        List<String> cells = new ArrayList<>();
        header.forEach(cell -> cells.add(cell.getStringCellValue().trim()));
        return cells.stream().filter(value -> !value.isEmpty()).toList();
    }

    private List<String> inferColumnHintsFromFileName(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        List<String> hints = new ArrayList<>();
        if (normalized.contains("patient")) {
            hints.add("patient");
        }
        if (normalized.contains("diagnosis")) {
            hints.add("diagnosis");
        }
        if (normalized.contains("employee")) {
            hints.add("employee");
        }
        return hints;
    }

    private boolean containsSensitiveSignals(List<String> columnNames) {
        return columnNames.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("name")
                        || value.contains("email")
                        || value.contains("phone")
                        || value.contains("birth")
                        || value.contains("age")
                        || value.contains("gender")
                        || value.contains("patient")
                        || value.contains("diagnosis")
                        || value.contains("salary"));
    }

    private String formatFromArtifact(ArtifactRef artifact) {
        return switch (artifact.artifactType()) {
            case DATASET_CSV -> "csv";
            case DATASET_XLSX -> "xlsx";
            case DATASET_PARQUET -> "parquet";
            case DOCUMENT_PDF -> "unknown";
        };
    }

    private record DatasetSnapshot(
            String format,
            int rowCount,
            int columnCount,
            List<String> columnNames
    ) {
    }
}
