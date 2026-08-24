package com.merchtyl.catalogue;

@FunctionalInterface
interface CatalogueReferenceFactory<T extends CatalogueReference> {
    T create(CatalogueReferenceValues values);
}
