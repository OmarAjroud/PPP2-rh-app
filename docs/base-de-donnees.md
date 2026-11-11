# Base de données

MySQL 8.4. Le schéma est créé et mis à jour uniquement par les migrations Flyway
(`backend/src/main/resources/db/migration`, de `V1` à `V7`) ; Hibernate se contente de le vérifier au démarrage
(`ddl-auto: validate`). Ce diagramme reflète l'état après la dernière migration.

## Diagramme entité-relation

```mermaid
erDiagram
    ROLES ||--o{ EMPLOYES : "a pour rôle"
    DEPARTEMENTS ||--o{ EMPLOYES : "appartient à"
    EMPLOYES |o--o{ EMPLOYES : "est le manager de"
    EMPLOYES ||--o| UTILISATEURS : "se connecte avec"
    UTILISATEURS ||--o{ JETONS_UTILISATEUR : "reçoit"

    EMPLOYES ||--o{ DEMANDES_CONGE : "demande"
    EMPLOYES |o--o{ DEMANDES_CONGE : "décide"
    TYPES_CONGE ||--o{ DEMANDES_CONGE : "est de type"
    EMPLOYES ||--o{ SOLDES_CONGE : "dispose de"
    TYPES_CONGE ||--o{ SOLDES_CONGE : "concerne"
    SOLDES_CONGE ||--o{ MOUVEMENTS_SOLDE : "historique"
    DEMANDES_CONGE |o--o{ MOUVEMENTS_SOLDE : "provoque"
    EMPLOYES |o--o{ MOUVEMENTS_SOLDE : "enregistre"

    EMPLOYES ||--o{ NOTIFICATIONS : "reçoit"

    ROLES {
        bigint id PK
        varchar nom UK "ADMIN, RH, MANAGER, EMPLOYE..."
        varchar description
    }
    DEPARTEMENTS {
        bigint id PK
        varchar nom UK
        varchar description
    }
    EMPLOYES {
        bigint id PK
        varchar nom
        varchar prenom
        varchar email UK
        varchar telephone "30 - obligatoire à la saisie depuis V10"
        date date_embauche
        decimal salaire "11,3 - dinars tunisiens, facultatif"
        bigint departement_id FK
        bigint role_id FK
        bigint manager_id FK "facultatif"
    }
    UTILISATEURS {
        bigint id PK
        bigint employe_id FK "UK - un compte par employé"
        varchar nom_utilisateur UK
        varchar mot_de_passe_hash "BCrypt - vide tant que non activé"
        boolean actif
        int version_jeton "incrémentée = anciens JWT révoqués"
    }
    JETONS_UTILISATEUR {
        bigint id PK
        bigint utilisateur_id FK
        varchar type_jeton "ACTIVATION, REINITIALISATION"
        varchar jeton_hash UK "SHA-256, jamais le jeton"
        datetime date_creation
        datetime date_expiration
        datetime date_utilisation "usage unique"
    }
    TYPES_CONGE {
        bigint id PK
        varchar code UK "ANNUEL, MALADIE, SANS_SOLDE"
        varchar libelle
        boolean deduit_du_solde
        decimal jours_par_an "5,1"
        boolean retroactif_autorise
    }
    SOLDES_CONGE {
        bigint id PK
        bigint employe_id FK "UK avec type et année"
        bigint type_conge_id FK
        int annee
        decimal jours_alloues "5,1 - >= 0"
        decimal jours_pris "5,1 - >= 0"
    }
    DEMANDES_CONGE {
        bigint id PK
        bigint employe_id FK
        bigint type_conge_id FK
        date date_debut
        date date_fin "date_fin >= date_debut"
        decimal nombre_jours "jours ouvrables, > 0"
        varchar commentaire
        varchar statut "EN_ATTENTE, APPROUVEE, REFUSEE, ANNULEE"
        datetime date_creation
        bigint decide_par_id FK
        datetime date_decision
        varchar motif_decision
        bigint version "verrou optimiste"
    }
    MOUVEMENTS_SOLDE {
        bigint id PK
        bigint solde_id FK
        bigint demande_id FK "facultatif"
        varchar type_mouvement "ALLOCATION, DEDUCTION, REMBOURSEMENT, AJUSTEMENT"
        decimal jours "signé"
        datetime date_mouvement
        varchar commentaire
        bigint cree_par_id FK
    }
    NOTIFICATIONS {
        bigint id PK
        bigint destinataire_id FK "suppression en cascade"
        varchar type "CONGE_DEMANDE, CONGE_APPROUVE..."
        varchar titre
        varchar message
        varchar lien "chemin dans le front-end"
        boolean lue
        datetime date_creation
        datetime date_lecture
    }
```

Deux tables sont volontairement **sans clé étrangère** :

```mermaid
erDiagram
    JOURNAL_AUDIT {
        bigint id PK
        datetime date_action "à la milliseconde"
        bigint utilisateur_id "sans FK - le journal survit à la suppression du compte"
        varchar nom_utilisateur
        varchar action "CREATION, MODIFICATION, CONNEXION, APPEL_ASSISTANT..."
        varchar entite
        varchar entite_id
        json anciennes_valeurs
        json nouvelles_valeurs
        varchar details
        varchar adresse_ip
    }
    JOURS_FERIES {
        bigint id PK
        date date_ferie UK
        varchar libelle
    }
```

## Règles garanties par la base

| Règle | Mécanisme |
|---|---|
| Email d'employé, nom de rôle et de département uniques | contraintes `UNIQUE` |
| Un seul compte par employé | `utilisateurs.employe_id UNIQUE` |
| Un seul solde par employé, type de congé et année | `uk_solde (employe_id, type_conge_id, annee)` |
| Jamais de solde négatif | `CHECK (jours_alloues >= 0 AND jours_pris >= 0)` |
| Dates de congé cohérentes, au moins un jour | `CHECK (date_fin >= date_debut)`, `CHECK (nombre_jours > 0)` |
| Deux décisions simultanées sur une même demande | colonne `version` (verrou optimiste JPA : la seconde reçoit 409) |
| Jetons d'activation et de réinitialisation | seule l'empreinte SHA-256 est stockée ; supprimés avec le compte (`ON DELETE CASCADE`) |
| Historique d'un solde | la somme des mouvements est toujours égale à `jours_alloues - jours_pris` |

## Index

| Table | Index | Sert à |
|---|---|---|
| `demandes_conge` | `(employe_id, date_debut, date_fin)`, `(statut)` | détection des chevauchements, listes filtrées |
| `journal_audit` | `(date_action)`, `(nom_utilisateur)`, `(action)`, `(entite, entite_id)` | filtres de l'écran d'audit |
| `notifications` | `(destinataire_id, lue, date_creation)` | compteur de la cloche, interrogé régulièrement |
| `mouvements_solde` | `(solde_id)` | historique d'un solde |
| `jetons_utilisateur` | `(utilisateur_id)` | invalidation des liens en attente |

## Migrations

| Version | Contenu |
|---|---|
| V1 | Rôles, départements, employés, utilisateurs |
| V2 | Données de départ : 4 rôles, 3 départements, 4 employés |
| V3 | Comptes sans mot de passe (invitation), version des jetons, jetons d'activation / réinitialisation |
| V4 | Journal d'audit |
| V5 | Types de congé, soldes, jours fériés (2026-2028), demandes, mouvements de solde |
| V6 | Reprise : mouvement d'allocation initiale pour les soldes existants |
| V7 | Notifications |
