# PoPS

PoPS est une application Android Kotlin/Compose pour consulter les notes Oasis, conserver un cache local, et notifier les nouvelles notes comme les notes modifiées.

## Fonctionnalités

- Navigation en bas avec `Oasis` et `Réglages`
- Barre haute Oasis avec sélection d'année et rafraîchissement manuel
- Onglets `Épreuves`, `Modules`, `UEs` avec le flux `Épreuves` actif
- Stockage chiffré du login et du mot de passe avec AndroidX Security Crypto
- Cache local JSON des notes
- Polling en arrière-plan via WorkManager
- Notifications locales pour nouvelles notes, notes modifiées et erreurs de connexion répétées
- Serveur mock local parlant la même interface HTTP qu'Oasis

## Structure utile

- `app/src/main/java/.../data`: settings chiffrés, client Oasis, cache local JSON, repository et logique de diff
- `app/src/main/java/.../ui`: écrans Compose et ViewModel
- `app/src/main/java/.../sync`: worker et planification périodique
- `app/src/main/java/.../notifications`: notifications de notes et d'erreurs, receiver de dismissal
- `mock_server/mock_oasis_server.py`: serveur mock compatible Oasis

## Construire l'application

```powershell
.\gradlew.bat assembleDebug
```

L'APK debug est généré dans `app\build\outputs\apk\debug\app-debug.apk`. Sous Windows avec `LOCALAPPDATA`, il se trouve dans `$env:LOCALAPPDATA\PoPS-App-Build\app\outputs\apk\debug\app-debug.apk` pour éviter les conflits OneDrive.

Le build nécessite Git avec l'historique complet et un JDK 21 complet. Si Gradle choisit le JRE de VS Code sans `jlink`, utilise le JDK d'Android Studio :

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat --no-daemon '-Dorg.gradle.java.installations.auto-detect=false' '-Dorg.gradle.java.installations.paths=C:/Program Files/Android/Android Studio/jbr' :app:assembleDebug
```

## Versions automatiques

Aucun numéro n'est à mettre à jour à la main. Gradle génère `versionCode` depuis le nombre de commits Git et `versionName` au format `0.<compteur>+<hash>`, avec `-debug` pour un APK debug. La section À propos affiche exactement cette version Android. Par exemple, `0.12+abcd1234-debug` est plus récent que `0.11+efab5678-debug` sur l'historique publié de `main` ; le hash permet de retrouver la révision exacte.

Des modifications locales du code ajoutent `-dirty.<empreinte>` : deux états locaux différents ont des identifiants différents, sans modifier le compteur. Le compteur seul ne départage pas ces builds locaux ni des branches divergentes. Pour partager une version comparable, commit puis reconstruis depuis `main`. Conserve l'historique publié, et utilise un clone complet (CI : `fetch-depth: 0`). Un clone superficiel échoue explicitement plutôt que de produire un compteur incorrect.

```powershell
.\gradlew.bat :app:printAppVersion
```

## Utilisation normale

1. Ouvre l'application.
2. Va dans `Réglages` puis ajoute un compte.
3. Renseigne le login et le mot de passe Oasis. L'ajout ou la modification des identifiants teste la connexion avant d'enregistrer le compte.
4. Choisis la fréquence de vérification et l'état des notifications.
5. Ouvre `URL du serveur` pour changer l’instance, ou `Synchro > Avancé` pour les conditions réseau/charge et les erreurs TLS.

Les sections des réglages utilisent les mêmes marges et des séparateurs espacés entre les groupes de commandes.

Tous les réglages sont enregistrés automatiquement. L’URL est enregistrée après une courte pause de saisie ; une URL invalide ne remplace pas le serveur enregistré et un champ vide utilise l’instance par défaut.

Les réglages restent modifiables sans connexion à Oasis. La fréquence de synchro, les conditions réseau/charge et les notifications sont propres au compte sélectionné. Désactiver les erreurs de synchro, toutes les notifications du compte ou sa synchro en arrière-plan efface l’alerte d’erreur en cours. L’ajout ou la modification des identifiants nécessite toujours une authentification réussie. Un refus conserve les identifiants précédents et rappelle qu’un compte non activé ou une fermeture temporaire d’Oasis peut aussi bloquer la connexion.

Les notifications bloquées par Android apparaissent désactivées et grisées, avec une explication en rouge et un accès aux réglages système. L’état est actualisé au retour dans l’app, sans modifier les préférences enregistrées du compte.

L'application remonte les années académiques passées jusqu'à rencontrer deux années consécutives sans notes, avec une borne de sécurité de 10 ans.

## Serveur dans l'onboarding

Dans l'onboarding, « Changer de serveur » en bas de la page de connexion déplie un champ d’URL. Le laisser vide utilise l’instance par défaut affichée en placeholder. La connexion teste cette URL, puis enregistre l'URL et le compte ensemble uniquement après authentification réussie. Une erreur conserve les réglages précédents. Une URL HTTP(S) avec un chemin de base est acceptée, notamment pour le mock local.

## Gestionnaires de mots de passe

Les formulaires de connexion de l’onboarding et des paramètres utilisent le service Android Autofill choisi sur le téléphone. Une connexion réussie valide la session pour proposer la sauvegarde ; annuler le formulaire annule cette session.

## Lancer le serveur mock

Le serveur mock utilise uniquement la bibliothèque standard Python et expose les mêmes routes que le client Oasis attendu par l'application.

```powershell
py -3 .\mock_server\mock_oasis_server.py --host 0.0.0.0 --port 8080
```

Vérification rapide:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/health
Invoke-RestMethod http://127.0.0.1:8080/api/scenarios
```

