# Server Presence

<img src="logo/logo-400.png" width="128" alt="Logo">

Mod Fabric (client) pour Minecraft 26.3 qui affiche le serveur sur lequel tu joues dans ta présence Discord : nom (« Joue à PlatoMINE »), IP, logo du serveur, tête de ton skin, chrono et un bouton vers le site. Il se masque tout seul quand tu es en vanish.

Prérequis : Fabric Loader 0.19.5+, Fabric API, Java 25, l'application Discord de bureau ouverte.

## Serveurs préconfigurés

| Serveur | IP | Détection du vanish |
| --- | --- | --- |
| PlatoMINE | play.platomine.fr | oui (messages, ligne « VANISH ACTIVÉ » du scoreboard, « [V] » du tab de PlatoCore) |
| LeChantierMC | play.lechantiermc.fr | oui (motifs génériques) |
| CHL SMP | play.chl-smp.fr | oui (motifs génériques) |
| SkillioGames | play.skilliogames.fr | non |
| LuckatTech Survie | luckattech.playit.game | non |

Tout autre serveur rejoint pour la première fois est **ajouté automatiquement** à la liste, avec son nom de la liste Multijoueur et son logo (sauf mondes LAN, Realms et adresses locales) ; il suffit ensuite de le modifier ou de le désactiver si besoin. L'option « Ajout automatique » coupe ce comportement.

Dans **Multijoueur**, chaque serveur a un petit logo Discord en bas à droite (sous l'icône de ping) : bleu = affiché sur Discord, gris = non affiché ; un clic l'active ou le désactive.

## Réglages

Bouton **Options → Présence Discord…** (aussi depuis le menu principal, sans être connecté), touche **F7** (modifiable dans Options → Commandes) ou commande **/presence** :

- activer/désactiver chaque serveur, en ajouter, en supprimer ;
- par serveur : nom « Joue à », lignes 1 et 2, logo (vide = logo du serveur récupéré automatiquement), bouton, chrono, adresses reconnues (`*.domaine.fr` = tous les sous-domaines) ;
- comportement sur les autres serveurs et en vanish : **Générique** (« En multijoueur », sans nom ni IP) ou **Masqué** (rien sur Discord) ;
- variables utilisables dans les textes : `{server}` `{ip}` `{players}` `{player}` `{dimension}` `{version}`.

Les réglages sont dans `config/serverpresence.json`.

## Vanish

Pour les serveurs où la détection est activée, le mod :

1. attend quelques secondes après la connexion (8 s par défaut) avant d'afficher le serveur, le temps de voir si un vanish est déjà actif ;
2. lit les messages **système** du serveur (jamais les messages des joueurs) : « vanish activé / désactivé » ;
3. surveille en continu un indicateur : ligne du scoreboard, ton nom dans le tab, haut/bas du tab, barre de boss, barre d'action.

Les trois motifs (expressions régulières, sans casse) se règlent par serveur ; le bouton « Motifs génériques » remplit des motifs pour SuperVanish, PremiumVanish, EssentialsX, CMI… en français et en anglais.

En secours, le **mode discret** masque le serveur actuel jusqu'à la déconnexion : `/presence discret`, une touche à assigner, ou le bouton dans les réglages. `/presence statut` affiche ce que le mod a détecté.

## Application Discord

Le mod passe par sa propre application Discord, « Minecraft », dont l'icône est le logo du mod (Discord l'affiche dans les salons vocaux). Son identifiant est écrit uniquement dans le code (`PresenceManager.DISCORD_APPLICATION_ID`) et ne se règle pas en jeu ; le nom affiché (« Joue à … ») est de toute façon remplacé par celui du serveur.

## Compiler

```
./gradlew build
```

Gradle a besoin de Java 25. `./gradlew runClientGameTest` lance un vrai client et vérifie les écrans, la détection du vanish et l'envoi de la présence au Discord ouvert sur la machine.

Le logo (`logo/`, 400×400 pour CurseForge) est dessiné par `logo/logo.py` : `python logo/logo.py logo`.

## Licence

MIT, voir [LICENSE](LICENSE).
