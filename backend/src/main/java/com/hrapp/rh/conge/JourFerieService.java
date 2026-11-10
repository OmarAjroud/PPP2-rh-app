package com.hrapp.rh.conge;

import com.hrapp.rh.common.exception.ConflitException;
import com.hrapp.rh.common.exception.RequeteInvalideException;
import com.hrapp.rh.common.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JourFerieService {

    static final int ANNEE_MIN = 2000;
    static final int ANNEE_MAX = 2100;

    private final JourFerieRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<JourFerieResponse> findByYear(Integer annee) {
        int anneeCible = annee != null ? annee : LocalDate.now(clock).getYear();
        verifierAnnee(anneeCible);
        return repository.findByDateFerieBetweenOrderByDateFerie(
                        LocalDate.of(anneeCible, 1, 1), LocalDate.of(anneeCible, 12, 31))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public JourFerieResponse create(JourFerieRequest requete) {
        verifierAnnee(requete.date().getYear());
        if (repository.existsByDateFerie(requete.date())) {
            throw new ConflitException("Un jour férié existe déjà à cette date");
        }
        JourFerie jour = new JourFerie();
        jour.setDateFerie(requete.date());
        jour.setLibelle(requete.libelle().trim());
        return toResponse(repository.save(jour));
    }

    @Transactional
    public void delete(Long id) {
        JourFerie jour = repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Jour férié introuvable (id=" + id + ")"));
        repository.delete(jour);
    }

    /** Same range for reading and writing: a holiday saved outside it could never be listed nor deleted. */
    private static void verifierAnnee(int annee) {
        if (annee < ANNEE_MIN || annee > ANNEE_MAX) {
            throw new RequeteInvalideException("Année invalide : " + annee + " (entre " + ANNEE_MIN + " et " + ANNEE_MAX + ")");
        }
    }

    private JourFerieResponse toResponse(JourFerie jour) {
        return new JourFerieResponse(jour.getId(), jour.getDateFerie(), jour.getLibelle());
    }
}