## Tester le mock sur émulateur

1. Lance le serveur mock sur le PC.
2. Ouvre `Réglages` dans l'application.
3. Déplie `Avancé`.
4. Mets l'URL Oasis à `http://10.0.2.2:8080/`.
5. L’URL est enregistrée automatiquement.
6. Ajoute un compte avec n'importe quel login et mot de passe non vides.
7. Reviens sur `Oasis` puis appuie sur l'icône de rafraîchissement.

`10.0.2.2` ne fonctionne que depuis l'émulateur Android.

## Tester le mock sur téléphone physique

1. Connecte le téléphone et le PC au même Wi-Fi.
2. Récupère l'IPv4 du PC:

```powershell
ipconfig
```

3. Lance le serveur mock.
4. Vérifie que le pare-feu Windows autorise Python ou le port `8080` sur le réseau privé.
5. Installe l'application debug:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

6. Dans `Réglages > Avancé`, mets l'URL Oasis à `http://<IP_DU_PC>:8080/`.
7. L’URL est enregistrée automatiquement.
8. Ajoute un compte avec un login et un mot de passe non vides.
9. Reviens sur `Oasis` puis lance un rafraîchissement.

Si ça marche sur émulateur mais pas sur téléphone, la cause la plus fréquente est presque toujours réseau:

- `10.0.2.2` ne marche pas sur un vrai téléphone
- le téléphone n'est pas sur le même réseau local
- le pare-feu bloque l'accès entrant au port `8080`
- l'URL utilise `localhost` ou `127.0.0.1`, qui pointent vers le téléphone lui-même

## Debug sur téléphone et émulateur

Oui, tu peux attacher un terminal et lire les logs sur les deux.

### Vérifier que l'appareil est vu par ADB

```powershell
adb devices
```

### Ouvrir un shell sur l'appareil

```powershell
adb shell
```

### Lire les logs utiles de l'application

```powershell
adb logcat | Select-String "PoPS"
```

Le client Android journalise notamment:

- l'URL Oasis ciblée
- les échecs de récupération de semestre
- les erreurs de synchro en arrière-plan

### Filtrer les erreurs réseau Android

```powershell
adb logcat | Select-String "OkHttp|SSL|UnknownHost|ConnectException|SocketTimeout"
```

### Tester directement la reachability depuis le téléphone

Selon l'image Android:

```powershell
adb shell ping -c 1 <IP_DU_PC>
```

ou via le navigateur du téléphone en ouvrant `http://<IP_DU_PC>:8080/health`.

### Depuis Android Studio

Tu peux aussi utiliser `Logcat`, sélectionner le téléphone branché, puis filtrer sur `PoPS`, `PoPS-Oasis` ou `PoPS-Repository`.

## Simuler une nouvelle note et une note modifiée

Nouvelle note:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/admin/scenario -ContentType 'application/json' -Body '{"scenario":"new_note"}'
```

Note modifiée:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/admin/scenario -ContentType 'application/json' -Body '{"scenario":"updated_note"}'
```

Scénario suivant:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/admin/cycle -ContentType 'application/json' -Body '{}'
```

Ensuite, attends le prochain polling ou appuie sur le bouton de rafraîchissement dans `Oasis`.

## Notifications d'erreur en arrière-plan

Quand une synchro en arrière-plan échoue:

- une notification d'erreur est créée au premier échec du jour
- les échecs suivants le même jour réutilisent cette notification et incrémentent le compteur
- si la notification est effacée, un nouvel échec plus tard dans la journée pourra à nouveau alerter
- une synchro réussie réinitialise cet état

## Limites actuelles

- Android ne garantit pas une exécution stricte à la minute près pour le polling, et WorkManager ne descend pas sous 15 minutes en périodique.
- La détection de modification repose sur un appariement heuristique, faute d'identifiant natif Oasis stable.
- `Ignorer les erreurs TLS` reste une option de dépannage pour test, pas une solution de sécurité finale.
