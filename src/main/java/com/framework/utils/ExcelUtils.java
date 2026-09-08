package com.framework.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Reads Excel (.xlsx) test data into a list of row-maps keyed by header, so DataProviders
 * don't need to know column indices.
 */
public final class ExcelUtils {

    private ExcelUtils() {
    }

    public static List<Map<String, String>> readSheetAsMaps(String filePath, String sheetName) {
        try (InputStream is = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName + " in " + filePath);
            }

            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell));
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    rowMap.put(headers.get(c), cell == null ? "" : formatter.formatCellValue(cell));
                }
                rows.add(rowMap);
            }
            return rows;

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Excel file: " + filePath, e);
        }
    }
}
