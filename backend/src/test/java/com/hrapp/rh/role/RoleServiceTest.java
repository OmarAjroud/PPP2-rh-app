package com.hrapp.rh.role;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.employe.EmployeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository repository;
    @Mock private EmployeRepository employeRepository;
    @Spy  private RoleMapper mapper = new RoleMapperImpl();

    @InjectMocks private RoleService service;

    @Test
    @DisplayName("Création : refusée si le nom du rôle existe déjà")
    void create_shouldThrowConflict_whenNameAlreadyExists() {
        given(repository.existsByNomIgnoreCase("ADMIN")).willReturn(true);

        assertThatThrownBy(() -> service.create(new RoleRequest("ADMIN", "Doublon")))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("existe déjà");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : enregistre le rôle quand le nom est libre")
    void create_shouldReturnRole_whenNameIsFree() {
        given(repository.existsByNomIgnoreCase("STAGIAIRE")).willReturn(false);
        given(repository.save(any(Role.class))).willAnswer(invocation -> {
            Role saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        RoleResponse result = service.create(new RoleRequest("STAGIAIRE", "Stagiaire"));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.nom()).isEqualTo("STAGIAIRE");
    }

    @Test
    @DisplayName("Modification : refusée si le nom est pris par un autre rôle")
    void update_shouldThrowConflict_whenNameUsedByAnotherRole() {
        given(repository.findById(2L)).willReturn(Optional.of(role(2L, "RH")));
        given(repository.existsByNomIgnoreCaseAndIdNot("ADMIN", 2L)).willReturn(true);

        assertThatThrownBy(() -> service.update(2L, new RoleRequest("ADMIN", "x")))
                .isInstanceOf(ConflitException.class);
    }

    @Test
    @DisplayName("Suppression : refusée si des employés utilisent ce rôle")
    void delete_shouldThrowConflict_whenRoleIsUsedByEmployees() {
        given(repository.findById(1L)).willReturn(Optional.of(role(1L, "ADMIN")));
        given(employeRepository.existsByRoleId(1L)).willReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("employés");

        verify(repository, never()).delete(any(Role.class));
    }

    @Test
    @DisplayName("Suppression : supprime le rôle s'il n'est utilisé par personne")
    void delete_shouldDeleteRole_whenUnused() {
        Role role = role(9L, "STAGIAIRE");
        given(repository.findById(9L)).willReturn(Optional.of(role));
        given(employeRepository.existsByRoleId(9L)).willReturn(false);

        service.delete(9L);

        verify(repository).delete(role);
    }

    private Role role(Long id, String nom) {
        Role r = new Role();
        r.setId(id);
        r.setNom(nom);
        return r;
    }
}