package org.openapitools.entity;

import com.techmath.allcrud.entity.AbstractEntity;

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
