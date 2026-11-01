# Direction visuelle : application Flybox (Android)

## Mode

Operate. L'utilisateur vient vérifier sa consommation et le risque de manquer de data, souvent d'une main, en quelques secondes. La lisibilité et la vitesse de lecture passent avant l'expression. L'identité tient dans la construction des graphiques, pas dans la décoration.

Hypothèses (aucune réponse utilisateur possible) : usage personnel, un seul utilisateur, écran 360 à 412 dp de large, usage en plein jour comme le soir, pouce droit. Aucun logo, aucun visuel Orange n'est repris : #ff7900 est une couleur d'accent, rien d'autre.

## Direction

Le forfait est un cadran qui regarde en avant. L'anneau de l'accueil ne montre pas seulement ce qui reste : un segment hachuré prolonge l'arc jusqu'à l'endroit où le solde sera à la date d'expiration, au rythme actuel. On lit donc "où j'en suis" et "où j'arrive" dans un seul geste, avant même de lire la bannière.

Tous les graphiques reprennent ce vocabulaire : un trait plein pour le mesuré, un trait hachuré ou clair pour l'estimé, l'orange pour la donnée de l'utilisateur, le vert et le rouge réservés au verdict. Montserrat très gras pour quelques chiffres, très sobre ailleurs. Le ton reste chaleureux (fond crème, formes rondes) sans mascotte ni illustration.

## Typography

Une seule famille, imposée par l'utilisateur : **Montserrat** (Google Fonts, Julieta Ulanovsky, licence SIL OFL 1.1, police variable, embarquée dans `res/font` pour fonctionner hors ligne). Poids utilisés : 500, 600, 700, 800. Chiffres tabulaires (`fontFeatureSettings = "tnum"`) sur tous les montants, axes et valeurs de graphique, pour que les nombres ne sautent pas.

Note pour la chaîne d'outils : si `team design-check` interdit Montserrat, il faut l'ajouter à `project.design.allowed_fonts`. Le choix est celui de l'utilisateur, pas une valeur par défaut.

