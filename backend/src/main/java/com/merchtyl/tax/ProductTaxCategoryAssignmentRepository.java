package com.merchtyl.tax;

import com.merchtyl.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface ProductTaxCategoryAssignmentRepository extends JpaRepository<ProductTaxCategoryAssignment, UUID>, JpaSpecificationExecutor<ProductTaxCategoryAssignment> {
    @Override
    @EntityGraph(attributePaths = {"product", "taxCategory"})
    Page<ProductTaxCategoryAssignment> findAll(Specification<ProductTaxCategoryAssignment> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"product", "taxCategory"})
    Optional<ProductTaxCategoryAssignment> findById(UUID id);

    boolean existsByProduct(Product product);

    boolean existsByProductAndIdNot(Product product, UUID id);
}
