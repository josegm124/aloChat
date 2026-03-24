package com.alo.report.storage;

import com.alo.contracts.assessment.GeneratedAssessmentReport;
import com.alo.report.api.PublishedReportResponse;
import com.alo.report.config.ReportStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class S3ReportStorageService {
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final ReportStorageProperties properties;

    public S3ReportStorageService(
            S3Client s3Client,
            ObjectMapper objectMapper,
            ReportStorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public PublishedReportResponse store(GeneratedAssessmentReport report) {
        String baseKey = baseKey(report.reportId());
        putJson(baseKey + "/report.json", report);
        putString(baseKey + "/report.html", report.webReport().htmlContent(), "text/html; charset=utf-8");
        putBytes(
                baseKey + "/report.pdf",
                Base64.getDecoder().decode(report.pdfReportArtifact().base64Content()),
                report.pdfReportArtifact().contentType()
        );
        return new PublishedReportResponse(report, reportAccessUrl(report.reportId()), pdfDownloadUrl(report.reportId()));
    }

    public GeneratedAssessmentReport loadReport(String reportId) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket())
                    .key(baseKey(reportId) + "/report.json")
                    .build());
            return objectMapper.readValue(bytes.asInputStream(), GeneratedAssessmentReport.class);
        } catch (NoSuchKeyException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to deserialize report " + reportId, exception);
        }
    }

    public String loadHtml(String reportId) {
        return loadUtf8(baseKey(reportId) + "/report.html");
    }

    public byte[] loadPdf(String reportId) {
        return loadBytes(baseKey(reportId) + "/report.pdf");
    }

    public String reportAccessUrl(String reportId) {
        return baseUrl() + "/api/v1/reports/" + reportId + "/view";
    }

    public String pdfDownloadUrl(String reportId) {
        return baseUrl() + "/api/v1/reports/" + reportId + "/pdf";
    }

    private void putJson(String key, Object value) {
        try {
            putBytes(key, objectMapper.writeValueAsBytes(value), "application/json");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize report payload", exception);
        }
    }

    private void putString(String key, String value, String contentType) {
        putBytes(key, value.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private void putBytes(String key, byte[] value, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(value)
        );
    }

    private String loadUtf8(String key) {
        return new String(loadBytes(key), StandardCharsets.UTF_8);
    }

    private byte[] loadBytes(String key) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report artifact not found", exception);
        }
    }

    private String baseKey(String reportId) {
        return "reports/by-report/" + reportId;
    }

    private String bucket() {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new IllegalStateException("alo.report.storage.bucket is required");
        }
        return properties.bucket();
    }

    private String baseUrl() {
        if (properties.publicBaseUrl() == null || properties.publicBaseUrl().isBlank()) {
            return "http://localhost:8086";
        }
        return properties.publicBaseUrl().endsWith("/")
                ? properties.publicBaseUrl().substring(0, properties.publicBaseUrl().length() - 1)
                : properties.publicBaseUrl();
    }
}
