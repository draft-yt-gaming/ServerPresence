# Server Presence

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

## Réglages

Touche **F7** (modifiable dans Options → Commandes) ou commande **/presence** :

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

Par défaut, le mod utilise l'application Discord « PlatoMINE » ; le nom affiché (« Joue à … ») est remplacé par celui du serveur, donc n'importe quelle application convient. Pour utiliser la tienne : crée-la sur https://discord.com/developers/applications et colle son *Application ID* dans les réglages.

## Compiler

```
./gradlew build
```

Gradle a besoin de Java 25. `./gradlew runClientGameTest` lance un vrai client et vérifie les écrans, la détection du vanish et l'envoi de la présence au Discord ouvert sur la machine.
