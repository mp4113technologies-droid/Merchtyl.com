package com.merchtyl.product;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkuGeneratorTest {
    private final SkuGenerator generator=new SkuGenerator(org.mockito.Mockito.mock(com.merchtyl.catalogue.MerchantIdentifierGenerator.class), org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class));
    @Test void generatesShortReadableVariantSkus(){
        assertThat(generator.base("Coca Cola","500 ml")).isEqualTo("COCA-COLA-500ML");
        assertThat(generator.base("Coca Cola","2 L")).isEqualTo("COCA-COLA-2L");
        assertThat(generator.base("Pepsi","500 ml")).isEqualTo("PEPSI-500ML");
        assertThat(generator.base("Lay's","BBQ 66g")).isEqualTo("LAYS-BBQ-66G");
    }
    @Test void normalizesMerchantSkuAndUsesReadableCollisionSuffix(){
        assertThat(generator.preserveProvided(" 01-cöké ")).isEqualTo("01-cöké");
        Set<String> used=Set.of("COCA-COLA-500ML-001","COCA-COLA-500ML-002");
        assertThat(generator.unique("Coca Cola","500 ml",used::contains)).isEqualTo("COCA-COLA-500ML-003");
    }
    @Test void productReferenceIsStableAndCannotBeReassigned(){
        Product product=org.mockito.Mockito.mock(Product.class,org.mockito.Mockito.CALLS_REAL_METHODS);
        product.assignProductReference("PRD-000125");
        product.assignProductReference("PRD-000125");
        assertThat(product.getProductReference()).isEqualTo("PRD-000125");
        assertThatThrownBy(()->product.assignProductReference("PRD-000126")).isInstanceOf(IllegalStateException.class);
    }
}
