package com.enicilion.backend.tickets.service;

import com.enicilion.backend.tickets.entity.SpectatorTicket;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Collections;

@Service
@Slf4j
public class PdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(java.time.ZoneId.of("Asia/Kolkata"));

    private static final Color BG_COLOR = new Color(18, 18, 18);
    private static final Color CARD_COLOR = new Color(30, 30, 30);
    private static final Color BORDER_COLOR = new Color(51, 51, 51);
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color ACCENT_COLOR = new Color(212, 175, 55);

    public byte[] generateTicketPdf(SpectatorTicket ticket) {
        return generateTicketsPdf(Collections.singletonList(ticket));
    }

    public byte[] generateTicketsPdf(List<SpectatorTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return new byte[0];
        }
        log.info("Generating professional dark theme PDF for {} tickets", tickets.size());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document document = new Document(PageSize.A4.rotate(), 40, 40, 40, 40);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            Font brandFont = new Font(Font.HELVETICA, 24, Font.BOLD, TEXT_PRIMARY);
            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, TEXT_PRIMARY);
            Font labelFont = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_SECONDARY);
            Font valueFont = new Font(Font.HELVETICA, 11, Font.BOLD, TEXT_PRIMARY);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, TEXT_PRIMARY);
            Font rowFont = new Font(Font.HELVETICA, 10, Font.NORMAL, TEXT_PRIMARY);
            Font footerFont = new Font(Font.HELVETICA, 9, Font.ITALIC, TEXT_SECONDARY);
            Font accentFont = new Font(Font.HELVETICA, 11, Font.BOLD, ACCENT_COLOR);

            SpectatorTicket firstTicket = tickets.get(0);
            com.enicilion.backend.payments.entity.Payment payment = firstTicket.getPayment();

            if (payment != null) {
                drawBackground(writer, document);

                PdfPTable headerTable = new PdfPTable(2);
                headerTable.setWidthPercentage(100);
                headerTable.setWidths(new float[]{1f, 1f});

                PdfPCell logoCell = new PdfPCell();
                logoCell.setBorder(Rectangle.NO_BORDER);
                logoCell.addElement(new Paragraph("ENICILION", brandFont));
                logoCell.addElement(new Paragraph("TRANSACTION RECEIPT", labelFont));
                headerTable.addCell(logoCell);

                PdfPCell statusCell = new PdfPCell();
                statusCell.setBorder(Rectangle.NO_BORDER);
                statusCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                Paragraph statusP = new Paragraph("PAYMENT SUCCESSFUL", new Font(Font.HELVETICA, 12, Font.BOLD, new Color(46, 204, 113)));
                statusP.setAlignment(Element.ALIGN_RIGHT);
                statusCell.addElement(statusP);
                headerTable.addCell(statusCell);
                headerTable.setSpacingAfter(20f);
                document.add(headerTable);

                drawSeparator(document, ACCENT_COLOR);

                PdfPTable detailsTable = new PdfPTable(2);
                detailsTable.setWidthPercentage(100);
                detailsTable.setWidths(new float[]{1f, 1f});
                detailsTable.setSpacingAfter(30f);

                PdfPCell leftCell = createCardCell();
                leftCell.addElement(new Paragraph("CUSTOMER DETAILS", titleFont));
                leftCell.addElement(new Paragraph(" ", new Font(Font.HELVETICA, 5)));
                addDetailParagraph(leftCell, "NAME: ", payment.getUser().getFullName(), labelFont, valueFont);
                addDetailParagraph(leftCell, "EMAIL: ", payment.getUser().getEmail(), labelFont, valueFont);
                addDetailParagraph(leftCell, "PHONE: ", payment.getUser().getWhatsapp() != null ? payment.getUser().getWhatsapp() : "N/A", labelFont, valueFont);
                detailsTable.addCell(leftCell);

                PdfPCell rightCell = createCardCell();
                rightCell.addElement(new Paragraph("TRANSACTION DETAILS", titleFont));
                rightCell.addElement(new Paragraph(" ", new Font(Font.HELVETICA, 5)));
                addDetailParagraph(rightCell, "TRANSACTION ID: ", payment.getId().toString(), labelFont, valueFont);
                addDetailParagraph(rightCell, "PAID DATE: ", payment.getPaidAt() != null ? payment.getPaidAt().format(DATE_FORMATTER) : "N/A", labelFont, valueFont);
                addDetailParagraph(rightCell, "METHOD: ", payment.getProvider().name(), labelFont, valueFont);
                addDetailParagraph(rightCell, "TOTAL PAID: ", payment.getAmount() + " " + payment.getCurrency(), labelFont, accentFont);
                detailsTable.addCell(rightCell);

                document.add(detailsTable);

                Paragraph listHeader = new Paragraph("TICKETS ISSUED", titleFont);
                listHeader.setSpacingAfter(10f);
                document.add(listHeader);

                java.util.Map<com.enicilion.backend.tickets.entity.TicketTier, Integer> tierCounts = new java.util.LinkedHashMap<>();
                for (SpectatorTicket t : tickets) {
                    com.enicilion.backend.tickets.entity.TicketTier tier = t.getTier();
                    tierCounts.put(tier, tierCounts.getOrDefault(tier, 0) + 1);
                }

                PdfPTable listTable = new PdfPTable(4);
                listTable.setWidthPercentage(100);
                listTable.setWidths(new float[]{3f, 1.5f, 1.5f, 2f});
                
                addTableHeader(listTable, "TIER CATEGORY", headerFont);
                addTableHeader(listTable, "UNIT PRICE", headerFont);
                addTableHeader(listTable, "QUANTITY", headerFont);
                addTableHeader(listTable, "SUBTOTAL", headerFont);

                for (java.util.Map.Entry<com.enicilion.backend.tickets.entity.TicketTier, Integer> entry : tierCounts.entrySet()) {
                    com.enicilion.backend.tickets.entity.TicketTier tier = entry.getKey();
                    int qty = entry.getValue();
                    java.math.BigDecimal unitPrice = tier.getPrice();
                    java.math.BigDecimal subtotal = unitPrice.multiply(java.math.BigDecimal.valueOf(qty));
                    
                    addTableCell(listTable, tier.getName().toUpperCase(), rowFont);
                    addTableCell(listTable, "INR " + unitPrice.toString(), rowFont);
                    addTableCell(listTable, String.valueOf(qty), rowFont);
                    addTableCell(listTable, "INR " + subtotal.toString(), rowFont);
                }
                listTable.setSpacingAfter(10f);
                document.add(listTable);

                // Add Terms & Conditions Title
                Paragraph termsTitle = new Paragraph("TERMS & CONDITIONS", new Font(Font.HELVETICA, 7, Font.BOLD, ACCENT_COLOR));
                termsTitle.setSpacingBefore(10f);
                termsTitle.setSpacingAfter(3f);
                document.add(termsTitle);

                // Add Terms & Conditions Content
                Font termsFont = new Font(Font.HELVETICA, 5.5f, Font.NORMAL, TEXT_SECONDARY);
                Paragraph termsPara = new Paragraph(
                    "By purchasing, downloading, presenting, or using a Motorscape 2026 ticket, the attendee agrees to these terms:\n" +
                    "1. TICKET VALIDITY: Entry allowed only with valid QR ticket. Valid for one person and one-time entry only. Fake, duplicate, or altered tickets may be rejected. Do not share QR code.\n" +
                    "2. ENTRY & VERIFICATION: Present at gate. Screenshot or printed copy accepted if scannable. Organizer may verify identity, age, or booking details and refuse entry for safety/fraud.\n" +
                    "3. REFUNDS & CANCELLATION: Tickets are non-refundable unless cancelled by the organizer. Booking, platform, and payment gateway charges may be non-refundable. Rescheduled tickets remain valid.\n" +
                    "4. TRANSFER: Non-transferable unless supported. Unauthorized resale/duplication invalidates the ticket.\n" +
                    "5. SECURITY: Subject to venue security checks. Outside alcohol, weapons, fireworks, dangerous objects, or behavior lead to removal without refund.\n" +
                    "6. F&B RULES: Depends on tier. Alcohol subject to legal age check. Service can be refused.\n" +
                    "7. EVENT CHANGES: Schedule, zones, performers, or activities may change without notice.\n" +
                    "8. MEDIA CONSENT: Consents to appear in photography/videography for event coverage and marketing.\n" +
                    "9. LIABILITY & BELONGINGS: Responsible for personal items. Motorsport environments include sound, vehicles, crowds; entry is at own risk.\n" +
                    "10. SUPPORT: Contact Enicilion at enicilion.com.",
                    termsFont
                );
                termsPara.setLeading(7.5f);
                termsPara.setSpacingAfter(10f);
                document.add(termsPara);

                Paragraph receiptFooter = new Paragraph("Present the QR codes on the subsequent pages at the venue entry.", footerFont);
                receiptFooter.setAlignment(Element.ALIGN_CENTER);
                document.add(receiptFooter);
            }

            for (int i = 0; i < tickets.size(); i++) {
                SpectatorTicket ticket = tickets.get(i);
                
                if (payment != null || i > 0) {
                    document.newPage();
                }

                drawBackground(writer, document);

                PdfPTable ticketTable = new PdfPTable(3);
                ticketTable.setWidthPercentage(100);
                ticketTable.setWidths(new float[]{1.5f, 5.5f, 2.5f});
                ticketTable.setSpacingBefore(40f);

                PdfPCell brandCell = createCardCell();
                brandCell.setBackgroundColor(CARD_COLOR);
                brandCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                brandCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                
                String tierName = ticket.getTier() != null ? ticket.getTier().getName() : "General Admission";
                String passText = "PASS";
                if (tierName.toLowerCase().contains("vip")) {
                    passText = "VIP\nPASS";
                } else if (tierName.toLowerCase().contains("general")) {
                    passText = "GENERAL\nPASS";
                } else if (tierName.toLowerCase().contains("food") || tierName.toLowerCase().contains("beverage") || tierName.toLowerCase().contains("coupon")) {
                    passText = "F&B\nPASS";
                } else {
                    String firstWord = tierName.split("\\s+")[0].toUpperCase();
                    passText = firstWord + "\nPASS";
                }
                
                Paragraph brandP = new Paragraph("ENICILION\n" + passText, new Font(Font.HELVETICA, 16, Font.BOLD, ACCENT_COLOR));
                brandP.setAlignment(Element.ALIGN_CENTER);
                brandCell.addElement(brandP);
                ticketTable.addCell(brandCell);

                PdfPCell detailsCell = createCardCell();
                detailsCell.setBackgroundColor(BG_COLOR);
                detailsCell.setPadding(20f);
                
                Paragraph eventP = new Paragraph(ticket.getEvent().getName().toUpperCase(), new Font(Font.HELVETICA, 20, Font.BOLD, TEXT_PRIMARY));
                eventP.setSpacingAfter(10f);
                detailsCell.addElement(eventP);

                PdfPTable sepInner = new PdfPTable(1);
                sepInner.setWidthPercentage(100);
                PdfPCell sepInnerCell = new PdfPCell();
                sepInnerCell.setBorder(Rectangle.BOTTOM);
                sepInnerCell.setBorderColor(BORDER_COLOR);
                sepInnerCell.setBorderWidth(1f);
                sepInner.addCell(sepInnerCell);
                sepInner.setSpacingAfter(15f);
                detailsCell.addElement(sepInner);

                addDetailParagraph(detailsCell, "TICKET HOLDER", "", labelFont, valueFont);
                Paragraph nameP = new Paragraph(ticket.getUser().getFullName().toUpperCase(), new Font(Font.HELVETICA, 16, Font.BOLD, TEXT_PRIMARY));
                nameP.setSpacingAfter(15f);
                detailsCell.addElement(nameP);

                PdfPTable grid = new PdfPTable(2);
                grid.setWidthPercentage(100);
                
                PdfPCell dateCell = new PdfPCell();
                dateCell.setBorder(Rectangle.NO_BORDER);
                dateCell.addElement(new Paragraph("DATE & TIME", labelFont));
                dateCell.addElement(new Paragraph(ticket.getEvent().getEventDate().format(DATE_FORMATTER), valueFont));
                grid.addCell(dateCell);

                PdfPCell tierCell = new PdfPCell();
                tierCell.setBorder(Rectangle.NO_BORDER);
                tierCell.addElement(new Paragraph("CATEGORY", labelFont));
                Paragraph tP = new Paragraph(ticket.getTier().getName().toUpperCase(), accentFont);
                tierCell.addElement(tP);
                grid.addCell(tierCell);

                grid.setSpacingAfter(15f);
                detailsCell.addElement(grid);

                addDetailParagraph(detailsCell, "VENUE", ticket.getEvent().getLocation(), labelFont, valueFont);
                
                ticketTable.addCell(detailsCell);

                PdfPCell qrCell = createCardCell();
                qrCell.setBackgroundColor(CARD_COLOR);
                qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                qrCell.setPadding(15f);

                byte[] qrCodeBytes = generateQrCodePng(ticket.getTicketCode());
                Image qrImage = Image.getInstance(qrCodeBytes);
                qrImage.setAlignment(Element.ALIGN_CENTER);
                qrImage.scaleAbsolute(130f, 130f);
                PdfPCell imgCell = new PdfPCell(qrImage);
                imgCell.setBorder(Rectangle.BOX);
                imgCell.setBorderColor(Color.WHITE);
                imgCell.setBorderWidth(4f);
                imgCell.setBackgroundColor(Color.WHITE);
                imgCell.setPadding(8f);
                imgCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                imgCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                
                PdfPTable imgTable = new PdfPTable(1);
                imgTable.setWidthPercentage(100);
                imgTable.addCell(imgCell);
                
                qrCell.addElement(imgTable);
                
                Paragraph codeP = new Paragraph(ticket.getTicketCode(), new Font(Font.HELVETICA, 10, Font.BOLD, TEXT_PRIMARY));
                codeP.setAlignment(Element.ALIGN_CENTER);
                codeP.setSpacingBefore(10f);
                qrCell.addElement(codeP);

                ticketTable.addCell(qrCell);

                document.add(ticketTable);

                Paragraph footer = new Paragraph("By using this ticket, you agree to the Terms & Conditions. Do not share your QR code.", footerFont);
                footer.setAlignment(Element.ALIGN_CENTER);
                footer.setSpacingBefore(60f);
                document.add(footer);
            }

            document.close();

        } catch (Exception e) {
            log.error("Failed to construct OpenPDF document", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }

    private void drawBackground(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContentUnder();
        cb.setColorFill(BG_COLOR);
        cb.rectangle(0, 0, document.getPageSize().getWidth(), document.getPageSize().getHeight());
        cb.fill();
    }

    private void drawSeparator(Document document, Color color) throws DocumentException {
        PdfPTable separator = new PdfPTable(1);
        separator.setWidthPercentage(100);
        PdfPCell sepCell = new PdfPCell();
        sepCell.setFixedHeight(2f);
        sepCell.setBackgroundColor(color);
        sepCell.setBorder(Rectangle.NO_BORDER);
        separator.addCell(sepCell);
        separator.setSpacingAfter(20f);
        document.add(separator);
    }

    private PdfPCell createCardCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(CARD_COLOR);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(1f);
        cell.setPadding(15f);
        return cell;
    }

    private void addDetailParagraph(PdfPCell container, String label, String value, Font labelFont, Font valueFont) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label, labelFont));
        if (value != null && !value.isEmpty()) {
            p.add(new Chunk(" " + value, valueFont));
        }
        p.setSpacingAfter(5f);
        container.addElement(p);
    }

    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(CARD_COLOR);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(BG_COLOR);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private byte[] generateQrCodePng(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 250, 250, hints);
            
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate QR Code image", e);
            throw new RuntimeException("QR Code generation failed", e);
        }
    }
}
