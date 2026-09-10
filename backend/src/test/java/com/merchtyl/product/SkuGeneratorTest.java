package com.merchtyl.product;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkuGeneratorTest {
    private final SkuGenerator generator=new SkuGenerator();
    @Test void generatesShortReadableVariantSkus(){
        assertThat(generator.base("Coca Cola","500 ml")).isEqualTo("COC500ML");
        assertThat(generator.base("Coca Cola","2 L")).isEqualTo("COC2L");
        assertThat(generator.base("Pepsi","500 ml")).isEqualTo("PEP500ML");
        assertThat(generator.base("Lays","BBQ 66g")).isEqualTo("LAYBBQ66G");
    }
    @Test void normalizesMerchantSkuAndUsesReadableCollisionSuffix(){
        assertThat(generator.normalizeProvided(" 01-cöké ")).isEqualTo("01-COKE");
        Set<String> used=Set.of("COC500ML","COC500ML-2");
        assertThat(generator.unique("Coca Cola","500 ml",used::contains)).isEqualTo("COC500ML-3");
    }
    @Test void productReferenceIsStableAndCannotBeReassigned(){
        Product product=org.mockito.Mockito.mock(Product.class,org.mockito.Mockito.CALLS_REAL_METHODS);
        product.assignProductReference("PRD-000125");
        product.assignProductReference("PRD-000125");
        assertThat(product.getProductReference()).isEqualTo("PRD-000125");
        assertThatThrownBy(()->product.assignProductReference("PRD-000126")).isInstanceOf(IllegalStateException.class);
    }
}
