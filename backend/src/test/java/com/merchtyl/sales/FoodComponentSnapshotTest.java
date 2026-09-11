package com.merchtyl.sales;

import com.merchtyl.foodmenu.FoodComponentSelectionState;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class FoodComponentSnapshotTest {
    @Test
    void roundTripsStableIdentityStateNameAndAuthoritativePrice() {
        var values=List.of(
                new FoodComponentSnapshot(UUID.randomUUID(), FoodComponentSelectionState.REMOVED,"Tomato\nripe",BigDecimal.ZERO),
                new FoodComponentSnapshot(UUID.randomUUID(), FoodComponentSelectionState.EXTRA,"Pickles",new BigDecimal("0.5000")));
        assertThat(FoodComponentSnapshot.decode(FoodComponentSnapshot.encode(values))).isEqualTo(values);
    }
}
