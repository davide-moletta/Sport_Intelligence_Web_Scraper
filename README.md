# Moletta_Davide_Tesi
Selenium scraper + neo4j graph database


Web scraper realizzato per raccogliere i dati relativi a tutti i tornei del tipo ATP singolare.
I dati raccolti vengono elaborati e salvati in un database a grafo (neo4j).

Esempio grafico dell'architettura del database (Caricherò al più presto il dump del database completo).

Il nodo di tipo Championship contiene le informazioni relative ad un determinato torneo ATP ed è collegato a tutte le edizioni svolte del torneo stesso.
Il nodo di tipo Edition contiene le informazioni relative ad una determinata edizione ed è collegato a tutte le partite svolte durante l'edizione stessa.
Il nodo di tipo Match contiene le informazioni relative ad una determinata partita ed è collegato a tutti i giocatori che hanno partecipato nella partita stessa.
Il nodo di tipo Player contiene le informazioni relative ad un determinato giocatore ed è collegato a tutte le partite in cui ha giocato.

La relazione Edition of segnala a quale torneo appartiene una specifica edizione e in che anno si è svolta.
La relazione Match of segnala in quale edizione è stato svolto un determinato match.
La relazione Played in segnala in quali partite ha giocato un giocatore e il risultato dell'incontro stesso (winner/loser).

![](Architettura%20Database.PNG)

