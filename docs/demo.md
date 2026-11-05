# Scénario de démonstration

Durée : 15 à 20 minutes. Tout le scénario a été exécuté sur une installation neuve (`docker compose`) avant d'être écrit ;
les réponses de l'assistant citées plus bas sont des réponses réelles du modèle.

## Préparation

### Une semaine avant : répétition sur le poste de démonstration

1. Installer Docker Desktop, Git, Ollama et Postman.
2. `git clone https://github.com/OmarAjroud/PPP2-rh-app.git`, puis `cd PPP2-rh-app`.
3. `ollama pull qwen3:4b-instruct` (≈ 2,5 Go : à télécharger chez soi, pas sur le Wi-Fi du jour J).
4. `cp .env.example .env` (ou copier le fichier à la main) et renseigner `JWT_SECRET`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`.
   Pour `JWT_SECRET`, dans PowerShell : `[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))`.
5. `docker compose up -d --build`, attendre que `docker compose ps` affiche `healthy` pour `backend`.
6. Dans Postman : **Import** des deux fichiers de `docs/postman/`, choisir l'environnement « RH App – local »,
   puis **Run collection** : les 35 requêtes doivent être vertes.
7. Dérouler le scénario ci-dessous une fois en entier, chronomètre en main.

### La veille

- `git pull` puis `docker compose up -d --build` (pour avoir la dernière version).
- Repartir de données propres : `docker compose down -v` puis `docker compose up -d --build`.

### Le jour J, 10 minutes avant

1. Démarrer Docker Desktop et vérifier qu'Ollama tourne (icône dans la barre des tâches).
2. `docker compose up -d` puis vérifier http://localhost:8080/actuator/health → `{"status":"UP"}`.
3. **Réchauffer l'assistant** : poser une première question (Postman, dossier 3) pour charger le modèle en mémoire :
   la première réponse est la plus lente.
4. Ouvrir les onglets : le dépôt GitHub, Swagger (http://localhost:8080/swagger-ui.html), Mailpit (http://localhost:8025),
   Postman, et une fenêtre PowerShell.

## Déroulé

### 1. Le projet en deux minutes (GitHub)

- Page d'accueil du dépôt : le badge **CI** vert, la liste des fonctionnalités, les comptes de démonstration.
- [Architecture](architecture.md) : le schéma d'ensemble et la matrice des droits.
- [Base de données](base-de-donnees.md) : le diagramme entité-relation et les règles garanties par MySQL.

> **Message** : une API complète, sécurisée, testée automatiquement et déployable en une commande.

### 2. L'API documentée (Swagger)

1. Ouvrir Swagger, `POST /api/auth/login` avec `{"nomUtilisateur": "rh", "motDePasse": "Test1234!"}`.
2. Copier `accessToken`, bouton **Authorize**, coller le jeton.
3. `GET /api/employes` : liste paginée, tri (`sort=nom`), recherche (`recherche=Lina Gharbi`).

> **Message** : chaque endpoint est documenté et testable directement ; le jeton expire au bout de 15 minutes.

### 3. Les droits selon le rôle (Postman, dossiers 0 et 1)

Lancer le dossier **0. Connexion**, puis montrer une à une les requêtes du dossier **1. Droits selon le rôle** :

| Requête | Ce qu'on voit |
|---|---|
| Employés vus par RH / par le manager / par l'employée | 4, puis 3 (son département), puis 1 (elle-même) |
| Fiche de Lina vue par RH puis par le manager | salaire `2500.00`, puis `null` |
| Création d'un rôle par RH | `403` : seul l'ADMIN gère les rôles |
| Tableau de bord RH puis manager | toute l'entreprise et la masse salariale ; puis son département, `masseSalariale: null` |
| Journal d'audit par RH | `403` : réservé à l'ADMIN |

> **Message** : la même requête donne des résultats différents selon qui la fait ; les règles sont appliquées par le serveur,
> pas par l'interface.

### 4. Le cycle d'une demande de congé, en direct (Postman dossier 2 + Mailpit + PowerShell)

**Avant de commencer**, dans la fenêtre PowerShell, ouvrir le flux de notifications de Lina (le ticket est valable
30 secondes : lancer les quatre lignes d'un coup) :

```powershell
$base = "http://localhost:8080"
$jeton = (Invoke-RestMethod -Method Post "$base/api/auth/login" -ContentType "application/json" -Body '{"nomUtilisateur":"employe","motDePasse":"Test1234!"}').accessToken
$ticket = (Invoke-RestMethod -Method Post "$base/api/notifications/flux/ticket" -Headers @{ Authorization = "Bearer $jeton" }).ticket
curl.exe -N "$base/api/notifications/flux?ticket=$ticket"
```

La fenêtre affiche aussitôt `event:non-lues` (le compteur de la cloche) et reste ouverte, en attente.

Puis, dans Postman, dossier **2. Cycle d'une demande de congé**, une requête à la fois :

1. **Aperçu** : nombre de jours ouvrables (week-ends et jours fériés exclus).
2. **Lina demande un congé** → `201`, `EN_ATTENTE`. Dans Mailpit : l'email « Nouvelle demande de congé » pour Karim.
3. **Même période une seconde fois** → `409` : pas de chevauchement.
4. **Karim : sa cloche** et **les demandes de son équipe** : la demande de Lina l'attend.
5. **Lina tente de valider** → `403`.
   **Sara (RH) tente de valider à la place de Karim** → `403` : « Cette demande est à traiter par Karim Mansour. Les RH
   pourront la traiter à partir du … ». Le manager décide d'abord ; les RH n'interviennent qu'en recours.
6. **Karim valide** → `APPROUVEE`. **Regarder la fenêtre PowerShell** : l'événement `notification` de Lina arrive
   en direct. Dans Mailpit : l'email « Demande de congé approuvée ».
7. **Solde** et **historique** de Lina : les jours sont déduits, mouvement `DEDUCTION`.
8. **Lina annule** (le congé n'a pas commencé) → historique : `REMBOURSEMENT`, les jours sont rendus ; Karim est prévenu.
9. **Admin : journal d'audit** de la demande : chaque changement avec l'ancienne et la nouvelle valeur.

> **Message** : solde, historique, notification et audit sont enregistrés dans une seule transaction ; les emails et
> la notification en direct ne partent qu'une fois la décision réellement enregistrée.

### 5. L'assistant RH (Postman dossier 3, ou Swagger `POST /api/assistant/chat`)

Corps de la requête : `{"messages": [{"role": "utilisateur", "contenu": "<question>"}]}`.
Sans carte graphique, compter 10 à 25 secondes par réponse : le dire avant de lancer la question.

Questions testées et réponses obtenues :

| Compte | Question | Réponse obtenue |
|---|---|---|
| `employe` | Combien de jours de congé annuel me reste-t-il ? | « Vous avez 22 jours de congé annuel disponibles pour l'année 2026. » |
| `manager` | Ai-je des demandes de congé à valider ? | la demande de Lina, avec le type, les dates et le nombre de jours |
| `manager` | Qui est en congé cette semaine en IT ? | les congés approuvés de son département sur la semaine |
| `rh` | Combien d'employés compte chaque département ? | Informatique 3, Ressources Humaines 1, Finance 0 |
| `rh` | Quelle est la masse salariale du département Informatique ? | « 11 700,00 €, moyenne 3 900,00 € par employé » |
| `rh` | Quel est le solde de congés de Lina Gharbi ? | alloués, pris, en attente, disponibles |
| `manager` | Quelle est la masse salariale de mon département ? | « … n'est pas communiquée à cet utilisateur. » (**refus voulu**) |
| `employe` | Quel est le salaire de Karim Mansour ? | information non disponible (**refus voulu**) |
| `employe` | Pose-moi un congé du 21 au 23 décembre. | renvoie vers l'écran de demande : l'assistant ne modifie rien |

Montrer ensuite, dans le journal d'audit (`admin`, filtre `action=APPEL_ASSISTANT`), que chaque outil appelé est enregistré.

> **Message** : le modèle ne voit que ce que l'utilisateur a le droit de voir, car il n'interroge pas la base : il appelle
> des outils qui passent par les mêmes règles que l'API. Il tourne en local (gratuit, données sur la machine) ; l'API Claude
> peut le remplacer par un simple réglage. Les mêmes outils sont proposés aux clients externes par le serveur MCP.

### 6. La qualité (GitHub)

- Onglet **Actions** : à chaque pull request, 299 tests unitaires et 39 tests d'intégration sur une vraie base MySQL, et
  la construction de l'image Docker.
- Une pull request : les vérifications vertes avant la fusion.

> **Message** : chaque modification est vérifiée automatiquement avant d'arriver sur `main`.

## Questions probables

| Question | Réponse |
|---|---|
| Les RH peuvent-elles décider à la place du manager ? | Non : le manager décide d'abord. Les RH décident en recours (pas de manager, manager indisponible, ou pas de décision après 3 jours : la demande leur est alors transmise automatiquement). |
| Et si un salarié rentre plus tôt de congé ? | Les RH interrompent le congé : il se termine la veille et les jours non pris sont rendus. |
| Comment couper l'accès d'un employé qui part ? | Désactivation de son compte : connexion refusée et jetons invalidés immédiatement, historique conservé. |
| Que se passe-t-il si deux managers valident en même temps ? | Le solde est verrouillé pendant la validation et la demande porte une version : la seconde décision reçoit `409`. |
| Peut-on deviner un mot de passe en essayant ? | Après 5 échecs pour un même compte depuis une même adresse en 15 minutes, ou 20 échecs depuis une adresse tous comptes confondus : refus temporaire (`429`). |
| Un jeton volé reste-t-il valable ? | Le jeton d'accès 15 minutes au plus. Le jeton de rafraîchissement ne sert qu'une fois : s'il est réutilisé (copie volée), toutes les sessions du compte sont fermées. Changer de mot de passe ou se déconnecter partout invalide tout immédiatement. |
| Pourquoi un modèle local plutôt qu'une API ? | Gratuit, sans clé, et les données RH ne quittent pas la machine. Claude reste disponible par configuration. |
| L'assistant peut-il se tromper ? | Oui, surtout un petit modèle, sur des questions inhabituelles. Mais il ne peut ni modifier de données ni voir ce que l'utilisateur n'a pas le droit de voir. |
| Pourquoi pas de front-end ? | Le back-end est terminé et testé ; le front-end Angular est la suite du projet, l'API est prête pour lui (CORS, SSE, erreurs uniformes). |
| Comment est-ce déployé ? | Une image Docker construite et testée par la CI, et un `docker compose` qui lance toute l'application. |

## En cas de problème

| Symptôme | Solution |
|---|---|
| L'assistant répond `503` | Ollama n'est pas démarré ou le modèle manque : lancer Ollama, puis `ollama pull qwen3:4b-instruct`. |
| `docker compose up` : « port is already allocated » | Changer `BACKEND_PORT` ou `MAILPIT_PORT` dans `.env`, ou arrêter le programme qui occupe le port. |
| `429` à la connexion | Trop d'essais manqués : attendre 15 minutes, ou `docker compose restart backend`. |
| La fenêtre PowerShell n'affiche rien | Le ticket a expiré (30 s) : relancer les quatre lignes d'un coup. |
| Données de démonstration modifiées | `docker compose down -v` puis `docker compose up -d --build` : retour aux données de départ. |
| Une requête Postman est rouge « 401 » | Le jeton a expiré (15 min) : relancer le dossier « 0. Connexion ». |
