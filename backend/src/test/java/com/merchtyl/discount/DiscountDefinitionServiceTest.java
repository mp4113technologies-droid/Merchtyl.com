package com.merchtyl.discount;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.sales.SaleAdjustmentType;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscountDefinitionServiceTest {
    private final DiscountDefinitionRepository repository=mock(DiscountDefinitionRepository.class);
    private final StoreAccessService access=mock(StoreAccessService.class);
    private final StoreRepository stores=mock(StoreRepository.class);
    private final DiscountDefinitionService service=new DiscountDefinitionService(repository,access,stores);
    private final Authentication auth=mock(Authentication.class);
    private final User actor=mock(User.class);
    private final UUID tenantId=UUID.randomUUID();

    @Test void ownerOrManagerScopeCreatesAndUpdatesDefinitionsWithinCurrentTenant(){
        when(access.currentTenantUser(auth)).thenReturn(actor); when(actor.getTenantId()).thenReturn(tenantId); when(actor.getId()).thenReturn(UUID.randomUUID());
        when(repository.save(any())).thenAnswer(call->call.getArgument(0));
        var created=service.create(new DiscountDefinitionRequest(" Staff Discount ",SaleAdjustmentType.DISCOUNT_PERCENTAGE,new BigDecimal("10"),"Team",true),auth);
        assertThat(created.name()).isEqualTo("Staff Discount"); assertThat(created.value()).isEqualByComparingTo("10"); assertThat(created.active()).isTrue();
    }

    @Test void rejectsInvalidDefinitionValues(){
        when(access.currentTenantUser(auth)).thenReturn(actor); when(actor.getTenantId()).thenReturn(tenantId);
        assertThatThrownBy(()->service.create(new DiscountDefinitionRequest("Bad",SaleAdjustmentType.DISCOUNT_PERCENTAGE,new BigDecimal("100.01"),null,true),auth)).isInstanceOf(BadRequestException.class).hasMessage("DISCOUNT_PERCENTAGE_EXCEEDS_MAXIMUM");
        assertThatThrownBy(()->service.create(new DiscountDefinitionRequest("Bad",SaleAdjustmentType.DISCOUNT_AMOUNT,BigDecimal.ZERO,null,true),auth)).isInstanceOf(BadRequestException.class).hasMessage("DISCOUNT_VALUE_INVALID");
    }

    @Test void tenantIsolationAndInactiveEnforcementAreAuthoritative(){
        UUID id=UUID.randomUUID();
        when(repository.findByIdAndTenantId(id,tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.requireActive(id,tenantId)).isInstanceOf(NotFoundException.class).hasMessage("DISCOUNT_NOT_FOUND");
        DiscountDefinition inactive=mock(DiscountDefinition.class); when(inactive.isActive()).thenReturn(false);
        when(repository.findByIdAndTenantId(id,tenantId)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(()->service.requireActive(id,tenantId)).hasMessage("DISCOUNT_INACTIVE");
    }

    @Test void activePosListIsTenantStoreAndScheduleScoped(){
        UUID storeId=UUID.randomUUID(),otherStoreId=UUID.randomUUID(),actorId=UUID.randomUUID();
        when(access.currentTenantId(auth)).thenReturn(tenantId);
        DiscountDefinition allStores=new DiscountDefinition(tenantId,new DiscountDefinitionRequest(
                "Staff",SaleAdjustmentType.DISCOUNT_PERCENTAGE,new BigDecimal("10"),null,null,null,null,null,
                Set.of(),Set.of(),true,Set.of(),null,null,true),actorId);
        DiscountDefinition selectedStore=new DiscountDefinition(tenantId,new DiscountDefinitionRequest(
                "Lunch",SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),null,null,null,null,null,
                Set.of(),Set.of(),false,Set.of(storeId),null,null,true),actorId);
        DiscountDefinition anotherStore=new DiscountDefinition(tenantId,new DiscountDefinitionRequest(
                "Other",SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),null,null,null,null,null,
                Set.of(),Set.of(),false,Set.of(otherStoreId),null,null,true),actorId);
        when(repository.findAllByTenantIdAndActiveTrueOrderByNameAsc(tenantId)).thenReturn(List.of(allStores,selectedStore,anotherStore));

        assertThat(service.activeForStore(storeId,auth)).extracting(DiscountDefinitionResponse::name).containsExactly("Staff","Lunch");
        verify(access).requireStoreAccess(auth,storeId);
    }
}
