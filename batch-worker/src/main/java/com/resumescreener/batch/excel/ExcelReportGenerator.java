package com.resumescreener.batch.excel;

import com.resumescreener.common.entity.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class ExcelReportGenerator {

    public byte[] generate(List<CandidateScore> scores, ScoringWeights weights) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            createRankingSheet(workbook, scores, weights);
            createSkillsMatrixSheet(workbook, scores);
            createJustificationsSheet(workbook, scores);
            createConfigSheet(workbook, weights);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel: " + e.getMessage());
        }
    }

    private void createRankingSheet(XSSFWorkbook workbook, List<CandidateScore> scores, ScoringWeights weights) {
        XSSFSheet sheet = workbook.createSheet("Ranking Summary");
        String[] headers = {"Rank", "Name", "Email", "Score", "Technical", "Experience", "Education", "Soft Skills", "Culture Fit"};
        
        XSSFCellStyle green = workbook.createCellStyle();
        green.setFillForegroundColor(new XSSFColor(new java.awt.Color(220, 252, 231), null));
        green.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        
        int row = 0;
        XSSFRow headerRow = sheet.createRow(row++);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        for (CandidateScore score : scores) {
            XSSFRow dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(score.getRank());
            dataRow.createCell(1).setCellValue(score.getCandidate().getName());
            dataRow.createCell(2).setCellValue(score.getCandidate().getEmail());
            dataRow.createCell(3).setCellValue(score.getCompositeScore());
            dataRow.createCell(4).setCellValue(score.getTechnicalSkills());
            dataRow.createCell(5).setCellValue(score.getExperience());
            dataRow.createCell(6).setCellValue(score.getEducation());
            dataRow.createCell(7).setCellValue(score.getSoftSkills());
            dataRow.createCell(8).setCellValue(score.getCultureFit());

            if (score.getRank() != null && score.getRank() == 1) {
                for (int i = 0; i < headers.length; i++) {
                    if (dataRow.getCell(i) != null) dataRow.getCell(i).setCellStyle(green);
                }
            }
        }
    }

    private void createSkillsMatrixSheet(XSSFWorkbook workbook, List<CandidateScore> scores) {
        XSSFSheet sheet = workbook.createSheet("Skills Matrix");
        Set<String> allSkills = new TreeSet<>();
        scores.forEach(s -> { if (s.getSkills() != null) allSkills.addAll(s.getSkills()); });
        
        XSSFRow headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Candidate");
        int col = 1;
        for (String skill : allSkills) {
            headerRow.createCell(col++).setCellValue(skill);
        }

        int row = 1;
        for (CandidateScore score : scores) {
            XSSFRow dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(score.getCandidate().getName());
            col = 1;
            List<String> candidateSkills = score.getSkills();
            for (String skill : allSkills) {
                dataRow.createCell(col++).setCellValue(candidateSkills != null && candidateSkills.contains(skill) ? "Y" : "");
            }
        }
    }

    private void createJustificationsSheet(XSSFWorkbook workbook, List<CandidateScore> scores) {
        XSSFSheet sheet = workbook.createSheet("Justifications");
        String[] headers = {"Rank", "Name", "Justification", "Strengths", "Gaps"};
        
        XSSFRow headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        int row = 1;
        for (CandidateScore score : scores) {
            XSSFRow dataRow = sheet.createRow(row++);
            dataRow.createCell(0).setCellValue(score.getRank() != null ? score.getRank() : 0);
            dataRow.createCell(1).setCellValue(score.getCandidate().getName());
            dataRow.createCell(2).setCellValue(score.getJustification());
            dataRow.createCell(3).setCellValue(score.getStrengths());
            dataRow.createCell(4).setCellValue(score.getGaps());
        }
    }

    private void createConfigSheet(XSSFWorkbook workbook, ScoringWeights weights) {
        XSSFSheet sheet = workbook.createSheet("Session Config");
        XSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("Weight");
        header.createCell(1).setCellValue("Value");

        if (weights == null) {
            XSSFRow r = sheet.createRow(1);
            r.createCell(0).setCellValue("No custom weights — defaults used");
            return;
        }

        int rowNum = 1;
        String[][] entries = {
            {"Technical Skills", String.valueOf(weights.getTechnicalSkills())},
            {"Experience",       String.valueOf(weights.getExperience())},
            {"Education",        String.valueOf(weights.getEducation())},
            {"Soft Skills",      String.valueOf(weights.getSoftSkills())},
            {"Culture Fit",      String.valueOf(weights.getCultureFit())}
        };
        for (String[] entry : entries) {
            XSSFRow r = sheet.createRow(rowNum++);
            r.createCell(0).setCellValue(entry[0]);
            r.createCell(1).setCellValue(entry[1]);
        }
    }
}
