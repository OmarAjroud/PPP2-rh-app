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

## Congés (partie 1 : demandes)

- Types : `GET /api/types-conge` (annuel, maladie, sans solde)
- Mes soldes : `GET /api/conges/soldes/moi`
- Aperçu du décompte : `GET /api/conges/demandes/jours-ouvrables?dateDebut=...&dateFin=...`
- Demander : `POST /api/conges/demandes` ; mes demandes : `GET /api/conges/demandes/moi` ; annuler : `POST /api/conges/demandes/{id}/annulation`
- Jours fériés : `GET /api/jours-feries?annee=2026` ; ajout/suppression réservés à RH et ADMIN
  (les fêtes religieuses changent chaque année : à saisir dès l'annonce officielle).
- Week-end configurable : `app.leave.weekend-days` (par défaut samedi et dimanche).

## Congés (partie 2 : validation)

- Manager : `GET /api/conges/demandes/equipe?statut=EN_ATTENTE` (son équipe = les employés dont il est le manager direct)
- Valider : `POST /api/conges/demandes/{id}/validation` ; refuser (motif obligatoire) : `POST /api/conges/demandes/{id}/refus`
- RH : vue globale `GET /api/conges/demandes` (filtres : statut, employeId, departementId, du, au)
- Annuler un congé approuvé qui n'a pas commencé : `POST /api/conges/demandes/{id}/annulation` (les jours sont rendus)
- Soldes : `GET /api/conges/soldes/{employeId}` ; historique : `GET /api/conges/soldes/{employeId}/mouvements`
- Ajustement RH : `POST /api/conges/soldes/{employeId}/ajustements` (commentaire obligatoire)
- Règles : nul ne valide sa propre demande ; une demande n'est visible que de son auteur, de son manager d'équipe, des RH et des administrateurs.

## Notifications (partie 1 : stockage, consultation, emails)

- Nouvelle demande de congé : notifie le manager direct ; s'il n'y en a pas, tous les RH ; à défaut, les administrateurs.
- Demande approuvée ou refusée : notifie le demandeur (avec le commentaire ou le motif).
- Demande annulée : notifie les mêmes valideurs, ainsi que la personne qui l'avait approuvée.
- Personne n'est jamais notifié de sa propre action. La notification est enregistrée dans la même transaction que l'action.
- Mes notifications : `GET /api/notifications?nonLues=true&page=0&size=10`
- Compteur de la cloche (à interroger régulièrement, ex. toutes les 30 s) : `GET /api/notifications/non-lues/nombre`
- Marquer comme lue : `POST /api/notifications/{id}/lecture` ; tout marquer comme lu : `POST /api/notifications/lecture`
- Un email accompagne chaque notification (lien vers `FRONTEND_URL`) ; à désactiver avec `NOTIFICATIONS_EMAIL_ENABLED=false`.

## Notifications (partie 2 : temps réel avec SSE)

`EventSource` (navigateur) ne peut pas envoyer d'en-tête `Authorization` : on échange d'abord le JWT contre un ticket.

1. `POST /api/notifications/flux/ticket` (avec le JWT) → `{"ticket": "...", "expireDans": 30}` : usage unique, 30 secondes.
2. `new EventSource('/api/notifications/flux?ticket=...')` (un client capable d'envoyer des en-têtes peut aussi passer le JWT en `Authorization`).

Événements reçus :
- `non-lues` : `{"nombre": 3}`, dès l'ouverture (la cloche est juste même après une reconnexion), puis à chaque lecture depuis un autre onglet ;
- `notification` : `{"notification": {...}, "nombreNonLues": 4}` à chaque nouvelle notification ;
- un commentaire `:ping` toutes les 25 secondes (garde la connexion ouverte à travers les proxys).

Le flux se ferme après la durée de vie d'un jeton d'accès (15 min) ; le client redemande alors un ticket et se reconnecte.
Un compte désactivé ou un mot de passe changé perd donc le temps réel en 15 minutes au plus. Au plus 5 flux par utilisateur.
Les tickets et les connexions sont gardés en mémoire : valable pour une seule instance de l'application.
Les événements ne partent qu'après validation de la transaction ; le polling de la partie 1 reste disponible en secours.

## Tableau de bord

`GET /api/tableau-de-bord?annee=2026&departementId=1` (les deux paramètres sont facultatifs ; année en cours par défaut).

- Indicateurs : nombre d'employés, demandes de congé en attente, embauches du mois en cours, absents aujourd'hui (congé approuvé).
- Graphiques : effectif par département, embauches par mois (les 12 mois, 0 compris), répartition des congés approuvés de l'année par type,
  masse salariale par département (total, moyenne sur les salaires renseignés, effectif).
- ADMIN / RH : toute l'entreprise, ou un département au choix. MANAGER : son département uniquement (403 pour un autre), sans masse salariale (`null`).
  EMPLOYE : 403.

## Assistant RH (Claude + MCP)

Outils en lecture seule, partagés par le chat et le serveur MCP : `lister_departements`, `rechercher_employes`, `consulter_employe`,
`statistiques_departement`, `solde_conges`, `demandes_en_attente`, `absences_periode`.

- **Droits** : chaque outil appelle le même service que l'écran correspondant, avec l'utilisateur connecté. Mêmes règles que l'API :
  un manager reste dans son département, le salaire n'est visible que des ADMIN / RH (et de l'intéressé), un employé ne voit que lui-même.
- **Journal d'audit** : chaque appel d'outil est enregistré (action `APPEL_ASSISTANT`, entité `AssistantRH`) avec l'outil, la source (CHAT ou MCP),
  les arguments et l'éventuelle erreur.

### Chat dans l'application

- `POST /api/assistant/chat` : `{"messages": [{"role": "utilisateur", "contenu": "Qui est en congé la semaine prochaine en informatique ?"}]}`
  → `{"reponse": "...", "outilsUtilises": ["lister_departements", "absences_periode"]}`.
  L'API ne garde pas d'historique : le front-end renvoie toute la conversation (20 messages au plus).
- `GET /api/assistant/statut` : moteur, modèle, disponibilité (pour afficher ou masquer le chat).

**Moteur par défaut : un modèle local avec [Ollama](https://ollama.com)** (gratuit, sans clé, les données RH ne quittent pas la machine).

```bash
winget install Ollama.Ollama          # puis rouvrir le terminal ; Ollama démarre en arrière-plan
ollama pull qwen3:4b-instruct         # ~2,5 Go, à télécharger avant la démonstration
```

- Modèle : `OLLAMA_MODEL` (par défaut `qwen3:4b-instruct`, la variante *sans réflexion* : rapide, même sans carte graphique ;
  la variante `qwen3:4b` raisonne longuement avant chaque réponse). Adresse : `OLLAMA_URL` (par défaut `http://localhost:11434`).
- Le modèle est préchargé au démarrage de l'application et reste en mémoire 30 minutes entre deux questions.
- Temps de réponse indicatifs : ~1 à 2 s avec une carte graphique NVIDIA ; 10 à 25 s sur un portable sans carte graphique.
- Ollama arrêté ou modèle absent : le chat répond 503 avec la commande à lancer ; le reste de l'application fonctionne.

**Moteur optionnel : l'API Claude** (`ASSISTANT_PROVIDER=claude`, clé `ANTHROPIC_API_KEY`, usage facturé), plus fiable sur les questions complexes.
`CLAUDE_MODEL` (par défaut `claude-opus-5-5`), `CLAUDE_EFFORT` (par défaut `medium`). Si le modèle refuse une demande,
l'API la relance sur un modèle de repli (`fallbacks: default`).

### Serveur MCP

`POST /mcp` (MCP « streamable HTTP », mode sans état), avec le même JWT que le reste de l'API. Exemple de configuration d'un client MCP :

```json
{ "mcpServers": { "rh": { "type": "http", "url": "http://localhost:8080/mcp",
                          "headers": { "Authorization": "Bearer <jeton d'accès>" } } } }
```

Le jeton d'accès expire au bout de 15 minutes : à renouveler dans la configuration du client.
