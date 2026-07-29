package org.openapitools.entity;

import com.techmath.allcrud.entity.AbstractEntity;

// TEST FIXTURE - NOT GENERATOR OUTPUT.
// This file simulates what a consuming project would hand-write for the "Order"
// entity (entity generation is out of scope for allcrud-generator's V1 - already
// decided). It exists only so the tests have something real to compile the
// generated Controller/Service against, since apiController.mustache/service.mustache
// reference "Order" by a hardcoded name and package (see those templates for why).
// It is kept under src/test/resources (not src/test/java) and compiled at test-run
// time alongside the freshly generated sources, because OrderConverter.java (in
// this same fixtures/ folder) references the generated OrderVO class, which does
// not exist yet when Gradle's normal compileTestJava step runs.
//
// Deliberately uses Integer for the ID (Product uses Long) - this project's
// multi-resource compat test relies on that mismatch to prove AllcrudSpringCodegen
// doesn't leak ID type resolution between resources processed in the same run.
public class Order implements AbstractEntity<Integer> {

    private Integer id;
    private String reference;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

}
