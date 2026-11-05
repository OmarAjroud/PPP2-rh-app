# Architecture

## Vue d'ensemble

```mermaid
flowchart LR
    subgraph Clients
        FE["Front-end Angular<br/>(à venir)"]
        SW["Swagger UI / Postman"]
        MCPC["Client MCP<br/>(Claude Desktop, IDE…)"]
    end

    subgraph Docker["docker compose"]
        subgraph API["Back-end Spring Boot (Java 21)"]
            SEC["Sécurité<br/>filtre JWT · @PreAuthorize<br/>limite des connexions"]
            REST["API REST /api/**"]
            SSE["Flux SSE<br/>/api/notifications/flux"]
            MCP["Serveur MCP<br/>/mcp"]
            SVC["Services métier<br/>(règles d'accès, transactions)"]
            AUD["Journal d'audit<br/>(écouteurs Hibernate)"]
        end
        DB[("MySQL 8.4<br/>schéma Flyway")]
        MAIL["Mailpit<br/>(SMTP de test)"]
    end

    LLM["Modèle de langage<br/>Ollama local (défaut)<br/>ou API Claude"]

    FE -->|"HTTPS + JWT"| SEC
    SW -->|"JWT"| SEC
    MCPC -->|"JSON-RPC + JWT"| SEC
    FE -.->|"EventSource + ticket"| SSE
    SEC --> REST
    SEC --> MCP
    REST --> SVC
    MCP --> SVC
    SVC -->|"JPA"| DB
    AUD -->|"JDBC, même transaction"| DB
    SVC -->|"après validation"| MAIL
    SVC -->|"après validation"| SSE
    SVC -->|"chat : outils"| LLM
```

- **Une seule application** (monolithe modulaire), découpée par fonctionnalité : `employe`, `departement`, `role`,
  `utilisateur`, `auth`, `conge`, `notification`, `tableaudebord`, `audit`, `assistant`, `mail`, `securite`.
- **Sans état** : aucune session côté serveur ; chaque requête porte son jeton JWT. On peut donc redémarrer
  l'application sans déconnecter personne (seuls les flux SSE ouverts se reconnectent).
- **La base est la source de vérité** : les règles critiques (unicité, soldes positifs, dates) sont aussi des contraintes
  MySQL, et les calculs (tableau de bord) sont faits par des requêtes d'agrégation. Voir [la base de données](base-de-donnees.md).

## Organisation du code

Chaque package suit la même structure en couches :

```mermaid
flowchart TB
    C["Controller<br/>HTTP, validation (@Valid), @PreAuthorize"] --> S
    S["Service<br/>règles métier et d'accès, @Transactional"] --> R
    S --> M["Mapper (MapStruct)<br/>entité → DTO"]
    R["Repository (Spring Data JPA)<br/>requêtes, Specifications, verrous"] --> E["Entité JPA"]
    S -. "événements" .-> L["Écouteurs<br/>notifications, emails, SSE"]
```

| Couche | Rôle | Exemple |
|---|---|---|
| Contrôleur | Reçoit la requête, valide le JSON, vérifie le rôle | `DemandeCongeController` |
| Service | Règles métier **et** règles d'accès fines (périmètre du manager, salaire masqué…) | `DemandeCongeService` |
| Repository | Accès aux données, recherches dynamiques, verrous | `DemandeCongeRepository` |
| DTO | Ce que l'API renvoie : jamais les entités elles-mêmes | `DemandeCongeResponse` |
| Événements | Ce qui découle d'une action sans l'alourdir | `DemandeCongeEvenement` → notifications |

Les erreurs sont centralisées (`GestionnaireExceptionsGlobal`) et renvoyées dans un format unique (`ErreurApi`) :
400 données invalides, 401 non authentifié, 403 interdit, 404 introuvable (aussi pour ce qu'on n'a pas le droit de voir),
405 mauvaise méthode HTTP, 415 corps qui n'est pas du JSON,
409 conflit métier, 429 trop de tentatives, 503 service externe indisponible.

## Sécurité

| Mécanisme | Détail |
|---|---|
| Authentification | JWT signé (HS256) : jeton d'accès 15 min + jeton de rafraîchissement 7 jours ; mots de passe BCrypt |
| Révocation | changer de mot de passe incrémente `version_jeton` : tous les jetons émis avant sont refusés |
| Autorisation | `@PreAuthorize` par rôle sur les contrôleurs, puis règles fines dans les services |
| Comptes | invitation par email ; liens d'activation et de réinitialisation à usage unique (empreinte SHA-256 en base) |
| Connexion | 429 après 5 échecs pour un même compte depuis une même adresse IP en 15 minutes |
| Données sensibles | salaire visible des seuls ADMIN/RH et de l'intéressé ; une ressource interdite est « introuvable » (404) |
| Audit | toute création, modification ou suppression d'une entité `@Audited`, avec les anciennes et nouvelles valeurs |
| Production | secrets uniquement par variables d'environnement ; refus de démarrer avec le secret de développement |

Matrice des droits (cahier des charges) :

