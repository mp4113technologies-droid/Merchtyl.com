package com.merchtyl.receipts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.sales.Payment;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ReceiptService {
    private static final String BRAND_NAME = "Merchtyl";
    private static final String BRAND_TAGLINE = "Point of sale receipt";

    private final ReceiptRepository receiptRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ReceiptNumberService receiptNumberService;
    private final Clock clock;
    @Autowired
    private StoreAccessService storeAccessService;

    @Autowired
    public ReceiptService(
            ReceiptRepository receiptRepository,
            SaleRepository saleRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            ReceiptNumberService receiptNumberService) {
        this(receiptRepository, saleRepository, userRepository, auditService, objectMapper, receiptNumberService, Clock.systemUTC());
    }

    ReceiptService(
            ReceiptRepository receiptRepository,
            SaleRepository saleRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            ReceiptNumberService receiptNumberService,
            Clock clock) {
        this.receiptRepository = receiptRepository;
        this.saleRepository = saleRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.receiptNumberService = receiptNumberService;
        this.clock = clock;
    }

    @Transactional
    public ReceiptResponse getForSale(UUID saleId, Authentication authentication) {
        requireSaleAccess(saleId, authentication);
        Receipt receipt = receiptRepository.findBySale_Id(saleId)
                .orElseGet(() -> createReceipt(saleId, authentication));
        return response(receipt);
    }

    @Transactional
    public ReceiptResponse reprintForSale(UUID saleId, Authentication authentication) {
        requireSaleAccess(saleId, authentication);
        User actor = actor(authentication);
        Receipt receipt = receiptRepository.findForUpdateBySale_Id(saleId)
                .orElseGet(() -> createReceipt(saleId, authentication));
        receipt.markReprinted(Instant.now(clock));
        Receipt saved = receiptRepository.saveAndFlush(receipt);
        ReceiptResponse response = response(saved);
        audit(actor, AuditAction.RECEIPT_REPRINTED, saved, response, "Receipt reprinted");
        return response;
    }

    @Transactional(readOnly = true)
    public ReceiptResponse findByReceiptNumber(String receiptNumber, Authentication authentication) {
        String normalized = receiptNumber == null ? "" : receiptNumber.trim();
        Receipt receipt = receiptRepository.findByReceiptNumberIgnoreCase(normalized)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));
        requireSaleAccess(receipt.getSale().getId(), authentication);
        return response(receipt);
    }

    private Receipt createReceipt(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
        if (sale.getStatus() != SaleStatus.COMPLETED
                && sale.getStatus() != SaleStatus.PARTIALLY_REFUNDED
                && sale.getStatus() != SaleStatus.REFUNDED) {
            throw new ConflictException("Receipt can only be generated for a completed sale");
        }
        ReceiptDocumentDto document = buildDocument(sale);
        Receipt receipt = new Receipt(sale, document.receiptNumber(), sale.getCompletedAt(), serialize(document));
        Receipt saved = receiptRepository.saveAndFlush(receipt);
        ReceiptResponse response = ReceiptResponse.from(saved, document);
        audit(actor, AuditAction.RECEIPT_GENERATED, saved, response, "Receipt generated");
        return saved;
    }

    private void requireSaleAccess(UUID saleId, Authentication authentication) {
        if (storeAccessService == null) {
            return;
        }
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
        storeAccessService.requireStoreAccess(authentication, sale.getStore().getId());
    }

    private ReceiptDocumentDto buildDocument(Sale sale) {
        BigDecimal cashTendered = money(sale.getPayments().stream()
                .map(Payment::getCashTendered)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal changeDue = money(sale.getPayments().stream()
                .map(Payment::getChangeDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal cashRoundingAdjustment = money(sale.getPayments().stream()
                .map(Payment::getCashRoundingAdjustment)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal cashTotal = money(sale.getTotalAmount().add(cashRoundingAdjustment));
        BigDecimal taxableAmount = money(sale.getItems().stream()
                .filter(item -> item.getEstimatedTaxAmount().signum() > 0)
                .map(item -> item.getLineSubtotal().subtract(item.getDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<ReceiptTaxSummaryDto> taxSummaries = receiptTaxSummaries(sale, taxableAmount);

        return new ReceiptDocumentDto(
                BRAND_NAME,
                BRAND_TAGLINE,
                new ReceiptStoreDto(
                        sale.getStore().getId(),
                        sale.getStore().getCode(),
                        sale.getStore().getName(),
                        sale.getStore().getLegalName(),
                        sale.getStore().getAddress(),
                        sale.getStore().getPhone(),
                        sale.getStore().getEmail()),
                new ReceiptRegisterDto(
                        sale.getRegister().getId(),
                        sale.getRegister().getCode(),
                        sale.getRegister().getName()),
                new ReceiptCashierDto(
                        sale.getCompletedBy().getId(),
                        sale.getCompletedBy().getDisplayName(),
                        sale.getCompletedBy().getEmail()),
                receiptNumberService.nextNumber(),
                sale.getId(),
                sale.getId().toString(),
                sale.getBusinessDate(),
                sale.getCompletedAt(),
                sale.getCurrencyCode(),
                sale.getItems().stream()
                        .sorted(Comparator.comparingInt(SaleItem::getLineNumber))
                        .map(this::item)
                        .toList(),
                sale.getSubtotalAmount(),
                sale.getDiscountAmount(),
                taxSummaries,
                sale.getEstimatedTaxAmount(),
                sale.getTotalAmount(),
                cashRoundingAdjustment,
                cashTotal,
                sale.getPayments().stream()
                        .sorted(Comparator.comparing(Payment::getCompletedAt))
                        .map(this::payment)
                        .toList(),
                cashTendered,
                changeDue,
                sale.getFoodOrderToken(),
                sale.getDiscountName());
    }

    private List<ReceiptTaxSummaryDto> receiptTaxSummaries(Sale sale, BigDecimal fallbackTaxableAmount) {
        if (sale.getEstimatedTaxAmount().signum() == 0) return List.of();
        var custom = sale.getItems().stream()
                .filter(item -> item.getEstimatedTaxAmount().signum() != 0 && "CUSTOM_PERCENTAGE".equals(item.getTaxCategoryTypeSnapshot()))
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getTaxCategoryCodeSnapshot() + "\u0000" + item.getTaxCategoryNameSnapshot()
                                + "\u0000" + item.getTaxRateSnapshot(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        BigDecimal customTax = custom.values().stream().flatMap(java.util.Collection::stream)
                .map(SaleItem::getEstimatedTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ReceiptTaxSummaryDto> result = new java.util.ArrayList<>();
        BigDecimal standardTax = money(sale.getEstimatedTaxAmount().subtract(customTax));
        if (standardTax.signum() != 0) result.add(new ReceiptTaxSummaryDto("TAX", "Sales tax", fallbackTaxableAmount, standardTax));
        custom.forEach((key, items) -> {
            SaleItem first = items.getFirst();
            BigDecimal base = money(items.stream().map(item -> item.getTaxableAmountSnapshot() == null ? BigDecimal.ZERO : item.getTaxableAmountSnapshot()).reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal tax = money(items.stream().map(SaleItem::getEstimatedTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            String label = first.getTaxCategoryNameSnapshot() + " " + first.getTaxRateSnapshot().stripTrailingZeros().toPlainString() + "%";
            result.add(new ReceiptTaxSummaryDto(first.getTaxCategoryCodeSnapshot(), label, base, tax));
        });
        return List.copyOf(result);
    }

    private ReceiptItemDto item(SaleItem item) {
        return new ReceiptItemDto(
                item.getId(),
                item.getProduct() == null ? null : item.getProduct().getId(),
                item.getLineNumber(),
                item.getProductName(),
                item.getQuantity(),
                item.getLineSubtotal().divide(item.getQuantity(), 4, RoundingMode.HALF_UP),
                item.getCompletedProductCost(),
                item.getCompletedProductPrice(),
                item.getCompletedProductCapabilities(),
                item.getDiscountAmount(),
                item.getLineSubtotal(),
                item.getEstimatedTaxAmount(),
                item.getLineTotal());
    }

    private ReceiptPaymentDto payment(Payment payment) {
        return new ReceiptPaymentDto(
                payment.getId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getMethod() == PaymentMethod.CASH ? payment.getCashTendered() : null,
                payment.getCashRoundingAdjustment(),
                payment.getCashSettlementAmount(),
                payment.getChangeDue(),
                payment.getReference(),
                payment.getCompletedAt());
    }

    private ReceiptResponse response(Receipt receipt) {
        return ReceiptResponse.from(receipt, deserialize(receipt.getDocument().getDocumentJson()));
    }

    private String serialize(ReceiptDocumentDto document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize receipt document", exception);
        }
    }

    private ReceiptDocumentDto deserialize(String documentJson) {
        try {
            return objectMapper.readValue(documentJson, ReceiptDocumentDto.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize receipt document", exception);
        }
    }

    private User actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Authenticated user is required");
        }
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ForbiddenOperationException("Authenticated user is required"));
        if (!user.isEnabled() || user.isLocked()) {
            throw new ForbiddenOperationException("User is not active");
        }
        return user;
    }

    private void audit(User actor, AuditAction action, Receipt receipt, ReceiptResponse response, String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                action,
                "RECEIPT",
                receipt.getId(),
                receipt.getSale().getStore().getId(),
                receipt.getSale().getRegister().getId(),
                null,
                response,
                reason));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
