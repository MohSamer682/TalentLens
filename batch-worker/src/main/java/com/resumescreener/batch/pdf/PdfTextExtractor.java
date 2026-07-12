package com.resumescreener.batch.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
public class PdfTextExtractor {

    public String extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return cleanText(text);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract PDF text: " + e.getMessage());
        }
    }

    private String cleanText(String text) {
        return text.replaceAll("\\s+", " ")
                .replaceAll("(?m)^\\s*\\d+\\r?\\n", "")
                .trim();
    }
}