| Fonctionnalité | ADMIN | RH | MANAGER | EMPLOYÉ |
|---|---|---|---|---|
| Employés : consulter | Tous | Tous | Son département | Son profil |
| Employés : ajouter / modifier / supprimer | Oui | Oui (sauf ADMIN) | — | — |
| Salaires | Oui | Oui | — | Le sien |
| Départements : consulter / gérer | Oui / Oui | Oui / Oui | Oui / — | Oui / — |
| Rôles : gérer | Oui | — | — | — |
| Congés : demander | Oui | Oui | Oui | Oui |
| Congés : valider / refuser | En recours¹ | En recours¹ | Son équipe | — |
| Tableau de bord | Complet | Complet | Son département | — |
| Journal d'audit | Oui | — | — | — |
| Assistant RH | Selon ses droits | Selon ses droits | Selon ses droits | Selon ses droits |

¹ **Le manager d'abord, les RH en recours** : les RH et l'ADMIN voient toutes les demandes, mais ne décident que si
l'employé n'a pas de manager, si son manager ne peut pas agir (compte désactivé, plus de rôle MANAGER), ou si la demande
attend depuis plus de 3 jours (`app.leave.escalation-days`) : elle leur est alors transmise automatiquement, avec un rappel
au manager. Les RH peuvent aussi interrompre un congé approuvé (retour anticipé), motif obligatoire.

## Exemple : la validation d'un congé

```mermaid
sequenceDiagram
    actor K as Karim (manager)
    participant API as Back-end
    participant DB as MySQL
    participant N as Notifications
    participant SMTP as Mailpit
    actor L as Lina (navigateur)

    K->>API: POST /api/conges/demandes/12/validation (JWT)
    API->>API: filtre JWT → @PreAuthorize MANAGER
    API->>DB: demande visible par Karim ? (manager direct de Lina)
    API->>DB: verrou sur le solde (SELECT … FOR UPDATE), revérification
    API->>DB: jours_pris += 3, mouvement DEDUCTION, statut APPROUVEE
    API->>N: événement APPROUVEE (même transaction)
    N->>DB: notification pour Lina
    API->>DB: COMMIT (+ ligne d'audit MODIFICATION)
    API-->>K: 200 demande approuvée
    Note over API,SMTP: après validation seulement, en arrière-plan
    API->>SMTP: email « Demande de congé approuvée »
    API-->>L: SSE event: notification (+ compteur de la cloche)
```

Tout ce qui doit être cohérent (solde, historique, notification, audit) est écrit **dans une seule transaction** :
soit tout réussit, soit rien n'est enregistré. Ce qui part vers l'extérieur (email, poussée SSE) n'est envoyé
**qu'après** la validation, pour ne jamais annoncer une décision qui aurait été annulée.

## Exemple : une question à l'assistant RH

```mermaid
sequenceDiagram
    actor K as Karim (manager)
    participant API as Back-end
    participant LLM as Modèle (Ollama)
    participant O as Outils RH
    participant S as Services métier

    K->>API: POST /api/assistant/chat « Qui est en congé la semaine prochaine en IT ? »
    API->>LLM: consignes (règles, dates de la semaine, départements) + question + 7 outils
    LLM-->>API: appel d'outil absences_periode(du=…, au=…, departementId=1)
    API->>O: exécuter avec l'utilisateur Karim
    O->>S: DemandeCongeService (périmètre : son département)
    S-->>O: congés approuvés
    O->>O: ligne d'audit APPEL_ASSISTANT
    O-->>API: résultat JSON
    API->>LLM: résultat de l'outil
    LLM-->>API: « Lina Gharbi sera absente du 9 au 13 novembre. »
    API-->>K: réponse + outils utilisés
```

- Le modèle **ne voit que ce que l'utilisateur a le droit de voir** : il n'accède jamais à la base, seulement aux outils,
  qui appellent les mêmes services que l'API avec l'utilisateur connecté.
- Les outils sont **en lecture seule** : l'assistant ne peut rien modifier.
- Le même ensemble d'outils est exposé aux clients externes par le **serveur MCP** (`/mcp`), avec le même JWT.
- Moteur au choix : **Ollama** (modèle local gratuit, données sur la machine) ou **l'API Claude** (`ASSISTANT_PROVIDER`).

## Déploiement

```mermaid
flowchart LR
    DEV["Poste de développement<br/>git push"] --> GH["GitHub<br/>pull request"]
    GH --> CI["GitHub Actions<br/>./mvnw verify (Testcontainers)<br/>docker build"]
    CI -->|"vert : fusion"| MAIN["main"]
    MAIN --> PC["Poste de démonstration<br/>git pull · docker compose up -d --build"]
```

| Environnement | Lancement | Configuration |
|---|---|---|
| Développement | `backend/docker-compose.yml` (MySQL, Mailpit) + application lancée depuis l'IDE | `application.yml` (valeurs de développement) |
| Démonstration / production | `docker-compose.yml` à la racine | profil `prod` + fichier `.env` |
| Tests d'intégration | Testcontainers (MySQL éphémère) | propriétés de test, base reconstruite avant chaque test |
