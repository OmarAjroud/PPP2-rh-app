package com.hrapp.rh.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AuditRepository {

    private static final String INSERT = """
            INSERT INTO journal_audit
                (date_action, utilisateur_id, nom_utilisateur, action, entite, entite_id,
                 anciennes_valeurs, nouvelles_valeurs, details, adresse_ip)
            VALUES
                (:dateAction, :utilisateurId, :nomUtilisateur, :action, :entite, :entiteId,
                 :anciennes, :nouvelles, :details, :adresseIp)
            """;

    private static final String SELECT = """
            SELECT id, date_action, utilisateur_id, nom_utilisateur, action, entite, entite_id,
                   anciennes_valeurs, nouvelles_valeurs, details, adresse_ip
            FROM journal_audit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void insert(AuditRecord ligne) {
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("dateAction", ligne.dateAction())
                .addValue("utilisateurId", ligne.utilisateurId())
                .addValue("nomUtilisateur", ligne.nomUtilisateur())
                .addValue("action", ligne.action().name())
                .addValue("entite", ligne.entite())
                .addValue("entiteId", ligne.entiteId())
                .addValue("anciennes", ligne.anciennesValeurs())
                .addValue("nouvelles", ligne.nouvellesValeurs())
                .addValue("details", ligne.details())
                .addValue("adresseIp", ligne.adresseIp()));
    }

    /** Recherche filtrée, la plus récente d'abord. Les valeurs de filtre sont toujours des paramètres liés. */
    public Page<AuditEntryResponse> search(AuditFilter filtre, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filtre.nomUtilisateur() != null && !filtre.nomUtilisateur().isBlank()) {
            where.append(" AND LOWER(nom_utilisateur) LIKE :nom");
            params.addValue("nom", "%" + filtre.nomUtilisateur().trim().toLowerCase() + "%");
        }
        if (filtre.action() != null) {
            where.append(" AND action = :action");
            params.addValue("action", filtre.action().name());
        }
        if (filtre.entite() != null && !filtre.entite().isBlank()) {
            where.append(" AND entite = :entite");
            params.addValue("entite", filtre.entite().trim());
        }
        if (filtre.entiteId() != null && !filtre.entiteId().isBlank()) {
            where.append(" AND entite_id = :entiteId");
            params.addValue("entiteId", filtre.entiteId().trim());
        }
        if (filtre.du() != null) {
            where.append(" AND date_action >= :du");
            params.addValue("du", filtre.du().atStartOfDay());
        }
        if (filtre.au() != null) {
            where.append(" AND date_action < :au");                     // « au » inclus : jusqu'à minuit le lendemain
            params.addValue("au", filtre.au().plusDays(1).atStartOfDay());
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM journal_audit" + where, params, Long.class);

        params.addValue("limite", pageable.getPageSize());
        params.addValue("decalage", pageable.getOffset());
        List<AuditEntryResponse> lignes = jdbc.query(
                SELECT + where + " ORDER BY date_action DESC, id DESC LIMIT :limite OFFSET :decalage",
                params, this::mapRow);

        return new PageImpl<>(lignes, pageable, total == null ? 0 : total);
    }

    /** Types d'entités déjà présents dans le journal (pour alimenter un filtre). */
    public List<String> listEntities() {
        return jdbc.getJdbcTemplate()
                .queryForList("SELECT DISTINCT entite FROM journal_audit ORDER BY entite", String.class);
    }

    // ---------- méthodes privées ----------

    private AuditEntryResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEntryResponse(
                rs.getLong("id"),
                rs.getObject("date_action", LocalDateTime.class),
                rs.getObject("utilisateur_id", Long.class),
                rs.getString("nom_utilisateur"),
                rs.getString("action"),
                rs.getString("entite"),
                rs.getString("entite_id"),
                toJson(rs.getString("anciennes_valeurs")),
                toJson(rs.getString("nouvelles_valeurs")),
                rs.getString("details"),
                rs.getString("adresse_ip"));
    }

    private JsonNode toJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            return null;
        }
    }
}
