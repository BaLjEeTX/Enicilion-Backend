package com.enicilion.backend.notification.service;

import com.enicilion.backend.notification.dto.TicketNotificationRequest;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

@Slf4j
@Service
public class TicketPdfService {

    private static final Color BG_COLOR = new Color(18, 18, 18);
    private static final Color CARD_COLOR = new Color(30, 30, 30);
    private static final Color BORDER_COLOR = new Color(51, 51, 51);
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color ACCENT_GOLD = new Color(212, 175, 55);
    private static final Color ACCENT_RED = new Color(239, 51, 64);

    public byte[] generateTicketPdf(TicketNotificationRequest data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            // 1. Draw premium dark background
            drawBackground(writer, document);

            // 2. Define premium font styles
            Font brandFont  = new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT_GOLD);
            Font titleFont  = new Font(Font.HELVETICA, 20, Font.BOLD, TEXT_PRIMARY);
            Font labelFont  = new Font(Font.HELVETICA, 8,  Font.NORMAL, TEXT_SECONDARY);
            Font valueFont  = new Font(Font.HELVETICA, 11, Font.BOLD,   TEXT_PRIMARY);
            Font codeFont   = new Font(Font.COURIER,   16, Font.BOLD,   ACCENT_RED);
            Font footerFont = new Font(Font.HELVETICA, 8,  Font.ITALIC, TEXT_SECONDARY);

            // 3. Header title / Branding
            Paragraph brandP = new Paragraph("ENICILION MOTORSCAPE", brandFont);
            brandP.setAlignment(Element.ALIGN_CENTER);
            brandP.setSpacingAfter(5);
            document.add(brandP);

            Paragraph title = new Paragraph(safe(data.getEventName(), "Event Ticket").toUpperCase(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(25);
            document.add(title);

            // 4. Main Card Table
            PdfPTable cardTable = new PdfPTable(1);
            cardTable.setWidthPercentage(95);
            
            PdfPCell cardCell = new PdfPCell();
            cardCell.setBackgroundColor(CARD_COLOR);
            cardCell.setBorder(Rectangle.BOX);
            cardCell.setBorderColor(BORDER_COLOR);
            cardCell.setBorderWidth(1.2f);
            cardCell.setPadding(20f);

            // Inside Card: Split into columns for details (left) and QR code (right)
            PdfPTable innerLayout = new PdfPTable(2);
            innerLayout.setWidthPercentage(100);
            innerLayout.setWidths(new float[]{6f, 4f});

            // Left Column: Detail Rows
            PdfPCell detailsCell = new PdfPCell();
            detailsCell.setBorder(Rectangle.NO_BORDER);
            detailsCell.setBackgroundColor(CARD_COLOR);

            addDetail(detailsCell, "TICKET HOLDER", safe(data.getUserName(), "-"), labelFont, valueFont);
            addDetail(detailsCell, "TICKET TYPE", safe(data.getTierName(), "General Admission").toUpperCase(), labelFont, new Font(Font.HELVETICA, 11, Font.BOLD, ACCENT_GOLD));
            addDetail(detailsCell, "DATE & TIME", safe(data.getEventDate(), "TBD"), labelFont, valueFont);
            addDetail(detailsCell, "VENUE", safe(data.getEventLocation(), "TBD"), labelFont, valueFont);
            addDetail(detailsCell, "ORDER ID", safe(data.getOrderId(), "-"), labelFont, valueFont);
            addDetail(detailsCell, "QUANTITY", String.valueOf(data.getQuantity()), labelFont, valueFont);

            innerLayout.addCell(detailsCell);

            // Right Column: QR Code & Code Text
            PdfPCell qrCell = new PdfPCell();
            qrCell.setBorder(Rectangle.NO_BORDER);
            qrCell.setBackgroundColor(CARD_COLOR);
            qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

            // Generate and wrap QR Code in whitelisted border container for high-contrast scan
            byte[] qrBytes = generateQrCode(data.getTicketCode(), 200);
            Image qr = Image.getInstance(qrBytes);
            qr.scaleToFit(120, 120);
            qr.setAlignment(Image.ALIGN_CENTER);

            PdfPCell imgCell = new PdfPCell(qr);
            imgCell.setBorder(Rectangle.BOX);
            imgCell.setBorderColor(Color.WHITE);
            imgCell.setBorderWidth(4f);
            imgCell.setBackgroundColor(Color.WHITE);
            imgCell.setPadding(6f);
            imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            imgCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

            PdfPTable qrWrapperTable = new PdfPTable(1);
            qrWrapperTable.setWidthPercentage(85);
            qrWrapperTable.addCell(imgCell);
            qrCell.addElement(qrWrapperTable);

            // Code Text below QR Code
            Paragraph codeP = new Paragraph(safe(data.getTicketCode(), ""), codeFont);
            codeP.setAlignment(Element.ALIGN_CENTER);
            codeP.setSpacingBefore(12);
            qrCell.addElement(codeP);

            innerLayout.addCell(qrCell);
            cardCell.addElement(innerLayout);
            cardTable.addCell(cardCell);
            document.add(cardTable);

            // 5. Footer info
            Paragraph footer = new Paragraph("Present this barcode at the venue entry. Do not share this QR code.\nValid for one scan only.", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(40);
            document.add(footer);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[TicketPdf] Failed to generate PDF for ticketCode={} — {}", data.getTicketCode(), e.getMessage(), e);
            return new byte[0];
        }
    }

    private void drawBackground(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContentUnder();
        cb.setColorFill(BG_COLOR);
        cb.rectangle(0, 0, document.getPageSize().getWidth(), document.getPageSize().getHeight());
        cb.fill();
    }

    private void addDetail(PdfPCell container, String label, String value, Font labelFont, Font valueFont) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", labelFont));
        p.add(new Chunk(value != null ? value : "", valueFont));
        p.setSpacingAfter(10f);
        container.addElement(p);
    }

    private byte[] generateQrCode(String content, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    private String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
