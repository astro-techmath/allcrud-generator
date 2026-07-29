package org.openapitools.entity;

import com.techmath.allcrud.entity.AbstractEntity;

// TEST FIXTURE - NOT GENERATOR OUTPUT.
// This file simulates what a consuming project would hand-write for the "Product"
// entity (entity generation is out of scope for allcrud-generator's V1 - already
// decided). It exists only so the tests have something real to compile the
// generated Controller/Service against, since apiController.mustache/service.mustache
// reference "Product" by a hardcoded name and package (see those templates for why).
// It is kept under src/test/resources (not src/test/java) and compiled at test-run
// time alongside the freshly generated sources, because ProductConverter.java (in
// this same fixtures/ folder) references the generated ProductVO class, which does
// not exist yet when Gradle's normal compileTestJava step runs.
public class Product implements AbstractEntity<Long> {

    private Long id;
    private String name;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
