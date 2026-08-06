package org.openapitools.entity;

import com.techmath.allcrud.entity.AbstractEntity;

// TEST FIXTURE - NOT GENERATOR OUTPUT.
public class Sprocket implements AbstractEntity<Long> {

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
