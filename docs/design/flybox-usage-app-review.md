# Revue design : application Flybox (build debug)

Date : 2026-11-01. Skill impeccable chargé (critique et audit natifs Android appliqués à la main contre le brief approuvé).

## Méthode

Émulateur Pixel 5 (Android 15, AVD dans le scratchpad), APK debug installé, captures réelles à 360 dp (densité 480) et 412 dp (densité 420), thème système clair puis sombre. Base Room réamorcée dans le scratchpad (historique Orange et relevés routeur) pour voir les états "Risque de dépassement" et "Dans les temps". Réseau coupé pour voir l'état hors ligne. Aucun identifiant routeur saisi : l'écran Appareils n'a été vu que dans son état "Connexion au routeur nécessaire", sa liste est revue sur le code seul. Permission d'usage accordée par appops pour voir Applis.

## Conforme au brief (vérifié sur captures)

- Montserrat partout (accueil, onglets, graphiques, pavé, réglages). Thème clair forcé, y compris avec le système en sombre.
- Aucun texte blanc sur l'orange : boutons et chiffres en encre #231A12 (contraste mesuré 6,5:1 sur #FF7900, pas 8:1 comme écrit au brief, mais largement suffisant).
- Orange limité à l'anneau, la barre du jour, les barres de graphique, l'onglet actif (pastille orange clair), le bouton principal. Texte orange en #B34B00 (5,1:1 sur le fond).
- Bannière : mot d'état + glyphe + phrase, jamais la couleur seule. Pas de tiret cadratin ni d'emoji dans les textes et le code (recherche faite sur app/src/main). Pas de grille de cartes : feuille continue, filets, une seule feuille blanche (Historique).
- "Historique" tient à 13 sp sur 360 dp, sur une ligne, sans coupe (marge d'environ 4 dp avec "Appareils", voir mineur).
- Gris texte #6A5D52 : 6,0:1 sur le fond. Vert #1E7A4C sur fond clair : 5,1:1. Rouge sur fond rouge clair : 4,8:1.
- Cibles : onglets 56 dp, boutons 52 dp, pavé 72 dp, pas +/- 48 dp, lignes 56 dp.
- États vus : chargement (code, squelettes), vide, hors ligne (puce grise, pas rouge), forfait en collecte, accès refusé (Appareils, Applis) avec un seul bouton orange.

## Problèmes

### Importants

1. **Barre d'état illisible quand le système est en sombre.** Capture : l'heure et les icônes sont blanches sur le fond crème, quasi invisibles. Cause : `enableEdgeToEdge()` sans style explicite suit le thème système et écrase `windowLightStatusBar` du thème XML. MainActivity.kt:72. À faire : `enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))` pour que "thème clair forcé" soit vrai aussi pour les barres système.
2. **Segment de projection de l'anneau : la lecture est bruyante et ambiguë.** Charts.kt:83 et 93. Le trait hachuré est dessiné par-dessus l'arc orange plein : les intervalles laissent voir l'orange, on obtient des barres rouge/orange (ou vert/orange) saturées qui vibrent (rouge sur orange 2,2:1, vert sur orange 2,0:1) et qui se lisent comme "orange" et non comme "estimé". Le cap rond de l'arc orange dépasse aussi aux deux extrémités du hachuré (blob visible à 12 h et à 10 h dans le cas à risque). À faire : tracer l'arc plein seulement de 0 à `projectionTo`, puis le hachuré sur la piste orange clair (#FFE3CC), tiret plus fin ou hachures obliques, un seul ton (vert ou rouge) ; cap Butt pour l'arc quand une projection est affichée, ou cap rond uniquement sur la fin du plein. Le trait doit rester le seul élément saturé, comme dans le brief ("trait hachuré ou clair pour l'estimé").
3. **"Actualiser" se coupe en "Actualis / er" à 360 dp en état hors ligne.** HomeScreen.kt:95 (ligne 91 pour la puce). La puce "Hors ligne, mis à jour à l'instant" prend presque toute la largeur, le TextAction passe à la ligne. À faire : `maxLines = 1`, `softWrap = false` sur TextAction et donner la priorité à l'action (la puce prend `weight(1f, fill=false)` avec ellipsis, ou retour à la ligne de la puce sous le titre, ou texte court "Hors ligne, il y a 12 min"). Même risque dans Appareils (DevicesScreen.kt:88-89 : "En ligne (n)" + "Trier par usage" + "Actualiser" sur une ligne à 360 dp, non vu, à vérifier ; passer à deux lignes ou déplacer le tri dans la ligne suivante).
4. **Étiquettes d'axe qui se heurtent aux données dans les graphiques d'Historique.** Charts.kt:174-175 (graduations) et Charts.kt:202 ("moy."). Le libellé "10 Go" est posé sur la première barre, "moy. 9,8 Go" sur les barres de droite. Dans la courbe de débit, la légende "Descendant / Montant" (Charts.kt:328 et suivantes) est dessinée dans la zone du tracé et la courbe la traverse ; l'étiquette de dernière valeur "1,7 Mo/s" chevauche aussi la courbe. À faire : sortir les libellés de graduation de la zone des barres (colonne gauche réservée ou fond de la feuille derrière le texte), placer "moy." au-dessus de la ligne dans une marge libre ou dans la légende sous le titre, et sortir la légende de la courbe au-dessus du cadre (ligne de légende sous le titre "Débit récent", avec petit trait coloré plein/encre pour ne pas dépendre du texte coloré seul).
5. **Pas d'axe horizontal sur la courbe de débit.** Aucun repère temporel : on ne sait pas si c'est 24 h ou 1 h ; seule la bulle au toucher donne l'heure. Ajouter deux ou trois étiquettes d'heure (il y a 24 h, 12 h, maintenant) en 12 sp.

### Mineurs

6. **Sélecteur de période : pastilles de 40 dp** (Basics.kt:142), sous les 48 dp du brief. Le conteneur fait 48 dp mais la zone tactile de chaque pastille 40 dp. Passer `heightIn(min = 48.dp)` ou étendre la zone tactile.
7. **Contraste des éléments non textuels.** Points vides du code (PinScreens.kt:88, #FFE3CC sur crème 1,17:1), touches blanches sans contour sur crème (1,1:1), piste de l'interrupteur désactivé #F6EDE3 (SettingsScreen.kt:200). Ajouter un contour 1 dp Hairline ou 2 dp aux points vides et aux touches, un contour plus foncé à l'interrupteur.
8. **Barres d'applis 4 et suivantes en #FFC594** (AppsScreen.kt:85, Theme.kt:23) : teinte hors jetons du brief, 1,3:1 sur la piste. Les valeurs sont écrites, donc non bloquant ; sinon utiliser le jeton primary avec alpha ou rester en #FF7900 pour toutes les lignes.
9. **Étiquette flottante du champ** dans Réglages : l'encoche du libellé "Identifiant" est en crème sur un champ blanc (visible comme un patch). Mettre `containerColor` sur le fond du libellé ou utiliser un libellé fixe au-dessus du champ.
10. **Onglets serrés à 360 dp** (MainActivity.kt:199) : "Historique" et "Appareils" sont à environ 4 dp l'un de l'autre. Ça tient, mais pas de marge pour un grossissement de police du système (`softWrap=false` et `Clip` couperaient le mot). Prévoir une vérification à taille de police 1,3 et réduire les marges internes de la barre, ou garder Clip mais tester.
11. **Onboarding sur écran court (360 x 640 dp)** : colonne non défilante avec centrage vertical, la version "Confirmez" tient ; la version à trois lignes d'explication frôle le bas (écran de verrouillage vu OK à 640 dp, onboarding non capturé à cette taille). Ajouter `verticalScroll` ou réduire l'espacement.
12. **Brief vs build** : le brief annonce 8:1 pour l'encre sur orange ; la valeur mesurée est 6,5:1. À corriger dans le brief (pas dans le code).

## Non vérifié

Liste Appareils avec données (pas d'identifiants), états erreur de formulaire du mot de passe, compte à rebours du pavé après 5 échecs, animations d'ouverture (statique seulement), focus clavier, lecteur d'écran.

## Captures (scratchpad)

/tmp/claude-1000/-home-tonny-Perso/e9fba1e2-e755-4f3a-9ec5-5a403ca6f3bf/scratchpad : onb360.png, home360.png, risk360.png, risk360_b.png, hist360_a.png, hist360_b.png, t360_2.png (Appareils), apps360.png, t360_4.png (Réglages), lock412.png, ok412.png, green412.png, lock640.png.
