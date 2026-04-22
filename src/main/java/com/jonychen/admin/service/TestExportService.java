package com.jonychen.admin.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.jonychen.test.entity.AIModelTestResultEntity;
import com.jonychen.test.entity.E2ETestResult;
import com.jonychen.test.entity.PerformanceTestResult;
import com.jonychen.test.repository.AIModelTestResultRepository;
import com.jonychen.test.repository.E2ETestResultRepository;
import com.jonychen.test.repository.PerformanceTestResultRepository;
import com.jonychen.test.repository.TestJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 测试结果导出服务
 *
 * <p>支持 JSON、CSV、Excel 三种格式导出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestExportService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TestJobRepository testJobRepository;
    private final E2ETestResultRepository e2eTestResultRepository;
    private final PerformanceTestResultRepository performanceTestResultRepository;
    private final AIModelTestResultRepository aiModelTestResultRepository;
    private final ObjectMapper objectMapper;

    /**
     * 导出测试结果
     *
     * @param testType 测试类型: E2E, PERFORMANCE, AI_MODEL, ALL
     * @param format 导出格式: json, csv, excel
     * @param from 开始时间（可选）
     * @param to 结束时间（可选）
     * @return 导出文件字节数组
     */
    public byte[] exportResults(
            String testType, String format, LocalDateTime from, LocalDateTime to) {
        Map<String, Object> data = collectExportData(testType, from, to);

        return switch (format.toLowerCase()) {
            case "json" -> exportToJson(data);
            case "csv" -> exportToCsv(testType, data);
            case "excel" -> exportToExcel(testType, data);
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        };
    }

    /** 获取导出文件名 */
    public String getExportFileName(String testType, String format) {
        String timestamp =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String typeLabel = testType.toLowerCase().replace("_", "-");
        return switch (format.toLowerCase()) {
            case "json" -> "test-results-" + typeLabel + "-" + timestamp + ".json";
            case "csv" -> "test-results-" + typeLabel + "-" + timestamp + ".csv";
            case "excel" -> "test-results-" + typeLabel + "-" + timestamp + ".xlsx";
            default -> "test-results-" + typeLabel + "-" + timestamp + ".dat";
        };
    }

    /** 获取导出 Content-Type */
    public String getExportContentType(String format) {
        return switch (format.toLowerCase()) {
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    // ==================== 数据收集 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> collectExportData(
            String testType, LocalDateTime from, LocalDateTime to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportTime", LocalDateTime.now().format(FORMATTER));
        data.put("testType", testType);

        if ("ALL".equals(testType) || "E2E".equals(testType)) {
            List<E2ETestResult> e2eResults = collectE2EData(from, to);
            data.put("e2eResults", e2eResults);
            data.put("e2eCount", e2eResults.size());
        }

        if ("ALL".equals(testType) || "PERFORMANCE".equals(testType)) {
            List<PerformanceTestResult> perfResults = collectPerformanceData(from, to);
            data.put("performanceResults", perfResults);
            data.put("performanceCount", perfResults.size());
        }

        if ("ALL".equals(testType) || "AI_MODEL".equals(testType)) {
            List<AIModelTestResultEntity> aiResults = collectAIModelData(from, to);
            data.put("aiModelResults", aiResults);
            data.put("aiModelCount", aiResults.size());
        }

        return data;
    }

    private List<E2ETestResult> collectE2EData(LocalDateTime from, LocalDateTime to) {
        return e2eTestResultRepository.findAll();
    }

    private List<PerformanceTestResult> collectPerformanceData(
            LocalDateTime from, LocalDateTime to) {
        return performanceTestResultRepository.findAll();
    }

    private List<AIModelTestResultEntity> collectAIModelData(LocalDateTime from, LocalDateTime to) {
        return aiModelTestResultRepository.findAll();
    }

    // ==================== JSON 导出 ====================

    private byte[] exportToJson(Map<String, Object> data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export as JSON", e);
        }
    }

    // ==================== CSV 导出 ====================

    private byte[] exportToCsv(String testType, Map<String, Object> data) {
        try {
            CsvMapper csvMapper = new CsvMapper();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer =
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

            // BOM for Excel compatibility
            writer.write('\uFEFF');

            switch (testType) {
                case "E2E", "ALL" -> {
                    @SuppressWarnings("unchecked")
                    List<E2ETestResult> e2eResults =
                            (List<E2ETestResult>) data.getOrDefault("e2eResults", List.of());
                    if (!e2eResults.isEmpty()) {
                        CsvSchema schema =
                                CsvSchema.builder()
                                        .addColumn("jobId")
                                        .addColumn("testName")
                                        .addColumn("status")
                                        .addColumn("durationMs")
                                        .addColumn("assertionsPassed")
                                        .addColumn("assertionsFailed")
                                        .addColumn("errorMessage")
                                        .addColumn("createdAt")
                                        .build()
                                        .withHeader();
                        csvMapper.writer(schema).writeValues(writer).writeAll(e2eResults);
                    }
                }
                case "PERFORMANCE" -> {
                    @SuppressWarnings("unchecked")
                    List<PerformanceTestResult> perfResults =
                            (List<PerformanceTestResult>)
                                    data.getOrDefault("performanceResults", List.of());
                    if (!perfResults.isEmpty()) {
                        CsvSchema schema =
                                CsvSchema.builder()
                                        .addColumn("jobId")
                                        .addColumn("simulation")
                                        .addColumn("requests")
                                        .addColumn("successRate")
                                        .addColumn("avgResponseTime")
                                        .addColumn("maxResponseTime")
                                        .addColumn("p95ResponseTime")
                                        .addColumn("p99ResponseTime")
                                        .addColumn("createdAt")
                                        .build()
                                        .withHeader();
                        csvMapper.writer(schema).writeValues(writer).writeAll(perfResults);
                    }
                }
                case "AI_MODEL" -> {
                    @SuppressWarnings("unchecked")
                    List<AIModelTestResultEntity> aiResults =
                            (List<AIModelTestResultEntity>)
                                    data.getOrDefault("aiModelResults", List.of());
                    if (!aiResults.isEmpty()) {
                        CsvSchema schema =
                                CsvSchema.builder()
                                        .addColumn("jobId")
                                        .addColumn("testCaseId")
                                        .addColumn("testName")
                                        .addColumn("category")
                                        .addColumn("score")
                                        .addColumn("passed")
                                        .addColumn("responseTime")
                                        .addColumn("createdAt")
                                        .build()
                                        .withHeader();
                        csvMapper.writer(schema).writeValues(writer).writeAll(aiResults);
                    }
                }
            }

            writer.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to export as CSV", e);
        }
    }

    // ==================== Excel 导出 ====================

    private byte[] exportToExcel(String testType, Map<String, Object> data) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            @SuppressWarnings("unchecked")
            List<E2ETestResult> e2eResults =
                    (List<E2ETestResult>) data.getOrDefault("e2eResults", List.of());
            @SuppressWarnings("unchecked")
            List<PerformanceTestResult> perfResults =
                    (List<PerformanceTestResult>)
                            data.getOrDefault("performanceResults", List.of());
            @SuppressWarnings("unchecked")
            List<AIModelTestResultEntity> aiResults =
                    (List<AIModelTestResultEntity>) data.getOrDefault("aiModelResults", List.of());

            // E2E Sheet
            if (!e2eResults.isEmpty()) {
                Sheet sheet = workbook.createSheet("E2E测试结果");
                createE2EHeader(sheet);
                for (int i = 0; i < e2eResults.size(); i++) {
                    createE2ERow(sheet, i + 1, e2eResults.get(i));
                }
                autoSizeColumns(sheet, 8);
            }

            // Performance Sheet
            if (!perfResults.isEmpty()) {
                Sheet sheet = workbook.createSheet("性能测试结果");
                createPerformanceHeader(sheet);
                for (int i = 0; i < perfResults.size(); i++) {
                    createPerformanceRow(sheet, i + 1, perfResults.get(i));
                }
                autoSizeColumns(sheet, 10);
            }

            // AI Model Sheet
            if (!aiResults.isEmpty()) {
                Sheet sheet = workbook.createSheet("AI模型测试结果");
                createAIModelHeader(sheet);
                for (int i = 0; i < aiResults.size(); i++) {
                    createAIModelRow(sheet, i + 1, aiResults.get(i));
                }
                autoSizeColumns(sheet, 8);
            }

            // 如果没有数据，创建空 sheet
            if (workbook.getNumberOfSheets() == 0) {
                Sheet sheet = workbook.createSheet("无数据");
                sheet.createRow(0).createCell(0).setCellValue("没有找到测试结果数据");
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to export as Excel", e);
        }
    }

    // ==================== Excel 辅助方法 ====================

    private void createE2EHeader(Sheet sheet) {
        Row row = sheet.createRow(0);
        String[] headers = {"任务ID", "测试名称", "状态", "耗时(ms)", "通过断言", "失败断言", "错误信息", "创建时间"};
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
        }
    }

    private void createE2ERow(Sheet sheet, int rowIndex, E2ETestResult result) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(result.getJobId());
        row.createCell(1).setCellValue(result.getTestName());
        row.createCell(2).setCellValue(result.getStatus());
        row.createCell(3).setCellValue(result.getDurationMs() != null ? result.getDurationMs() : 0);
        row.createCell(4)
                .setCellValue(
                        result.getAssertionsPassed() != null ? result.getAssertionsPassed() : 0);
        row.createCell(5)
                .setCellValue(
                        result.getAssertionsFailed() != null ? result.getAssertionsFailed() : 0);
        row.createCell(6)
                .setCellValue(result.getErrorMessage() != null ? result.getErrorMessage() : "");
        row.createCell(7)
                .setCellValue(
                        result.getCreatedAt() != null
                                ? result.getCreatedAt().format(FORMATTER)
                                : "");
    }

    private void createPerformanceHeader(Sheet sheet) {
        Row row = sheet.createRow(0);
        String[] headers = {
            "任务ID",
            "场景",
            "请求数",
            "成功率(%)",
            "平均响应(ms)",
            "最大响应(ms)",
            "P95(ms)",
            "P99(ms)",
            "开始时间",
            "结束时间"
        };
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
        }
    }

    private void createPerformanceRow(Sheet sheet, int rowIndex, PerformanceTestResult result) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(result.getJobId());
        row.createCell(1).setCellValue(result.getSimulation());
        row.createCell(2).setCellValue(result.getRequests() != null ? result.getRequests() : 0);
        row.createCell(3)
                .setCellValue(
                        result.getSuccessRate() != null
                                ? result.getSuccessRate().doubleValue()
                                : 0);
        row.createCell(4)
                .setCellValue(
                        result.getAvgResponseTime() != null ? result.getAvgResponseTime() : 0);
        row.createCell(5)
                .setCellValue(
                        result.getMaxResponseTime() != null ? result.getMaxResponseTime() : 0);
        row.createCell(6)
                .setCellValue(
                        result.getP95ResponseTime() != null ? result.getP95ResponseTime() : 0);
        row.createCell(7)
                .setCellValue(
                        result.getP99ResponseTime() != null ? result.getP99ResponseTime() : 0);
        row.createCell(8)
                .setCellValue(
                        result.getStartTime() != null
                                ? result.getStartTime().format(FORMATTER)
                                : "");
        row.createCell(9)
                .setCellValue(
                        result.getEndTime() != null ? result.getEndTime().format(FORMATTER) : "");
    }

    private void createAIModelHeader(Sheet sheet) {
        Row row = sheet.createRow(0);
        String[] headers = {"任务ID", "用例ID", "测试名称", "分类", "得分", "是否通过", "响应时间(ms)", "创建时间"};
        for (int i = 0; i < headers.length; i++) {
            row.createCell(i).setCellValue(headers[i]);
        }
    }

    private void createAIModelRow(Sheet sheet, int rowIndex, AIModelTestResultEntity result) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(result.getJobId());
        row.createCell(1).setCellValue(result.getTestCaseId());
        row.createCell(2).setCellValue(result.getTestName());
        row.createCell(3).setCellValue(result.getCategory() != null ? result.getCategory() : "");
        row.createCell(4)
                .setCellValue(result.getScore() != null ? result.getScore().doubleValue() : 0);
        row.createCell(5)
                .setCellValue(result.getPassed() != null && result.getPassed() ? "通过" : "未通过");
        row.createCell(6)
                .setCellValue(result.getResponseTime() != null ? result.getResponseTime() : 0);
        row.createCell(7)
                .setCellValue(
                        result.getCreatedAt() != null
                                ? result.getCreatedAt().format(FORMATTER)
                                : "");
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
