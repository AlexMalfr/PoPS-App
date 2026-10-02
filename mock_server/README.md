# Simulateur Oasis

Serveur Python 3.10+ sans dépendance, compatible avec les lectures du client PoPS et accompagné d’un atelier web pour gérer les données. Les données initiales sont synthétiques ; aucun identifiant du vrai Oasis n’est utilisé par le simulateur.

```powershell
py -3 .\mock_server\mock_oasis_server.py --host 0.0.0.0 --port 8080
```

Ouvrir [l’administration](http://127.0.0.1:8080/admin) sur le PC et [l’espace étudiant](http://127.0.0.1:8080/). Si le port est occupé, choisir `--port 8081`. L’émulateur utilise `http://10.0.2.2:8080/`, un téléphone `http://<IPv4-du-PC>:8080/`. L’API étudiante est accessible sur le LAN ; l’administration est réservée à la boucle locale par défaut.

Deux comptes sont créés **uniquement si le fichier de données n’existe pas** : `demo` / `demo` (Camille Martin) et `lea` / `lea` (Léa Dubois). Leurs notes sont différentes. Un autre login ou un mauvais mot de passe est rejeté.

## Atelier web

- **Compte & profil** : création, activation, désactivation et suppression d’un compte ; login, mot de passe, code étudiant et nom affiché ; tous les champs personnels retrouvés dans les rapports (coordonnées, urgence, fichiers, handicap, consentements) et accueil.
- **Notes** : années et semestres, UEs, matières, épreuves, notes non publiées (`null`), coefficients, moyennes, rangs, appréciations, ECTS, décisions et motifs de jury. Créer l’UE, puis la matière, puis l’épreuve ; leurs relations sont vérifiées à l’enregistrement.
- **Cursus** : champs, quitus et historique de scolarité sous forme de tableaux extensibles ; bilans annuels.
- **Choix** : inscriptions aux UEs et matières, options et attributs Oasis par année/semestre.
- **Documents** : joindre, télécharger et retirer les fichiers ; modifier leurs métadonnées et toutes les rubriques de documents. Maximum 5 Mo par fichier. Une photo PNG attachée à `STUDENT`, champ `PHOTO`, est disponible sur la route de photo attendue par PoPS.
- **Réponses API** : remplacer une route pour un compte, avec méthode, paramètres de sélection, statut, type MIME et corps HTML/JSON. Ce mécanisme permet de tester de nouveaux contrats avant leur implémentation. Les fichiers et l’authentification sont gérés séparément.
- **JSON complet** : modifier tous les champs supplémentaires d’un compte ; ils sont conservés au fil des enregistrements. Le mot de passe du mock est visible et éditable en clair, comme dans Compte & profil.
- **Réglages du serveur** : plateforme fermée, blocage IP manuel, messages d’erreur, durée des sessions, latence et panne HTTP.
- **Sessions & activité** : expirer les sessions, réinitialiser les tentatives de connexion et consulter les 200 derniers événements, sans login, mot de passe, cookie ou contenu de requête.
- **Couverture des routes** : inventaire de 62 routes découvertes dans les anciens rapports et indication de leur prise en charge.

Les modifications restent dans le formulaire jusqu’à **Enregistrer**. Une révision empêche un formulaire ancien d’écraser silencieusement une modification faite ailleurs. Les moyennes et rangs sont édités explicitement : les règles complètes de calcul de l’école restent à confirmer.

Les trois scénarios historiques `initial`, `new_note` et `updated_note` restent disponibles. Depuis l’atelier ils s’appliquent au seul compte sélectionné et remplacent ses résultats **2025/2026**, en conservant ses autres années et données. Depuis l’API, omettre `account_id` applique le scénario à tous les comptes.

## Connexion et persistance

Les mots de passe du mock sont stockés et affichés en clair dans l’administration, les JSON et les sauvegardes, conformément au choix de développement. Le cookie `PHPSESSID` utilise un identifiant aléatoire, `HttpOnly` et `SameSite=Lax`. Les sessions sont propres au compte, expirent (une heure par défaut) et cessent de fonctionner après désactivation, suppression ou changement des identifiants. Un étudiant ne peut pas récupérer les notes ou fichiers d’un autre étudiant.

Après le premier échec, la réponse est `{"text":"Vos identifiants sont incorrects. Veuillez réessayer (9 tentatives restantes).","success":false,"reload":"no"}`. Le compteur est lié à l’adresse IP ; le dixième échec bloque les connexions jusqu’au changement de journée locale du serveur. Une connexion réussie remet le compteur à zéro. Le mode fermé provoque aussi ce décompte, même avec les bons identifiants. Les compteurs et sessions sont en mémoire : un redémarrage les efface. Le bouton de réinitialisation efface les blocages automatiques ; le réglage de blocage manuel reste indépendant.

Les données et réglages sont écrits atomiquement dans `mock_server/data/state.json`, ignoré par Git. `--data C:\chemin\state.json` sélectionne un autre jeu de données. Un fichier invalide provoque un échec au démarrage et n’est jamais remplacé par les données initiales. Ne pas faire écrire plusieurs processus dans le même fichier.

**Exporter une sauvegarde** conserve toutes les données enregistrées et les mots de passe en clair. **Importer une sauvegarde** remplace le jeu de données et expire les sessions. **Importer un rapport de reverse** ajoute un compte depuis `all_pages.json` ou un rapport de page, avec un login/mot de passe de test choisi dans l’atelier. Les champs personnels, semestres, moyennes annuelles, tableaux et choix sont importés ; les références supplémentaires sont conservées dans le JSON. Les liens de production sont retirés et aucun fichier n’est téléchargé : joindre les fichiers locaux depuis Documents.

Pour ouvrir l’administration sur le LAN, définir un jeton avant le lancement :

```powershell
$env:OASIS_MOCK_ADMIN_TOKEN = Read-Host 'Jeton de test pour administration LAN'
py -3 .\mock_server\mock_oasis_server.py --port 8080
```

Le jeton est alors requis aussi en local et se saisit dans l’atelier. L’API utilise `Authorization: Bearer <jeton>`. Ce serveur HTTP est prévu pour un environnement de développement local.

## API d’administration

`GET /api/admin/state` renvoie toutes les données, mots de passe en clair inclus, et fournit l’en-tête `X-CSRF-Token`. Chaque mutation doit reprendre cet en-tête ; les requêtes de navigateur d’une autre origine sont refusées. Les exemples ci-dessous supposent l’administration locale sans jeton.

```powershell
$snapshot = Invoke-WebRequest http://127.0.0.1:8080/api/admin/state
$data = $snapshot.Content | ConvertFrom-Json
$headers = @{ 'X-CSRF-Token' = $snapshot.Headers['X-CSRF-Token'] }
$body = @{ revision = $data.revision; account_id = $data.accounts[0].id; scenario = 'new_note' } | ConvertTo-Json
Invoke-RestMethod -Method Post http://127.0.0.1:8080/api/admin/scenario -Headers $headers -ContentType 'application/json' -Body $body
```

| Route | Méthode | Contenu |
|---|---|---|
| `/health`, `/api/scenarios` | GET | État public, scénarios |
| `/api/admin/state`, `/api/admin/export` | GET | État éditable, sauvegarde complète avec mots de passe |
| `/api/admin/accounts` | POST | `login`, `password`, `student_id`, `display_name`, `empty`, `revision` |
| `/api/admin/accounts/<id>` | PUT / DELETE | `account`, mot de passe optionnel / suppression ; `revision` |
| `/api/admin/settings` | PUT | `settings`, `revision` |
| `/api/admin/scenario`, `/api/admin/cycle` | POST | `scenario` / suivant ; `account_id` optionnel, `revision` |
| `/api/admin/import` | POST | Sauvegarde dans `state`, `revision` |
| `/api/admin/import-report` | POST | `report`, `login`, `password`, `revision` |
| `/api/admin/events`, `/api/admin/coverage` | GET | Diagnostics, inventaire |
| `/api/admin/sessions/expire`, `/api/admin/auth/reset` | POST | `{}` |

## Fidélité et prochain reverse

Les routes de lecture utilisent les noms Oasis d’origine : `BO\Layout\MainContent::load`, `StudentCursus::reload_semester`, `::get_ranking`, `::get_validation_report` (route popup), `MyChoices::load`, les téléchargements `AbstractUploadField::download`, ainsi que les pages HOME, STUDENT, MYCURSUS, MYMARKS, MYCHOICES et MYDOCUMENTS. Les noms de champs, tables, classes et attributs utilisés par PoPS sont conservés. Les routes contenant des antislashs doubles dans les artifacts sont normalisées.

La découverte d’une route dans le JavaScript de production ne prouve pas son protocole de mutation. Les envois/suppressions de fichiers, mutations de choix, exemptions et opérations génériques BO dont les requêtes n’ont pas été capturées renvoient **501**, sauf réponse personnalisée. Leurs données sont entièrement gérables dans l’atelier. Le mot de passe oublié produit uniquement un événement local, sans e-mail et sans changer de mot de passe.

Le 2 octobre 2026, une seule tentative avec les identifiants du fichier `.env` a renvoyé HTTP 200 et `success:false`, `reload:no`, avec le message de blocage IP pour la journée. Aucun second essai n’a été fait et aucun secret n’a été enregistré. Le message avec neuf tentatives restantes et le seuil de dix proviennent de l’observation fournie par l’utilisateur. Le vrai message de fermeture, les règles de remise à zéro et les mutations restent à confirmer quand les accès reviendront, avec plusieurs comptes. Le réglage de fermeture n’affiche pas d’avertissement explicite dans la réponse étudiante.

## Vérification

```powershell
py -3 -m unittest discover -s mock_server -v
```

Les tests lancent leur serveur sur un port libre, utilisent des fichiers temporaires et n’appellent jamais le vrai Oasis. Ils vérifient les comptes, sessions, décompte des tentatives, fermeture, pannes, séparation des données, persistance, validation, conflits de révision, fichiers/photos, réponses personnalisées et import des anciens rapports.
