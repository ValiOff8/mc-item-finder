# Chest Item Finder – Minecraft 26.3 / Fabric

Ein Mod für Minecraft **Java Edition 26.3**, der die Inhalte geöffneter Kisten lokal speichert. Über eine Suche mit Item-Raster findest du anschließend die passenden Kisten: Sie bekommen helle weiß-goldene Umrisse, die auch durch Wände sichtbar sind.

## Installieren

1. Installiere [Fabric Loader](https://fabricmc.net/use/installer/) für **Minecraft 26.3**, mindestens Version **0.19.5**.
2. Lege `mc-item-finder-1.0.0.jar` und [Fabric API 0.161.0+26.3](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0%2B26.3/fabric-api-0.161.0%2B26.3.jar) in den `mods`-Ordner deiner Minecraft-Installation.
3. Starte das Fabric-Profil. Minecraft 26.3 benötigt **Java 25**; beim offiziellen Launcher wird die passende Java-Laufzeit normalerweise mitgeliefert.

Die fertige Mod-Datei liegt in `build/libs/mc-item-finder-1.0.0.jar`. Die `-sources.jar` ist Quellcode und gehört nicht in den `mods`-Ordner. Der Mod wird nur auf deinem Client installiert; auf dem Server ist keine Installation nötig.

## Benutzen

- **O** schaltet „Kisten merken“ ein oder aus. Anfangs ist es **aus**. Die Einstellung bleibt nach einem Neustart erhalten.
- Öffne bei eingeschaltetem Merken die Kisten, die du später durchsuchen möchtest. Normale Kisten, Redstone-Kisten und Doppelkisten werden erfasst.
- **I** öffnet die Suche. Suche nach dem Itemnamen in deiner Spielsprache, einer ID wie `minecraft:diamond` oder einem Item-Tag wie `#minecraft:logs`.
- Klicke auf ein Item. Die Suche schließt sich und alle gespeicherten Kisten mit diesem Item erhalten einen leuchtenden Umriss. Die Zahlen unter den Icons zeigen die insgesamt gemerkte Item-Anzahl in der aktuellen Dimension.
- Öffne die Suche erneut und klicke auf **„Markierung löschen“**, um die Markierung zu beenden. Der Schalter „Kisten merken“ ist ebenfalls direkt in der Suche erreichbar.

Die Tasten kannst du unter **Optionen → Steuerung → Tastenbelegung → Chest Item Finder** ändern. Suche und Merken sind getrennt: Auch bei ausgeschaltetem Merken kannst du zuvor gespeicherte Kisten durchsuchen. Das Suchfenster funktioniert auch im Überlebensmodus und pausiert das Spiel nicht.

## Welche Daten gespeichert werden

Gespeichert werden Kistenposition, Dimension, Item-IDs, Stückzahlen und der Zeitpunkt der letzten Inhaltsänderung. Es wird auf die erste vollständige Übermittlung des geöffneten Kisteninventars gewartet; dein Spielerinventar und das Item am Mauszeiger werden nicht als Kisteninhalt gespeichert. Änderungen während des Öffnens sowie der sichtbare Stand beim Schließen werden übernommen. Doppelkisten zählen als eine Kiste.

Die Dateien liegen im Spielverzeichnis unter `config/itemfinder/`: `config.json` enthält den Schalter, `worlds/*.json` enthält die Kisten. Einzelspielerwelten sind anhand ihres Speicherpfads getrennt, Server anhand ihrer Adresse; Dimensionen werden zusätzlich getrennt. Beschädigte Speicherdateien werden als `.corrupt-…` gesichert. Der Mod verschickt keine gespeicherten Daten an andere Spieler.

Die Suche verwendet den **zuletzt gesehenen Inhalt**. Wenn Hopper oder andere Spieler eine geschlossene Kiste verändern, musst du sie mit eingeschaltetem Merken erneut öffnen, um den Stand zu aktualisieren. Ungeöffnete Kisten werden nie durchsucht. Endertruhen, Fässer, Shulkerkisten und reine Server-Menüs sind nicht Teil der Kistenerfassung. Die Markierung zeichnet einen leuchtenden Kistenumriss; sie nutzt eine eigene Darstellung im Stil des Spektralpfeils. Geladene Positionen ohne Kiste werden nicht markiert.

## Entwickeln und prüfen

Voraussetzung: JDK 25. Der Gradle-Wrapper verwendet Gradle 9.6.0; Fabric Loom 1.17.21, Loader 0.19.5 und Fabric API 0.161.0+26.3 sind festgelegt. Minecraft 26.3 ist nicht mehr obfuskiert, daher wird die normale `jar`-Task verwendet.

```powershell
.\gradlew.bat build
```

In diesem Workspace liegt zusätzlich eine portable JDK-25-Toolchain in `.tools/jdk25/`. `./build.ps1` verwendet sie automatisch, falls vorhanden, und hält den Gradle-Cache innerhalb des Projekts.

```powershell
.\build.ps1
```

Die automatisierten Tests prüfen Speicher-Roundtrip, Welt-/Dimensionsisolation, Doppelkisten, leere Kisten, beschädigte Dateien, den Schalter und die Zuordnung von geöffneten/synchronisierten Containern. Build und Tests sind automatisiert geprüft; die Grafik und Bedienung wurden noch nicht in einem laufenden Spiel geprüft.

Manueller Spieltest:

1. Merken ausschalten, eine Kiste mit Diamanten öffnen und nach Diamanten suchen: keine Markierung.
2. Merken einschalten, dieselbe Kiste öffnen, schließen und Diamanten auswählen: Markierung auch hinter einer Wand.
3. Zweite Kiste und Doppelkiste mit Diamanten öffnen: alle drei Standorte werden markiert, die Doppelkiste zählt einmal.
4. Alle Diamanten aus einer Kiste nehmen und sie schließen: diese Kiste wird nicht mehr markiert. Diamanten nur im Spielerinventar führen nicht zu einem Treffer.
5. Merken ausschalten und weitere Kisten öffnen: keine neuen Treffer; bereits gespeicherte bleiben suchbar.
6. Spiel neu starten, Welt und Dimension wechseln: Speicher bleibt erhalten und die Treffer bleiben ihrem Kontext zugeordnet.
7. Mit gehaltenem Block an eine Kiste schleichen und einen Block platzieren: kein neuer Kisteneintrag.

Lizenz: MIT.