Échelle (sp / hauteur de ligne, poids) :
- Chiffre héros (reste au centre de l'anneau) : 44/48, 800. Un seul par écran, uniquement à l'accueil.
- Titre d'écran : 26/32, 700
- Titre de section : 18/24, 700
- Valeur de ligne (appareil, appli, jour) : 16/22, 700
- Corps : 15/22, 500
- Label, puce, onglet : 13/18, 600
- Légende, axe, "mis à jour il y a 4 min" : 12/16, 500 (minimum absolu, jamais en dessous)

Montserrat est large : pas de tracking positif, pas de capitales prolongées, noms d'appareils et d'applis sur une ligne avec points de suspension. Prévoir 10 à 15 pour cent de largeur en plus par rapport à une grotesque neutre quand on dimensionne les libellés des onglets (cinq onglets, libellés courts : Accueil, Historique, Appareils, Applis, Réglages ; "Historique" tient en 13 sp sur 360 dp, à vérifier).

## Color

Thème clair uniquement, forcé même si le système est en sombre (`darkTheme = false` en dur, pas de `isSystemInDarkTheme`). Raison : choix de l'utilisateur, et l'orange saturé sur fond crème est plus chaleureux et plus lisible en plein soleil qu'inversé.

Jetons (rôles Material 3 entre parenthèses) :
- Orange de marque `#FF7900` (primary, remplissages et graphiques uniquement)
- Encre sur orange `#231A12` (onPrimary), contraste mesure 6,5:1. Jamais de texte blanc sur l'orange (environ 2,9:1).
- Orange texte `#B34B00` (primary pour texte et liens sur fond clair), à valider à 4,5:1 minimum.
- Orange clair `#FFE3CC` (primaryContainer : puces actives, fond de l'anneau vide)
- Fond `#FFF8F1` (background, crème chaud)
- Surface `#FFFFFF` (surface, feuilles et barre de navigation)
- Surface atténuée `#F6EDE3` (surfaceVariant : pistes de barres, squelettes)
- Encre `#231A12` (onSurface), texte secondaire `#6A5D52` (onSurfaceVariant, au moins 5:1 sur le fond)
- Filet `#E8DACB` (outlineVariant)
- Vert verdict `#1E7A4C`, fond vert `#E2F3E9`
- Rouge verdict `#BF2F1B`, fond rouge `#FCE4DF`
- Encre de courbe `#4A3A2E` (série secondaire, débit montant)

Règle de rareté : l'orange occupe l'anneau, la donnée du jour, l'onglet actif et le bouton principal, rien d'autre. Vert et rouge n'apparaissent que sur la bannière de verdict, le segment hachuré de l'anneau, et les pastilles de statut. Ils ne servent jamais à décorer. L'information ne dépend jamais de la couleur seule : chaque verdict a un mot ("Dans les temps", "Risque de dépassement").

## Layout and motion

Structure : une feuille continue par écran, pas une grille de cartes identiques. Le fond crème porte le contenu ; les groupes sont séparés par l'espace (24 dp) et des filets de 1 dp, pas par des boîtes empilées. Une seule surface surélevée par écran au maximum : la feuille blanche arrondie qui porte le graphique principal.

Accueil, de haut en bas : puce de signal et "mis à jour il y a X min" (une ligne) ; anneau géant centré (diamètre = largeur d'écran moins 64 dp, soit environ 280 dp) ; sous l'anneau, total et date d'expiration en une phrase ; bannière de verdict pleine largeur ; rythme moyen et solde prévu à 30 jours en deux lignes de texte, pas deux tuiles ; usage du jour avec un petit trait de comparaison à la moyenne. La zone du pouce (bas) porte la bannière et la barre de navigation, jamais un contrôle rare.

Espacement : base 4 dp, marges d'écran 16 dp, entre sections 24 dp, dans une section 12 dp, hauteur minimale d'une ligne tactile 56 dp, cibles 48 dp minimum.

Rayons : feuille et bannière 24 dp, puces et sélecteur de période en pastille complète (50 pour cent), barres de graphique 8 dp en haut seulement, pistes de barre horizontale arrondies à la moitié de la hauteur, icônes d'applis en carré arrondi 12 dp (comme Android les fournit).

Barre de navigation : cinq onglets, fond blanc, filet en haut, indicateur actif en pastille orange clair `#FFE3CC` avec glyphe et libellé en encre, libellé toujours visible. Pas de glyphe décoratif ailleurs ; seules les icônes fonctionnelles (onglets, signal, appli, appareil) existent.

Mouvement, un seul moment écrit : à la première ouverture de l'accueil après déverrouillage, l'arc orange de l'anneau se déploie de zéro jusqu'à la valeur (900 ms, décélération forte, `CubicBezier(0.2, 0, 0, 1)`), puis le segment hachuré se trace en 400 ms, et le chiffre central compte jusqu'à sa valeur en même temps que l'arc. Rien ne bouge ensuite, sauf les retours d'appui standard. Si l'animation système est réduite, l'état final s'affiche directement. Pas de rebond, pas de transition d'écran décorative au-delà de celle de Material.

### Graphiques

**Anneau (accueil).** Épaisseur de trait 28 dp, bouts arrondis, départ à 12 h, sens horaire, ouverture de 0 degré (cercle complet). Piste `#FFE3CC`. Arc "reste" `#FF7900` de 0 à reste/total. Au centre : reste en Go (44 sp, 800), "Go restants" en 13 sp secondaire, puis rien d'autre. Segment de projection : l'arc orange plein s'arrete a la valeur du reste. Les hachures (tirets 6 dp, vide 6 dp) sont tracees UNIQUEMENT sur la piste pale `#FFE3CC`, a la suite de l'arc plein, en un seul ton : vert verdict si le solde prevu a l'expiration est positif, rouge verdict s'il tombe a zero avant l'expiration (les hachures vont alors jusqu'au repere "epuise" a 12 h). Jamais de hachures par-dessus l'orange plein. Bouts de trait : ronds seulement a l'extremite du plein, droits (butt) pour les hachures, afin que rien ne depasse. Valeur sous 1 Go : affichée en Mo, l'arc reste proportionnel.

**Barres quotidiennes (historique).** Une barre par jour, largeur = 60 pour cent de la fente (7 jours) ou 70 pour cent (30 jours, fentes d'environ 10 dp, pas d'étiquette par barre), orange `#FF7900`, coins hauts 8 dp (4 dp en 30 jours). Aujourd'hui en orange plein, les autres en orange à 55 pour cent d'opacité pour garder un point focal. Ligne de moyenne : filet horizontal tireté `#6A5D52` avec libellé "moy. 2,1 Go" à droite. Axe : seulement 3 graduations en 12 sp, filets `#E8DACB`, aucun cadre. Au toucher : la barre passe en orange plein, les autres s'atténuent, une bulle blanche (rayon 12 dp, ombre douce 4 dp de flou, sans décalage dur) affiche "mar. 28 oct, 2,34 Go" au-dessus de la barre, valeur en 16 sp 700. Cible tactile = toute la hauteur de la fente. Bascule 7 j / 30 j : pastille segmentée en haut de la feuille.

**Barres horizontales (applis).** Ligne de 56 dp : icône d'appli 36 dp, nom en 15 sp 600, valeur à droite en 16 sp 700 tnum, et sous le nom la barre de 10 dp de haut. Longueur proportionnelle à la plus grosse appli (100 pour cent), piste `#F6EDE3`. Les trois premières en orange `#FF7900`, les suivantes en orange clair `#FFC594`, pour un classement lisible sans arc-en-ciel. Pas de pourcentage décoratif ; la part du total apparaît seulement en texte secondaire sous la valeur. Sélecteur de période (Aujourd'hui, 7 jours, 30 jours) en pastilles en haut, 48 dp de haut. Note permanente "Chiffres de ce téléphone uniquement" en 12 sp en bas de liste.

**Courbe de débit (historique).** Zone de 160 dp de haut sous le graphique à barres. Descendant : trait de 2,5 dp `#FF7900`, remplissage dégradé vertical orange 18 pour cent vers 0 (dégradé fonctionnel de remplissage, pas décoratif, pas de violet ni de bleu). Montant : trait de 2 dp `#4A3A2E`, sans remplissage. Courbe lissée mais sans dépassement (monotone), pas de points sauf le dernier (cercle plein 8 dp avec liseré blanc 2 dp) accompagné de sa valeur "3,2 Mo/s". Axe vertical : 2 graduations, unité Mo/s. Légende en deux mots au bout des courbes, pas de boîte de légende. Toucher : repère vertical fin et bulle comme les barres.

**Bannière de verdict (accueil).** Pleine largeur moins marges, rayon 24 dp, hauteur au moins 72 dp, remplissage plein et teinté (vert `#E2F3E9` ou rouge `#FCE4DF`), sans bordure gauche ni ombre. Contenu : le mot d'état en 16 sp 800 dans la couleur de verdict (`#1E7A4C` ou `#BF2F1B`), puis la phrase en 15 sp 500 encre. Vert : "Dans les temps" / "Il vous restera environ 6 Go à l'expiration le 14/12." Rouge : "Risque de dépassement" / "Plus de data vers le 02/12, soit 12 jours avant l'expiration." Un glyphe fonctionnel de 24 dp (coche ou triangle d'alerte) précède le mot d'état, c'est la seule icône de la bannière. Sans prédiction (moins de 6 h) : fond `#F6EDE3`, encre secondaire, "Collecte des données en cours, estimation dans quelques heures." La bannière est un élément annoncé (liveRegion polite) pour TalkBack.

