package com.merchtyl.reference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurrencyRepository extends JpaRepository<Currency, UUID>, JpaSpecificationExecutor<Currency> {
    Optional<Currency> findByCodeIgnoreCase(String code);

    @Query(value = """
            select c.*
            from currencies c
            join country_currencies cc on cc.currency_id = c.id
            join countries country on country.id = cc.country_id
            where upper(country.code) = upper(:countryCode)
              and c.active = true
              and cc.active = true
            order by cc.default_currency desc, c.code asc
            """, nativeQuery = true)
    List<Currency> findAllowedForCountry(@Param("countryCode") String countryCode);
}
