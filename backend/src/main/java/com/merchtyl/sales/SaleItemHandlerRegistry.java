package com.merchtyl.sales;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.product.SellableType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SaleItemHandlerRegistry {
    private final Map<SellableType, SaleItemHandler> handlers;

    public SaleItemHandlerRegistry(List<SaleItemHandler> handlers) {
        this.handlers = new EnumMap<>(SellableType.class);
        handlers.forEach(handler -> {
            SaleItemHandler previous = this.handlers.putIfAbsent(handler.supportedType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate sale item handler for " + handler.supportedType());
            }
        });
    }

    public SaleItemHandler handlerFor(SellableType sellableType) {
        if (sellableType == null) {
            throw new BadRequestException("Sellable type is required");
        }
        SaleItemHandler handler = handlers.get(sellableType);
        if (handler == null) {
            throw new BadRequestException("No sale item handler registered for " + sellableType);
        }
        return handler;
    }

    public void validate(SaleItemRequest request) {
        if (request == null || request.product() == null) {
            throw new BadRequestException("Product is required");
        }
        handlerFor(request.product().getSellableType()).validate(request);
    }
}
