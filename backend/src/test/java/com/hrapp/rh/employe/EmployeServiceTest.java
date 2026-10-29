package com.hrapp.rh.employe;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.departement.Departement;
import com.hrapp.rh.departement.DepartementRepository;
import com.hrapp.rh.role.Role;
import com.hrapp.rh.role.RoleRepository;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.utilisateur.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmployeServiceTest {

    @Mock private EmployeRepository repository;
    @Mock private DepartementRepository departementRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Spy  private EmployeMapper mapper = new EmployeMapperImpl();

    @InjectMocks private EmployeService service;

    // ---------- création ----------

    @Test
    @DisplayName("Création : refusée si l'email existe déjà")
    void create_shouldThrowConflict_whenEmailAlreadyExists() {
        given(repository.existsByEmailIgnoreCase("lina@rh.com")).willReturn(true);

        assertThatThrownBy(() -> service.create(request("lina@rh.com", 1L, 4L, null)))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("existe déjà");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Création : enregistre l'employé quand les données sont valides")
    void create_shouldSaveEmployee_whenDataIsValid() {
        given(repository.existsByEmailIgnoreCase("yassine@rh.com")).willReturn(false);
        given(departementRepository.findById(1L)).willReturn(Optional.of(departement(1L, "Informatique")));
        given(roleRepository.findById(4L)).willReturn(Optional.of(role(4L, "EMPLOYE")));
        given(repository.save(any(Employe.class))).willAnswer(invocation -> {
            Employe saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        EmployeDetailResponse result = service.create(request("yassine@rh.com", 1L, 4L, null));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.email()).isEqualTo("yassine@rh.com");
        assertThat(result.departementNom()).isEqualTo("Informatique");
        assertThat(result.roleNom()).isEqualTo("EMPLOYE");
        assertThat(result.managerId()).isNull();
    }

    @Test
    @DisplayName("Création : 404 si le département n'existe pas")
    void create_shouldThrowNotFound_whenDepartmentDoesNotExist() {
        given(repository.existsByEmailIgnoreCase("yassine@rh.com")).willReturn(false);
        given(departementRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("yassine@rh.com", 99L, 4L, null)))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageContaining("Département introuvable");

        verify(repository, never()).save(any());
    }

    // ---------- modification ----------

    @Test
    @DisplayName("Modification : refusée si l'email appartient à un autre employé")
    void update_shouldThrowConflict_whenEmailBelongsToAnotherEmployee() {
        given(repository.findById(1L)).willReturn(Optional.of(employe(1L, "lina@rh.com")));
        given(repository.existsByEmailIgnoreCaseAndIdNot("sara@rh.com", 1L)).willReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("sara@rh.com", 1L, 4L, null)))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    @DisplayName("Modification : un employé ne peut pas être son propre manager")
    void update_shouldThrowConflict_whenEmployeeIsOwnManager() {
        given(repository.findById(1L)).willReturn(Optional.of(employe(1L, "lina@rh.com")));
        given(repository.existsByEmailIgnoreCaseAndIdNot("lina@rh.com", 1L)).willReturn(false);
        given(departementRepository.findById(1L)).willReturn(Optional.of(departement(1L, "Informatique")));
        given(roleRepository.findById(4L)).willReturn(Optional.of(role(4L, "EMPLOYE")));

        assertThatThrownBy(() -> service.update(1L, request("lina@rh.com", 1L, 4L, 1L)))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("propre manager");
    }

    // ---------- suppression ----------

    @Test
    @DisplayName("Suppression : refusée si l'employé est manager d'autres employés")
    void delete_shouldThrowConflict_whenEmployeeIsManagerOfOthers() {
        given(repository.findById(3L)).willReturn(Optional.of(employe(3L, "manager@rh.com")));
        given(repository.existsByManagerId(3L)).willReturn(true);

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("manager");

        verify(repository, never()).delete(any(Employe.class));
        verifyNoInteractions(utilisateurRepository);
    }

    @Test
    @DisplayName("Suppression : supprime l'employé et son compte s'il ne manage personne")
    void delete_shouldDeleteEmployeeAndAccount_whenHeManagesNobody() {
        Employe employe = employe(4L, "employe@rh.com");
        given(repository.findById(4L)).willReturn(Optional.of(employe));
        given(repository.existsByManagerId(4L)).willReturn(false);

        service.delete(4L);

        verify(utilisateurRepository).deleteByEmployeId(4L);
        verify(repository).delete(employe);
    }

    @Test
    @DisplayName("Suppression : 404 si l'employé n'existe pas")
    void delete_shouldThrowNotFound_whenEmployeeDoesNotExist() {
        given(repository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    // ---------- recherche ----------

    @Test
    @DisplayName("Recherche : tri refusé sur une colonne non autorisée (ex. salaire)")
    void search_shouldThrowBadRequest_whenSortColumnIsNotAllowed() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("salaire"));

        assertThatThrownBy(() -> service.search(user(1L, 1L, Roles.ADMIN), null, null, null, pageable))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("Tri non autorisé");

        verifyNoInteractions(repository);
    }

    // ---------- détail : droits d'accès et salaire ----------

    @Test
    @DisplayName("Détail : un employé ne peut pas consulter la fiche d'un autre employé")
    void findById_shouldThrowAccessDenied_whenEmployeeViewsSomeoneElse() {
        given(repository.findById(3L)).willReturn(Optional.of(employe(3L, "manager@rh.com")));

        assertThatThrownBy(() -> service.findById(user(4L, 1L, Roles.EMPLOYE), 3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Détail : un manager ne peut pas consulter un employé d'un autre département")
    void findById_shouldThrowAccessDenied_whenManagerViewsOtherDepartment() {
        given(repository.findById(2L)).willReturn(Optional.of(employe(2L, "rh@rh.com", 2L)));

        assertThatThrownBy(() -> service.findById(user(3L, 1L, Roles.MANAGER), 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Détail : un manager voit un employé de son département, sans le salaire")
    void findById_shouldHideSalary_whenManagerViewsEmployeeOfHisDepartment() {
        given(repository.findById(4L)).willReturn(Optional.of(employe(4L, "employe@rh.com", 1L)));

        EmployeDetailResponse result = service.findById(user(3L, 1L, Roles.MANAGER), 4L);

        assertThat(result.email()).isEqualTo("employe@rh.com");
        assertThat(result.salaire()).isNull();
    }

    @Test
    @DisplayName("Détail : un employé voit son propre salaire")
    void findById_shouldShowSalary_whenEmployeeViewsHisOwnProfile() {
        given(repository.findById(4L)).willReturn(Optional.of(employe(4L, "employe@rh.com", 1L)));

        EmployeDetailResponse result = service.findById(user(4L, 1L, Roles.EMPLOYE), 4L);

        assertThat(result.salaire()).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("Détail : le RH voit le salaire de n'importe quel employé")
    void findById_shouldShowSalary_whenRhViewsAnyEmployee() {
        given(repository.findById(4L)).willReturn(Optional.of(employe(4L, "employe@rh.com", 1L)));

        EmployeDetailResponse result = service.findById(user(2L, 2L, Roles.RH), 4L);

        assertThat(result.salaire()).isEqualByComparingTo("2500");
    }

    // ---------- méthodes utilitaires pour fabriquer les données de test ----------

    private Departement departement(Long id, String nom) {
        Departement d = new Departement();
        d.setId(id);
        d.setNom(nom);
        return d;
    }

    private Role role(Long id, String nom) {
        Role r = new Role();
        r.setId(id);
        r.setNom(nom);
        return r;
    }

    private Employe employe(Long id, String email) {
        return employe(id, email, 1L);
    }

    private Employe employe(Long id, String email, Long departementId) {
        Employe e = new Employe();
        e.setId(id);
        e.setNom("Gharbi");
        e.setPrenom("Lina");
        e.setEmail(email);
        e.setDateEmbauche(LocalDate.of(2023, 9, 1));
        e.setSalaire(new BigDecimal("2500"));
        e.setDepartement(departement(departementId, "Département " + departementId));
        e.setRole(role(4L, "EMPLOYE"));
        return e;
    }

    private UtilisateurConnecte user(Long employeId, Long departementId, String role) {
        return new UtilisateurConnecte(employeId, employeId, departementId, "test", "hash", role, true);
    }

    private EmployeRequest request(String email, Long departementId, Long roleId, Long managerId) {
        return new EmployeRequest("Jlassi", "Yassine", email, LocalDate.of(2026, 10, 1),
                new BigDecimal("2300"), departementId, roleId, managerId);
    }
}