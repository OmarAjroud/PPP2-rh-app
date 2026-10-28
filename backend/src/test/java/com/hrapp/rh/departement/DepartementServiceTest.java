package com.hrapp.rh.departement;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.employe.Employe;
import com.hrapp.rh.employe.EmployeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DepartementServiceTest {

    @Mock private DepartementRepository repository;
    @Mock private EmployeRepository employeRepository;
    @Spy  private DepartementMapper mapper = new DepartementMapperImpl();

    @InjectMocks private DepartementService service;

    @Test
    @DisplayName("Détail : renvoie le département avec la liste de ses employés")
    void findById_shouldReturnDepartmentWithItsEmployees() {
        Employe lina = new Employe();
        lina.setId(4L);
        lina.setNom("Gharbi");
        lina.setPrenom("Lina");
        lina.setEmail("employe@rh.com");

        given(repository.findById(1L)).willReturn(Optional.of(departement(1L, "Informatique")));
        given(employeRepository.findByDepartementId(1L)).willReturn(List.of(lina));

        DepartementDetailResponse result = service.findById(1L);

        assertThat(result.nom()).isEqualTo("Informatique");
        assertThat(result.employes()).hasSize(1);
        assertThat(result.employes().get(0).email()).isEqualTo("employe@rh.com");
    }

    @Test
    @DisplayName("Création : refusée si le nom du département existe déjà")
    void create_shouldThrowConflict_whenNameAlreadyExists() {
        given(repository.existsByNomIgnoreCase("Finance")).willReturn(true);

        assertThatThrownBy(() -> service.create(new DepartementRequest("Finance", "Doublon")))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("existe déjà");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Suppression : refusée si le département contient encore des employés")
    void delete_shouldThrowConflict_whenDepartmentStillHasEmployees() {
        given(repository.findById(1L)).willReturn(Optional.of(departement(1L, "Informatique")));
        given(employeRepository.countByDepartementId(1L)).willReturn(3L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("employés");

        verify(repository, never()).delete(any(Departement.class));
    }

    @Test
    @DisplayName("Suppression : supprime un département vide")
    void delete_shouldDeleteDepartment_whenEmpty() {
        Departement vide = departement(7L, "Marketing");
        given(repository.findById(7L)).willReturn(Optional.of(vide));
        given(employeRepository.countByDepartementId(7L)).willReturn(0L);

        service.delete(7L);

        verify(repository).delete(vide);
    }

    private Departement departement(Long id, String nom) {
        Departement d = new Departement();
        d.setId(id);
        d.setNom(nom);
        return d;
    }
}