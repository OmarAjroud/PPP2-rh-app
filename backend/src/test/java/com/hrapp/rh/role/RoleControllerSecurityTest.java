package com.hrapp.rh.role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Vérifie les règles @PreAuthorize du contrôleur : seul un ADMIN gère les rôles. */
@SpringJUnitConfig(RoleControllerSecurityTest.Config.class)
class RoleControllerSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        RoleService roleService() {
            return mock(RoleService.class);
        }

        @Bean
        RoleController roleController(RoleService roleService) {
            return new RoleController(roleService);
        }
    }

    private static final RoleRequest REQUETE = new RoleRequest("STAGIAIRE", "Stagiaire");

    @Autowired
    private RoleController controller;

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_shouldBeAllowed_toCreateUpdateAndDeleteRoles() {
        assertThatCode(() -> controller.create(REQUETE)).doesNotThrowAnyException();
        assertThatCode(() -> controller.update(5L, REQUETE)).doesNotThrowAnyException();
        assertThatCode(() -> controller.delete(5L)).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "RH")
    void rh_shouldBeDenied_managingRoles() {
        assertThatThrownBy(() -> controller.create(REQUETE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.update(5L, REQUETE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.delete(5L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "EMPLOYE")
    void employee_shouldBeDenied_managingRoles() {
        assertThatThrownBy(() -> controller.create(REQUETE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.delete(5L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "EMPLOYE")
    void anyConnectedUser_shouldBeAllowed_toReadRoles() {
        assertThatCode(() -> controller.list()).doesNotThrowAnyException();
        assertThatCode(() -> controller.getById(1L)).doesNotThrowAnyException();
    }
}
