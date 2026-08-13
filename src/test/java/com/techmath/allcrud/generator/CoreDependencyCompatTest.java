package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * See docs/adr/0011-pinned-core-version-drift-guard.md
 */
class CoreDependencyCompatTest {

    @Test
    void abstractEntityVoIsAnInterfaceWithIdAccessors() throws ClassNotFoundException {
        Class<?> abstractEntityVO = Class.forName("com.techmath.allcrud.entity.AbstractEntityVO");

        assertTrue(abstractEntityVO.isInterface(), "AbstractEntityVO should be an interface");
        assertHasMethodNamed(abstractEntityVO, "getId");
        assertHasMethodNamed(abstractEntityVO, "setId");
    }

    @Test
    void crudControllerIsAbstractWithThreeTypeParamsAndRequiredHooks() throws ClassNotFoundException {
        Class<?> crudController = Class.forName("com.techmath.allcrud.controller.CrudController");

        assertTrue(Modifier.isAbstract(crudController.getModifiers()), "CrudController should be abstract");

        TypeVariable<?>[] typeParameters = crudController.getTypeParameters();
        assertEquals(3, typeParameters.length,
                "CrudController should declare 3 type parameters (T, VO, ID), found: " + Arrays.toString(typeParameters));

        assertHasAbstractMethodNamed(crudController, "getService");
        assertHasAbstractMethodNamed(crudController, "getConverter");
    }

    @Test
    void crudServiceIsAbstractWithTwoTypeParamsAndRequiredHook() throws ClassNotFoundException {
        Class<?> crudService = Class.forName("com.techmath.allcrud.service.CrudService");

        assertTrue(Modifier.isAbstract(crudService.getModifiers()), "CrudService should be abstract");

        TypeVariable<?>[] typeParameters = crudService.getTypeParameters();
        assertEquals(2, typeParameters.length,
                "CrudService should declare 2 type parameters (T, ID), found: " + Arrays.toString(typeParameters));

        assertHasAbstractMethodNamed(crudService, "getRepository");
    }

    private void assertHasMethodNamed(Class<?> type, String methodName) {
        boolean found = Arrays.stream(type.getMethods())
                .anyMatch(method -> method.getName().equals(methodName));
        assertTrue(found, type.getName() + " should declare a method named " + methodName);
    }

    private void assertHasAbstractMethodNamed(Class<?> type, String methodName) {
        boolean found = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .anyMatch(method -> Modifier.isAbstract(method.getModifiers()));
        assertTrue(found, type.getName() + " should declare an abstract method named " + methodName);
    }

}
