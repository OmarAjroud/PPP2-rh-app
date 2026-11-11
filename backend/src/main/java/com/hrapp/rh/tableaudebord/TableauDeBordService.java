package com.hrapp.rh.tableaudebord;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import com.hrapp.rh.conge.StatutDemandeConge;
import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import com.hrapp.rh.tableaudebord.TableauDeBordResponse.EmbauchesMois;
import com.hrapp.rh.tableaudebord.TableauDeBordResponse.Indicateurs;
import com.hrapp.rh.tableaudebord.TableauDeBordResponse.MasseSalarialeDepartement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class TableauDeBordService {

    private final TableauDeBordRepository repository;
    private final Clock clock;

    /**
     * Tableau de bord de l'utilisateur connecté.
     * ADMIN / RH : toute l'entreprise, ou un département au choix ; masse salariale incluse.
     * MANAGER : uniquement son département ; jamais de masse salariale.
     *
     * @param annee         année des graphiques « embauches par mois » et « répartition des congés » (en cours par défaut)
     * @param departementId filtre optionnel
     */
    @Transactional(readOnly = true)
    public TableauDeBordResponse build(UtilisateurConnecte utilisateur, Integer annee, Long departementId) {
        Long perimetre = scope(utilisateur, departementId);
        LocalDate aujourdhui = LocalDate.now(clock);
        int anneeCible = checkedYear(annee, aujourdhui);
        LocalDate debutAnnee = LocalDate.of(anneeCible, 1, 1);
        LocalDate finAnnee = LocalDate.of(anneeCible, 12, 31);
        YearMonth moisCourant = YearMonth.from(aujourdhui);

        Indicateurs indicateurs = new Indicateurs(
                repository.countEmployees(perimetre),
                repository.countRequests(perimetre, StatutDemandeConge.EN_ATTENTE),
                repository.countHires(perimetre, moisCourant.atDay(1), moisCourant.atEndOfMonth()),
                repository.countAbsent(perimetre, aujourdhui, StatutDemandeConge.APPROUVEE));

        return new TableauDeBordResponse(
                anneeCible,
                perimetre,
                indicateurs,
                repository.headcountByDepartment(perimetre),
                hiresByMonth(perimetre, debutAnnee, finAnnee),
                repository.leaveByType(perimetre, StatutDemandeConge.APPROUVEE, debutAnnee, finAnnee),
                utilisateur.isAdminOrRh() ? payroll(perimetre) : null);
    }

    // ---------- méthodes privées ----------

    /** Le département sur lequel porte le tableau de bord (null = toute l'entreprise). */
    private Long scope(UtilisateurConnecte utilisateur, Long departementId) {
        if (utilisateur.isAdminOrRh()) {
            if (departementId != null && !repository.departmentExists(departementId)) {
                throw new RessourceIntrouvableException("Département introuvable (id=" + departementId + ")");
            }
            return departementId;
        }
        if (utilisateur.hasRole(Roles.MANAGER)) {
            if (departementId != null && !departementId.equals(utilisateur.getDepartementId())) {
                throw new AccessDeniedException("Un manager ne consulte que le tableau de bord de son département");
            }
            return utilisateur.getDepartementId();
        }
        // défense en profondeur : le contrôleur refuse déjà les autres rôles
        throw new AccessDeniedException("Tableau de bord réservé aux administrateurs, RH et managers");
    }

    /** Les 12 mois de l'année, y compris ceux sans embauche (0), pour un graphique continu. */
    private List<EmbauchesMois> hiresByMonth(Long perimetre, LocalDate debut, LocalDate fin) {
        Map<Integer, Long> parMois = new HashMap<>();
        for (Object[] ligne : repository.hiresByMonth(perimetre, debut, fin)) {
            parMois.put(((Number) ligne[0]).intValue(), toLong(ligne[1]));
        }
        return IntStream.rangeClosed(1, 12)
                .mapToObj(mois -> new EmbauchesMois(mois, parMois.getOrDefault(mois, 0L)))
                .toList();
    }

    /** Moyenne calculée sur les seuls salaires renseignés (un salaire vide ne compte pas pour 0). Dinars : 3 décimales. */
    private List<MasseSalarialeDepartement> payroll(Long perimetre) {
        return repository.payrollByDepartment(perimetre).stream().map(ligne -> {
            BigDecimal total = toBigDecimal(ligne[3]).setScale(3, RoundingMode.HALF_UP);
            long salairesRenseignes = toLong(ligne[4]);
            BigDecimal moyenne = salairesRenseignes == 0
                    ? BigDecimal.ZERO.setScale(3)
                    : total.divide(BigDecimal.valueOf(salairesRenseignes), 3, RoundingMode.HALF_UP);
            return new MasseSalarialeDepartement(toLong(ligne[0]), (String) ligne[1], toLong(ligne[2]),
                    total, moyenne);
        }).toList();
    }

    private int checkedYear(Integer annee, LocalDate aujourdhui) {
        int cible = annee != null ? annee : aujourdhui.getYear();
        if (cible < 2000 || cible > 2100) {
            throw new RequeteInvalideException("Année invalide : " + cible);
        }
        return cible;
    }

    private static long toLong(Object valeur) {
        return valeur == null ? 0 : ((Number) valeur).longValue();
    }

    private static BigDecimal toBigDecimal(Object valeur) {
        return valeur == null ? BigDecimal.ZERO : new BigDecimal(valeur.toString());
    }
}
