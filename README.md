# Conso Flybox

Application Android pour suivre la consommation internet d'une **Flybox 4G Orange Madagascar (CP02)**, directement depuis le téléphone connecté au Wi-Fi de la box. Aucun serveur, aucun compte en ligne : toutes les données restent sur le téléphone.

> Projet personnel et indépendant. Il n'est ni affilié ni approuvé par Orange. Aucun logo ni visuel Orange n'est utilisé.

## Fonctions

- **Accueil** : anneau du forfait restant, consommation du jour, verdict (« Dans les temps » ou « Risque de dépassement »), prévision à 30 jours, qualité du signal 4G.
- **Historique** : consommation par jour sur 7 ou 30 jours, courbe de débit sur 24 h.
- **Appareils** : liste des appareils connectés au routeur (connexion au routeur requise), avec un interrupteur Internet par appareil (activé ou bloqué), comme sur la page du routeur (confirmation demandée, ce téléphone ne peut pas se bloquer).
- **Applis** : classement de la consommation par application sur ce téléphone (permission « Accès aux données d'utilisation »).
- **Rappel quotidien** : notification locale à l'heure choisie (20 h par défaut) avec la consommation du jour et le reste du forfait.
- **Verrouillage par code PIN** local. Le mot de passe du routeur est stocké chiffré (Android Keystore) et n'est jamais journalisé.

## Comment ça marche

L'application interroge directement :

| Source | Données |
|---|---|
| `http://192.168.1.1/reqproc/proc_get?cmd=system_status` | compteurs cumulés, débit, durée de connexion, signal (sans connexion) |
| Routeur, avec session | liste des appareils connectés |
| `http://123.orange.mg/info-conso/` | forfait restant, total et date d'expiration |

La collecte se fait toutes les 15 minutes en arrière-plan (minimum d'Android) lorsque le téléphone est connecté au Wi-Fi de la Flybox. Hors de ce Wi-Fi, l'application affiche les dernières données avec leur âge.

La prévision utilise le rythme réel (baisse du solde du forfait, ou compteurs du routeur) sur au moins 6 heures d'historique.

## Limites connues

- **Consommation par appareil** : le routeur ne la fournit pas dans son interface standard. L'application la lit si le routeur la renvoie, sinon l'écran l'indique.
- **Connexion au routeur** : implémentée d'après le code web du routeur, vérifiée seulement avec des réponses simulées. Le routeur se verrouille après 5 échecs de mot de passe : l'application ne réessaie jamais automatiquement.
- La collecte de 15 minutes peut être retardée par l'économie de batterie d'Android.
- Le seul réglage du routeur que l'application modifie est l'interrupteur Internet d'un appareil, sur action explicite et confirmée.

## Compiler

Prérequis : JDK 21, Android SDK (`ANDROID_HOME` défini), Android 8.0 (API 26) minimum pour l'appareil.

```
./gradlew test            # tests unitaires et d'acceptation
./gradlew assembleDebug   # APK : app/build/outputs/apk/debug/app-debug.apk
```

Installation sur un téléphone relié en USB avec le débogage activé :

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Technologies

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, DataStore, OkHttp. Police Montserrat (licence SIL OFL 1.1, voir `app/src/main/assets/OFL-Montserrat.txt`).

## Documentation du projet

- Spécification : [`docs/specs/flybox-usage-app.md`](docs/specs/flybox-usage-app.md)
- Direction visuelle : [`docs/design/flybox-usage-app.md`](docs/design/flybox-usage-app.md)

## Licence

[MIT](LICENSE). La police Montserrat reste sous sa propre licence (SIL OFL 1.1).
