# Application de Gestion des Ressources Humaines

Stage 2026 · Spring Boot + MySQL (+ Angular, à venir)

[![CI](https://github.com/OmarAjroud/PPP2-rh-app/actions/workflows/ci.yml/badge.svg)](https://github.com/OmarAjroud/PPP2-rh-app/actions/workflows/ci.yml)

API de gestion des ressources humaines : employés, départements et rôles, congés avec validation et soldes,
notifications en temps réel, tableau de bord, journal d'audit et assistant RH en langage naturel (modèle local ou Claude,
serveur MCP). Chaque fonctionnalité applique les droits de l'utilisateur connecté (ADMIN, RH, MANAGER, EMPLOYE).

## Démarrage rapide (Docker)

Prérequis : Docker Desktop, et [Ollama](https://ollama.com) pour l'assistant RH (facultatif).

```bash
git clone https://github.com/OmarAjroud/PPP2-rh-app.git && cd PPP2-rh-app
cp .env.example .env              # renseigner JWT_SECRET, DB_PASSWORD et MYSQL_ROOT_PASSWORD
ollama pull qwen3:4b-instruct     # facultatif : modèle de l'assistant (≈ 2,5 Go)
docker compose up -d --build
```

| Service | Adresse |
|---|---|
| API | http://localhost:8080 |
| Documentation de l'API (Swagger) | http://localhost:8080/swagger-ui.html |
| Emails envoyés (Mailpit) | http://localhost:8025 |
| État de santé | http://localhost:8080/actuator/health |

### Comptes de démonstration

Mot de passe commun : `Test1234!` (créés si `SEED_TEST_ACCOUNTS=true`, comme dans `.env.example`).

| Compte | Personne | Rôle | Particularité |
|---|---|---|---|
| `admin` | Amine Ben Salah | ADMIN | Tout, dont la gestion des rôles et le journal d'audit |
| `rh` | Sara Trabelsi | RH | Tous les employés, salaires, vue globale des congés |
| `manager` | Karim Mansour | MANAGER | Son département ; valide les congés de son équipe |
| `employe` | Lina Gharbi | EMPLOYE | Son profil et ses congés ; équipe de Karim |

## Fonctionnalités

| Domaine | Points clés |
|---|---|
| Employés, départements, rôles | CRUD, recherche et tri côté serveur, salaire visible des seuls profils autorisés |
| Comptes | Invitation par email, activation, mot de passe oublié, JWT + jeton de rafraîchissement, révocation au changement de mot de passe |
| Congés | Jours ouvrables (week-ends et jours fériés exclus), règles métier, validation par le manager, soldes et historique |
| Notifications | Enregistrées avec chaque action, emails, temps réel (SSE) |
| Tableau de bord | Indicateurs et graphiques calculés par la base, périmètre selon le rôle |
| Journal d'audit | Créations, modifications (anciennes/nouvelles valeurs), connexions ; consultation réservée à l'ADMIN |
| Assistant RH | Questions en langage naturel, 7 outils en lecture seule exécutés avec les droits de l'utilisateur, serveur MCP |

## Documentation

- [Back-end : API, configuration, tests, déploiement](backend/README.md)
- [Architecture](docs/architecture.md)
- [Base de données (diagramme entité-relation)](docs/base-de-donnees.md)
- [Scénario de démonstration](docs/demo.md)
- [Collection Postman](docs/postman/) (importer les deux fichiers dans Postman)

## Structure du dépôt

```
backend/              API Spring Boot (Java 21) : code, tests, Dockerfile
docs/                 architecture, base de données, démonstration, collection Postman
frontend/             application Angular (à venir)
docker-compose.yml    application complète (MySQL, Mailpit, back-end, Ollama en option)
.env.example          modèle de configuration (secrets, comptes de démonstration, assistant)
.github/workflows/    intégration continue (tests et image Docker à chaque pull request)
```

## Technologies

Java 21 · Spring Boot 4 (Web MVC, Data JPA, Security, Validation, Mail, Actuator) · MySQL 8.4 · Flyway · JWT ·
MapStruct · Thymeleaf (emails) · springdoc-openapi · Ollama / API Claude · SDK MCP Java ·
JUnit 5, Mockito, Testcontainers · Docker, Docker Compose · GitHub Actions
