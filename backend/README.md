# Application de Gestion des Ressources Humaines

Stage 2026 · Angular + Spring Boot + MySQL

## Structure
- `backend/` : API Spring Boot
- `frontend/` : application Angular (à venir)
- `docs/` : diagrammes (ERD, architecture)

## Démarrage rapide
```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run
```
## Cycle de vie d'un compte

1. RH/ADMIN : `POST /api/employes` (création de l'employé)
2. RH/ADMIN : `POST /api/employes/{id}/invitation` (email d'activation envoyé à l'employé)
3. L'employé ouvre le lien et choisit son mot de passe : `POST /api/auth/activation`
4. Mot de passe oublié : `POST /api/auth/mot-de-passe-oublie` puis `POST /api/auth/reinitialisation`
5. Changer son mot de passe : `PUT /api/auth/mot-de-passe` (les autres sessions sont fermées)

Les liens reçus par email pointent vers `FRONTEND_URL` (voir la section Configuration).