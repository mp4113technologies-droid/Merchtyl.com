package com.merchtyl.receipts;

import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.register.RegisterType;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.StoreAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KitchenTicketService {
    private final SaleRepository saleRepository;
    private final StoreAccessService storeAccessService;

    public KitchenTicketService(SaleRepository saleRepository, StoreAccessService storeAccessService) {
        this.saleRepository = saleRepository;
        this.storeAccessService = storeAccessService;
    }

    @Transactional(readOnly = true)
    public KitchenTicketDto get(UUID saleId, boolean reprint, Authentication authentication) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
        storeAccessService.requireStoreAccess(authentication, sale.getStore().getId());
        if (sale.getRegister().getType() != RegisterType.FOOD_SERVICE) {
            throw new ConflictException("KITCHEN_TICKET_REQUIRES_FOOD_SERVICE_ORDER");
        }
        if (sale.getStatus() != SaleStatus.COMPLETED
                && sale.getStatus() != SaleStatus.PARTIALLY_REFUNDED
                && sale.getStatus() != SaleStatus.REFUNDED) {
            throw new ConflictException("KITCHEN_TICKET_REQUIRES_COMPLETED_ORDER");
        }
        if (sale.getFoodOrderToken() == null) {
            throw new ConflictException("FOOD_ORDER_TOKEN_NOT_ASSIGNED");
        }
        return new KitchenTicketDto(
                PrintDocumentType.KITCHEN_TICKET,
                sale.getId(),
                sale.getFoodOrderToken(),
                sale.getStore().getName(),
                sale.getRegister().getName(),
                sale.getCompletedBy().getDisplayName(),
                sale.getCompletedAt(),
                null,
                null,
                sale.getItems().stream().map(this::item).toList(),
                null,
                reprint);
    }

    private KitchenTicketItemDto item(SaleItem item) {
        return new KitchenTicketItemDto(
                item.getId(),
                item.getProductName(),
                item.getQuantity(),
                List.of(),
                item.getExternalReference());
    }
}