## States

- **Chargement** : squelettes dans la forme finale. L'anneau est une piste claire `#FFE3CC` sans arc ; les lignes sont des barres `#F6EDE3` aux coins 8 dp avec un balayage de brillance lent (1,4 s) limité à un ton plus clair, désactivé si animation réduite. Pas de roue qui tourne.
- **Vide (pas encore de données)** : l'anneau reste dessiné, vide, avec au centre "Pas encore de données" en 16 sp 700 et dessous une phrase qui dit quoi faire ("Connectez-vous au Wi-Fi de la Flybox, la collecte démarre seule."). Historique : trois barres fantômes `#F6EDE3` et la même phrase. Aucune illustration.
- **Erreur / hors ligne** : jamais bloquant. Les dernières valeurs restent affichées en entier ; une puce sous le titre passe en fond `#F6EDE3` avec texte "Hors ligne, mis à jour il y a 12 min" en 13 sp 600, jamais en rouge (ce n'est pas le verdict du forfait). Le segment hachuré de l'anneau est retiré si les données ont plus de 24 h. Forfait introuvable : à la place du reste, "Forfait indisponible" en 18 sp 700, le reste de l'écran fonctionne.
- **Accès refusé (connexion routeur ou PIN)** : écran plein avec un titre court, une phrase d'explication, et un seul bouton orange (ex. "Ouvrir les réglages"). Pour le PIN : pavé numérique de touches 72 dp rondes, points de saisie de 16 dp, après 5 échecs le pavé se grise et un compte à rebours "Réessayez dans 28 s" apparaît.
- **Focus** : anneau de focus de 3 dp `#231A12` décalé de 2 dp, visible sur fond clair et sur orange, présent sur pastilles, onglets, lignes tactiles et touches du pavé (clavier, commutateur d'accès). Appui : voile `#231A12` à 8 pour cent.
- **Texte d'erreur de formulaire** (mot de passe routeur) : sous le champ, 13 sp 600 en `#BF2F1B`, avec le motif nommé en clair ; le champ prend un contour de 2 dp de la même couleur.
- **Appareils hors ligne** : lignes en texte secondaire `#6A5D52`, sans barre d'usage, regroupées sous "Hors ligne" ; les appareils en ligne ont une pastille de statut pleine `#1E7A4C` de 10 dp avec le mot "En ligne" visible en 12 sp.

## References

Consultées le 2026-11-01. Aucune capture visuelle n'a pu être vue : seuls des titres ont été obtenus, donc rien ci-dessous ne décrit un visuel observé. Les principes viennent du brief et de la pratique des graphiques, rattachés à ces pages par leur sujet.

- https://www.awwwards.com/websites/sites_of_the_day/ : titres seuls (Dont Look up game, MECHA, Spyker Cars, A24, ...). Aucun sujet donnée ou tableau de bord. Rien retenu, utilité nulle pour ce produit. Rejeté : l'esthétique éditoriale de vitrine, hors sujet pour un outil de tâche.
- https://www.awwwards.com/websites/data-visualization/ : titres seuls (Trois pas de côté, Job Hunt, Darkmoon, Findra.art, ...). Aucun visuel vu, aucune description de graphique. Rien retenu ; rejeté en bloc faute d'éléments.
- https://dribbble.com/shots/26925633-Oura-Mobile-App-Smart-Ring-Health-Tracking-UI : titre et résumé texte seulement ("données biométriques en aperçus clairs"). Visuel non vu. Retenu comme sujet de comparaison : un anneau comme repère central d'une appli de suivi de santé, dont on prend l'idée de hiérarchie (un grand indicateur, du texte autour). Rejeté : le ton "alimenté par l'IA", les mises en page de cartes.
- https://dribbble.com/search/ring-chart et https://dribbble.com/search/data-dial : pages de recherche, titres seuls. Visuel non vu. Retenu : seulement que l'anneau et le cadran sont des formes établies pour un solde ; l'ajout du segment de projection est notre décision, non empruntée.
- https://dribbble.com/search/mobile-bar-chart et https://dribbble.com/search/mobile-line-chart : titres seuls, visuel non vu. Retenu : rien de concret. Rejeté : par principe, les graphiques au quadrillage dense et aux dégradés bleu-violet que ce type de recherche produit habituellement (affirmation de principe, non observée).
- Claude in Chrome : non utilisé, la recherche restreinte à dribbble.com a suffi pour établir que seuls les titres seraient disponibles. UNREACHABLE pour un visuel : Awwwards ne retourne que des listes de titres via WebFetch, Dribbble n'est pas rendu par la récupération.

Conséquence : la direction ci-dessus est dérivée du brief et des principes de lisibilité (hiérarchie, fiabilité de la lecture à une main, graphiques à encodage double mesuré/estimé), pas d'une imitation de références.

## Refused

- Polices génériques en voix principale (Inter, Roboto, Open Sans, Poppins, Arial, Geist, Fraunces, Plus Jakarta Sans, Space Grotesk) : Montserrat seul, imposé.
- Thème sombre, ou suivre le thème système.
- Dégradés violets ou bleus, texte en dégradé, verre dépoli ou flou décoratif. Le seul dégradé est le remplissage orange sous la courbe de débit.
- Grille de cartes identiques icône + titre + ligne de texte à l'accueil ; mosaïque de tuiles "métrique" à gros chiffre avec petit label au-dessus de chacune.
- Petit label au-dessus de chaque titre, numérotation de sections, grand chiffre en gabarit par réflexe (un seul chiffre héros, au centre de l'anneau).
- Bordure colorée à gauche sur les cartes ou bannières, ombres dures décalées.
- Étincelles, étoiles, baguettes magiques, emoji, icône décorative à côté de chaque titre ou bouton, mascotte et illustrations génériques d'état vide.
- Tirets cadratins dans les textes d'interface et dans ce document.
- Texte de remplissage marketing ("transparent", "puissant", "sans effort", "débloquez") : les textes disent ce qui est, avec les chiffres.
- Copie du logo, des couleurs en dégradé, du pictogramme ou des visuels d'Orange : seule la teinte #ff7900 est utilisée, comme accent.
- Texte blanc sur l'orange, et vert ou rouge utilisés seuls pour porter une information.
