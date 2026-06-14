# Cluedo Server

Dieses Repository stellt den Server für die Implementierung des Brettspiels
"Cluedo" dar. Das Projekt wurde im Rahmen der LV "Software Engineering II"
(621.250) an der AAU Klagenfurt entwickelt.

Cluedo ist ein bekanntes Detektiv-Brettspiel. Auf einem Spielbrett mit sechs
Räumen versuchen die Spieler herauszufinden, **welcher Verdächtige** mit
**welcher Waffe** in **welchem Raum** den Mord begangen hat. Dazu bewegen sie
sich würfelnd über das Spielfeld, äußern in Räumen Vermutungen und versuchen,
diese der Mitspieler zu widerlegen oder selbst zu widerlegen. Wer zuerst die
korrekte Lösung anklagt, gewinnt das Spiel.

Der Server ist in Java mit Spring Boot implementiert und kommuniziert über
WebSockets mit dem STOMP-Protokoll mit dem Android-Client
([`SE2-Cluedo-2026/App`](https://github.com/SE2-Cluedo-2026/App)). Er verwaltet
Lobby, Spielzustand, Spielfeld, Karten und die gesamte Spiellogik und
persistiert den Spielstand in einer MySQL-Datenbank.

## Login

Der Client erzeugt beim Start eine eindeutige Spieler-ID, die als
`playerKey` an den Server übermittelt wird. Über diese ID wird ein Spieler
serverseitig eindeutig identifiziert – auch bei einem Reconnect (z. B. wenn
die App kurzzeitig im Hintergrund war) wird der Spieler anhand seiner ID
seiner bestehenden Lobby bzw. seinem laufenden Spiel wieder zugeordnet
(`PLAYER_REJOINED` / `PLAYER_REJOINED_RUNNING`).

## Lobby

Beim Beitreten (`JOIN_LOBBY`) wird ein Spieler der einzigen aktuell
verwalteten Lobby hinzugefügt, solange das Spiel sich im Zustand `LOBBY`
befindet und noch nicht voll ist. Jeder Spieler wählt anschließend einen der
vier verfügbaren Charaktere (`MRS_LAVENDER`, `MRS_PINK`, `DR_RED`, `DR_BLUE`)
und markiert sich als bereit (`SET_CHARACTER_TYPE_AND_STATUS_READY`).

Das Spiel kann gestartet werden (`START_GAME`), sobald

- **mindestens 2** und **maximal 4 Spieler** in der Lobby sind, und
- **alle Spieler** sich als bereit markiert haben.

Ist die Lobby voll, erhalten weitere beitretende Spieler eine
`GAME_FULL`-Nachricht. Spieler können die Lobby vor Spielstart auch wieder
verlassen (`LEAVE_LOBBY`).

## Spielbeginn

Nach dem Start (`GAME_STARTED`) wird das Spielfeld mit sechs Räumen
(Küche, Salon, Arbeitszimmer, Ballsaal, Bibliothek, Billardzimmer)
initialisiert, jeder Spieler erhält seinen Charakter und eine Startposition,
und die Karten (Verdächtige, Waffen, Räume) werden gemischt. Jeweils eine
Verdächtigen-, Waffen- und Raumkarte wird zufällig als die Lösung des Falls
("Case File") festgelegt und vor allen Spielern verborgen; die restlichen
Karten werden gleichmäßig auf die Spieler verteilt.

Reihum würfelt der jeweils aktive Spieler (`ROLL_DICE`) und bewegt seine
Figur über das Spielfeld in Richtung eines Raums (`MOVE`). Der Server
verwaltet den Spielablauf über eine Zustandsmaschine mit den Phasen
`WAITING_FOR_ROLL`, `WAITING_FOR_MOVE`, `IN_ROOM`,
`WAITING_FOR_SUGGESTION_RESPONSE`, `WAITING_FOR_LIAR_DECISION` und
`TURN_ENDED`.

## Geheimgänge

Bestimmte Räume sind über Geheimgänge direkt miteinander verbunden und können
über `TAKE_HIDDEN_WAY` als Abkürzung genutzt werden, ohne über das
Spielfeld laufen zu müssen:

- Ballsaal ↔ Arbeitszimmer
- Billardzimmer ↔ Küche

## Vermutungen (Suggestions)

Befindet sich ein Spieler in einem Raum (`ENTER_ROOM`), kann er eine
Vermutung äußern (`MAKE_SUGGESTION`): eine Kombination aus Verdächtigem,
Waffe und dem aktuellen Raum. Der Server fragt daraufhin reihum die anderen
Spieler ab (`SUGGESTION_REQUEST`), ob sie eine der genannten Karten besitzen
und die Vermutung damit widerlegen können (`SUGGESTION_RESPONSE`). Das
Ergebnis wird allen Spielern mitgeteilt (`SUGGESTION_RESULT`).

## Schummeln

Wird ein Spieler aufgefordert, eine Vermutung zu widerlegen, besitzt aber
keine passende Karte, kann er einen Schummelversuch unternehmen
(`CHEAT_ATTEMPT`). Innerhalb eines Zeitfensters können andere Spieler einen
erkannten Schummelversuch melden (`CHEAT_BUTTON_PRESSED`); der Server wertet
aus, ob der Schummelversuch aufgedeckt wurde, und sendet das Ergebnis
(`CHEAT_RESULT`) inklusive der Information, welche Spieler geschummelt bzw.
den Schummler erfolgreich überführt haben.

## Anklage (Spielende)

Jeder Spieler kann jederzeit eine finale Anklage stellen
(`MAKE_ACCUSATION`) – eine Kombination aus Verdächtigem, Waffe und Raum.
Der Server vergleicht diese mit der zuvor festgelegten Lösung ("Case File"):

- Stimmt die Anklage mit der Lösung überein, gewinnt der anklagende Spieler
  und das Spiel endet (`GAME_FINISHED`).
- Ist die Anklage falsch, scheidet der Spieler aus den weiteren
  Vermutungsrunden aus (`isEliminated`), kann aber weiterhin würfeln und
  ziehen.

Das Spiel kann außerdem pausiert (`GAME_PAUSED`), fortgesetzt
(`CONTINUE_GAME`) oder abgebrochen werden (`GAME_ABORTED`), z. B. wenn nicht
genügend Spieler verbunden sind.

## Tech-Stack

- **Java 21**, **Spring Boot**
- **WebSocket/STOMP** für die Echtzeit-Kommunikation mit dem Client
- **MySQL** zur Persistierung des Spielstands
- **Docker** / `docker-compose` für lokale Entwicklung und Deployment (siehe
  [`docs/deployment.md`](docs/deployment.md))
