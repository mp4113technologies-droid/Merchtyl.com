package com.merchtyl.eod;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
class EndOfDayPdfRendererTest {
    @Test
    void rendersBrandedPaginatedManagementAndOperationalReport() throws Exception {
        EndOfDayReportResponse report = reportFixture();

        byte[] pdf = EndOfDayPdfRenderer.render(report, "America/St_Johns");
        PdfReader reader = new PdfReader(pdf);

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(2);
        assertThat(reader.getInfo().get("Producer")).isNotBlank();
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        StringBuilder detailText = new StringBuilder();
        for (int page = 2; page <= reader.getNumberOfPages(); page++) {
            detailText.append(new PdfTextExtractor(reader).getTextFromPage(page));
        }
        assertThat(text).contains("Initial Opening Cash", "Cash Before Final Settlement",
                "Till Sweeps / Cash Removed", "Cash Retained in Tills", "740.00", "260.00");
        assertThat(detailText.toString()).contains("REGISTER RECONCILIATION", "REGISTER SESSION DETAIL");
        reader.close();
    }

    @Test
    void csvAndPdfUseTheSamePhysicalRegisterCashSnapshot() throws Exception {
        EndOfDayReportResponse report = reportFixture();
        Method csv = BusinessDayService.class.getDeclaredMethod("csv", EndOfDayReportResponse.class);
        csv.setAccessible(true);

        String content = (String) csv.invoke(null, report);

        assertThat(content).contains("initialOpeningCash,740.00")
                .contains("cashBeforeFinalSettlement,1000.00")
                .contains("cashBeforeSettlement,1000.00")
                .contains("cashRemovedFromTills,260.00")
                .contains("tillSweepAmount,260.00")
                .contains("cashRetainedInTills,740.00")
                .contains("expectedCashInTills,740.00")
                .contains("registerId,registerCode,registerName,sessionCount,initialFloat,targetFloat")
                .contains("registerSessionId,registerId,registerCode,cashier,openedAt,closedAt,sessionOpeningBalance");
    }

    private static EndOfDayReportResponse reportFixture() throws Exception {
        Constructor<?> constructor = EndOfDayReportResponse.class.getDeclaredConstructors()[0];
        Object[] values = new Object[constructor.getParameterCount()];
        java.lang.reflect.Parameter[] parameters = constructor.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            Class<?> type = parameters[index].getType();
            String name = parameters[index].getName();
            values[index] = type == UUID.class ? UUID.randomUUID()
                    : type == String.class ? switch (name) {
                        case "storeCode" -> "STR001";
                        case "storeName" -> "Sweet Shop - Stephenville";
                        case "reportNumber" -> "STR001-2026-09-27-R1";
                        case "generatedByName" -> "Shashikant Patel";
                        case "currencyCode" -> "CAD";
                        default -> "";
                    }
                    : type == LocalDate.class ? LocalDate.of(2026, 9, 27)
                    : type == Instant.class ? Instant.parse("2026-09-27T23:34:00Z")
                    : type == BusinessDayStatus.class ? BusinessDayStatus.CLOSED
                    : type == BigDecimal.class ? switch (name) {
                        case "initialOpeningCash", "cashRetainedInTills", "expectedCash" -> new BigDecimal("740.00");
                        case "cashBeforeFinalSettlement", "countedCash" -> new BigDecimal("1000.00");
                        case "cashRemovedFromTills" -> new BigDecimal("260.00");
                        default -> BigDecimal.ZERO.setScale(2);
                    }
                    : type == long.class ? 0L
                    : type == int.class ? (name.equals("revision") ? 1 : 0)
                    : List.class.isAssignableFrom(type) ? List.of()
                    : null;
        }
        return (EndOfDayReportResponse) constructor.newInstance(values);
    }
}
