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

    private final JourFerieRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<JourFerieResponse> findByYear(Integer annee) {
        int anneeCible = annee != null ? annee : LocalDate.now(clock).getYear();
        if (anneeCible < 2000 || anneeCible > 2100) {
            throw new RequeteInvalideException("Année invalide : " + anneeCible);
        }
        return repository.findByDateFerieBetweenOrderByDateFerie(
                        LocalDate.of(anneeCible, 1, 1), LocalDate.of(anneeCible, 12, 31))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public JourFerieResponse create(JourFerieRequest requete) {
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

    private JourFerieResponse toResponse(JourFerie jour) {
        return new JourFerieResponse(jour.getId(), jour.getDateFerie(), jour.getLibelle());
    }
}