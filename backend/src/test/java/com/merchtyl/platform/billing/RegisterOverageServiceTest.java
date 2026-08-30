package com.merchtyl.platform.billing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterOverageServiceTest {
    @Test void underAllowanceHasNoOverage(){assertThat(calculate(2,1).additionalRegisters()).isZero();}
    @Test void exactAllowanceHasNoOverage(){assertThat(calculate(2,2).additionalRegisters()).isZero();}
    @Test void aboveAllowanceProducesDifference(){assertThat(calculate(2,4).additionalRegisters()).isEqualTo(2);}
    @Test void zeroAllowanceBillsEveryActiveRegister(){assertThat(calculate(0,4).additionalRegisters()).isEqualTo(4);}

    @Test
    void allowancesAreIndependentAndNeverPooled(){
        var result=RegisterOverageService.calculate(List.of(store("A",1),store("B",5)),2);
        assertThat(result.additionalRegisters()).isEqualTo(3).isNotEqualTo(2);
        assertThat(result.stores()).extracting(RegisterOverageService.StoreRegisterUsage::additionalRegisters).containsExactly(0,3);
    }

    @Test
    void multipleStoresEachReceiveTheirOwnAllowance(){
        var result=RegisterOverageService.calculate(List.of(store("One",1),store("Two",2),store("Three",3),store("Four",6)),2);
        assertThat(result.additionalRegisters()).isEqualTo(5);
        assertThat(result.stores()).extracting(RegisterOverageService.StoreRegisterUsage::additionalRegisters).containsExactly(0,0,1,4);
    }

    private static RegisterOverageService.RegisterOverage calculate(int allowance,int active){return RegisterOverageService.calculate(List.of(store("Store",active)),allowance);}
    private static RegisterOverageService.StoreRegisterUsage store(String name,int active){return new RegisterOverageService.StoreRegisterUsage(UUID.randomUUID(),name,active,0);}
}
