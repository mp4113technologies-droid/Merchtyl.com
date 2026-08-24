package com.merchtyl.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.idempotency.IdempotencyProperties;
import com.merchtyl.idempotency.IdempotencyRecord;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.idempotency.IdempotencyStore;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockCountServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000a02");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000a03");
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000a04");
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    private final StockCountRepository stockCountRepository = mock(StockCountRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final InventoryBalanceRepository balanceRepository = mock(InventoryBalanceRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Store store = mock(Store.class);
    private final Product product = mock(Product.class);
    private final User user = mock(User.class);
    private final InventoryBalance balance = new InventoryBalance(store, product, new BigDecimal("10.0000"), NOW);
    private final StockCountService stockCountService = new StockCountService(
            stockCountRepository,
            storeRepository,
            productRepository,
            balanceRepository,
            inventoryService,
            userRepository,
            auditService,
            idempotencyService(),
            objectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private StockCount savedCount;

    @BeforeEach
    void setUp() {
        when(store.getId()).thenReturn(STORE_ID);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product.isInventoryTrackingEnabled()).thenReturn(true);
        when(user.getId()).thenReturn(USER_ID);
        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(userRepository.findByEmailIgnoreCase("manager@example.test")).thenReturn(Optional.of(user));
        when(balanceRepository.findByStoreIdAndProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.of(balance));
        when(stockCountRepository.saveAndFlush(any(StockCount.class))).thenAnswer(invocation -> {
            savedCount = invocation.getArgument(0);
            return savedCount;
        });
        when(stockCountRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(savedCount));
        when(stockCountRepository.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(savedCount));
        when(inventoryService.recordStockChange(any(), any())).thenReturn(transactionResponse());
    }

    @Test
    void createDraftCapturesExpectedQuantityAndCalculatesVarianceWhenCounted() {
        StockCountResponse created = stockCountService.create(createRequest(null), authentication());
        UUID lineId = created.lines().getFirst().id();

        StockCountResponse counted = stockCountService.enterCountedQuantities(
                created.id(),
                new StockCountUpdateLinesRequest(List.of(new StockCountLineCountRequest(lineId, new BigDecimal("7.0000")))),
                authentication());

        assertThat(created.status()).isEqualTo(StockCountStatus.DRAFT);
        assertThat(created.lines().getFirst().expectedQuantity()).isEqualByComparingTo("10.0000");
        assertThat(counted.lines().getFirst().countedQuantity()).isEqualByComparingTo("7.0000");
        assertThat(counted.lines().getFirst().varianceQuantity()).isEqualByComparingTo("-3.0000");
        verify(auditService, times(2)).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void directSaveCreatesInventoryMovementAndMarksCountSaved() {
        StockCountResponse created = stockCountService.create(createRequest(null), authentication());
        UUID lineId = created.lines().getFirst().id();

        StockCountResponse saved = stockCountService.enterCountedQuantities(created.id(),
                new StockCountUpdateLinesRequest(List.of(new StockCountLineCountRequest(lineId, new BigDecimal("7.0000")))),
                authentication());

        assertThat(saved.status()).isEqualTo(StockCountStatus.SAVED);
        assertThat(saved.lines().getFirst().varianceQuantity()).isEqualByComparingTo("-3.0000");
        assertThat(savedCount.getLines().getFirst().getInventoryTransactionId()).isEqualTo(TRANSACTION_ID);
        verify(inventoryService, times(1)).recordStockChange(any(InventoryStockChangeRequest.class), any());
    }

    @Test
    void zeroDifferenceSavesWithoutArtificialInventoryMovement() {
        StockCountResponse saved = stockCountService.create(createRequest(new BigDecimal("10.0000")), authentication());

        assertThat(saved.status()).isEqualTo(StockCountStatus.SAVED);
        assertThat(saved.lines().getFirst().varianceQuantity()).isEqualByComparingTo("0.0000");
        verify(inventoryService, times(0)).recordStockChange(any(InventoryStockChangeRequest.class), any());
    }

    @Test
    void reviewRequiresAllLinesCounted() {
        StockCountResponse created = stockCountService.create(createRequest(null), authentication());

        assertThatThrownBy(() -> stockCountService.review(created.id(), new StockCountReviewRequest("Reviewed"), authentication()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("All count lines require an actual quantity");
    }

    @Test
    void existingInReviewCountCanBeSavedDirectly() {
        StockCountResponse created = stockCountService.create(createRequest(null), authentication());
        StockCountLine line = savedCount.getLines().getFirst();
        savedCount.enterCountedQuantity(line, new BigDecimal("7.0000"));
        savedCount.review(user, NOW, "Legacy review");

        StockCountResponse saved = stockCountService.enterCountedQuantities(created.id(),
                new StockCountUpdateLinesRequest(List.of(new StockCountLineCountRequest(line.getId(), new BigDecimal("8.0000")))),
                authentication());

        assertThat(saved.status()).isEqualTo(StockCountStatus.SAVED);
        assertThat(saved.lines().getFirst().resultingQuantity()).isEqualByComparingTo("7.0000");
    }

    private static StockCountCreateRequest createRequest(BigDecimal countedQuantity) {
        return new StockCountCreateRequest(
                STORE_ID,
                "Cycle count A",
                "Shelf count",
                List.of(new StockCountLineCreateRequest(PRODUCT_ID, countedQuantity)));
    }

    private static InventoryTransactionResponse transactionResponse() {
        return new InventoryTransactionResponse(
                TRANSACTION_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000a05"),
                STORE_ID,
                PRODUCT_ID,
                InventoryTransactionType.STOCK_COUNT_DECREASE,
                new BigDecimal("-3.0000"),
                new BigDecimal("7.0000"),
                "STOCK_COUNT",
                UUID.fromString("00000000-0000-0000-0000-000000000a06"),
                "Cycle count A",
                USER_ID,
                NOW,
                NOW,
                0);
    }

    private static TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken("manager@example.test", null);
    }

    private static IdempotencyService idempotencyService() {
        IdempotencyProperties properties = new IdempotencyProperties();
        return new IdempotencyService(new MemoryIdempotencyStore(), properties, objectMapper(), new NoOpTransactionOperations());
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static class MemoryIdempotencyStore implements IdempotencyStore {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();

        @Override
        public synchronized void deleteExpired(UUID userId, String endpoint, String idempotencyKey, Instant now) {
            IdempotencyRecord record = records.get(scope(userId, endpoint, idempotencyKey));
            if (record != null && record.getExpiresAt().isBefore(now)) {
                records.remove(scope(userId, endpoint, idempotencyKey));
            }
        }

        @Override
        public synchronized Optional<IdempotencyRecord> findActiveForUpdate(UUID userId, String endpoint, String idempotencyKey, Instant now) {
            IdempotencyRecord record = records.get(scope(userId, endpoint, idempotencyKey));
            if (record == null || record.getExpiresAt().isBefore(now)) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        @Override
        public synchronized IdempotencyRecord save(IdempotencyRecord record) {
            records.put(scope(record.getUserId(), record.getEndpoint(), record.getIdempotencyKey()), record);
            return record;
        }

        @Override
        public synchronized long deleteExpiredBefore(Instant now) {
            long deleted = 0;
            Iterator<IdempotencyRecord> iterator = records.values().iterator();
            while (iterator.hasNext()) {
                IdempotencyRecord record = iterator.next();
                if (record.getExpiresAt().isBefore(now)) {
                    iterator.remove();
                    deleted++;
                }
            }
            return deleted;
        }

        private static String scope(UUID userId, String endpoint, String idempotencyKey) {
            return userId + ":" + endpoint + ":" + idempotencyKey;
        }
    }

    private static class NoOpTransactionOperations implements TransactionOperations {
        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
