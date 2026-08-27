package com.merchtyl.platform.billing;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PlatformInvoicePdfService {
    private final PlatformBillingService billing;

    public PlatformInvoicePdfService(PlatformBillingService billing) {
        this.billing = billing;
    }

    public byte[] generate(java.util.UUID invoiceId) {
        BillingDtos.InvoiceResponse invoice = billing.invoice(invoiceId);
        BillingDtos.BillingSettingsResponse settings = billing.settings();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Paragraph heading = new Paragraph(settings.legalName() == null ? "MERCHTYL" : settings.legalName(), title);
        heading.setAlignment(Element.ALIGN_CENTER);
        document.add(heading);
        document.add(new Paragraph("Invoice " + invoice.invoiceNumber()));
        document.add(new Paragraph("Issue date: " + invoice.issueDate() + "    Due date: " + invoice.dueDate()));
        document.add(new Paragraph("Status: " + invoice.status()));
        document.add(new Paragraph("From: " + value(settings.billingAddress())));
        document.add(new Paragraph("Bill to: " + invoice.merchantName() + "\n" + value(invoice.billingAddress())));
        document.add(new Paragraph("Billing period: " + invoice.billingPeriodStart() + " to " + invoice.billingPeriodEnd()));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(new float[]{5, 1, 2, 2});
        table.setWidthPercentage(100);
        table.addCell("Description"); table.addCell("Qty"); table.addCell("Rate"); table.addCell("Amount");
        for (BillingDtos.InvoiceLine line : invoice.lines()) {
            table.addCell(line.description());
            table.addCell(line.quantity().toPlainString());
            table.addCell(line.unitPrice().toPlainString());
            table.addCell(line.lineTotal().toPlainString());
        }
        document.add(table);
        document.add(new Paragraph("Subtotal: " + invoice.subtotal() + " " + invoice.currency()));
        document.add(new Paragraph("Discount: " + invoice.discountTotal() + " " + invoice.currency()));
        document.add(new Paragraph((invoice.taxLabel() == null ? "Tax" : invoice.taxLabel()) + ": " + invoice.taxTotal() + " " + invoice.currency()));
        document.add(new Paragraph("TOTAL: " + invoice.total() + " " + invoice.currency(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
        if (settings.paymentInstructions() != null) document.add(new Paragraph("Payment instructions: " + settings.paymentInstructions()));
        if (settings.invoiceFooter() != null) document.add(new Paragraph(settings.invoiceFooter()));
        document.close();
        return output.toByteArray();
    }

    private static String value(String value) { return value == null ? "" : value; }
}
