package com.merchtyl.sales;

import com.merchtyl.foodmenu.FoodComponentSelectionState;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public record FoodComponentSnapshot(UUID componentId, FoodComponentSelectionState state, String name, BigDecimal priceAdjustment) {
    private static String field(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private static String value(String encoded){return new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8);}
    static String encode(List<FoodComponentSnapshot> values){return values==null||values.isEmpty()?null:values.stream().map(v->field(v.componentId().toString())+"."+field(v.state().name())+"."+field(v.name())+"."+field(v.priceAdjustment().toPlainString())).collect(java.util.stream.Collectors.joining("\n"));}
    static List<FoodComponentSnapshot> decode(String encoded){if(encoded==null||encoded.isBlank())return List.of();return encoded.lines().map(line->{var parts=line.split("\\.",-1);return new FoodComponentSnapshot(UUID.fromString(value(parts[0])),FoodComponentSelectionState.valueOf(value(parts[1])),value(parts[2]),new BigDecimal(value(parts[3])));}).toList();}
}
