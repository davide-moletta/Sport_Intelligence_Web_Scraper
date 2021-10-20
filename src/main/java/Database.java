import org.neo4j.driver.*;

import java.util.List;

import static org.neo4j.driver.Values.parameters;

public class Database implements AutoCloseable {
    private final Driver driver;

    public Database(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    //Chiude la comunicazione con il database
    @Override
    public void close() {
        driver.close();
    }

    //Elimina qualsiasi nodo e relazione dal database
    public void resetDB() {
        try (Transaction tx = driver.session().beginTransaction()) {
            tx.run("MATCH (n)-[r]-() DELETE r");
            tx.run("MATCH (n) DELETE n");
            tx.commit();
        }
    }

    //Aggiunge un nodo di tipo Championship al database con il nome inviato dal crawler
    public void addChampionships(String ATPname) {
        try (Transaction tx = driver.session().beginTransaction()) {
            tx.run("CREATE (n:Championship) " +
                    "SET n.name = $name", parameters("name", ATPname));
            tx.commit();
        }
    }

    //Aggiunge un nodo di tipo Edition al database con il nome inviato dal crawler e crea la relazione con il rispettivo campionato
    public void addEdition(String ATPname, String ATPedition, String ATPyear) {
        try (Transaction tx = driver.session().beginTransaction()) {
            tx.run("MATCH (n:Championship) WHERE n.name= $ATPname " +
                    "CREATE (a:Edition) SET a.edName= $ATPedition " +
                    "CREATE (n)<-[:EditionOf {year: $year}]-(a)", parameters("ATPname", ATPname, "ATPedition", ATPedition, "year", ATPyear));
            tx.commit();
        }
    }

    //Formati dei vettori e liste inviati dal crawler
    //matchdata -> [editionName, date, firstPlayer, secondPlayer, result, firstPlayerResult, secondPlayerResult, location, field, round, length]
    //matchstatistics -> {STATISTICHE PARTITA, STATISTICHE SET 1, STATISTICHE SET 2, STATISTICHE SET 3,...}
    //matchhistory -> [[[STORICO SET 1], [STORICO SET 2], ...], [[TIEBREAK SET 1], [TIEBREAK SET 2], ...], [[FIFTEENS SET 1], [FIFTEENS SET 2], ...]]
    //quotes -> [bookmaker1: quota1-quota2, bookmaker2:quota1-quota2,...]
    public void addMatch(String[] matchdata, List<List<String>> matchstatistics, List<List<List<String>>> matchhistory, List<String> quotes) {
        String editionName, date, firstPlayer, result, secondPlayer, firstPlayerResult, secondPlayerResult, location, field, round, length;

        String statistics = "", games = "", tiebreaks = "", fifteens = "";

        //Controlla se la lista dello sotrico set è vuota, se non lo è prende i dati di ogni lista e crea le query da inserire nel database
        if (!matchhistory.isEmpty()) {
            //Costruisce la query contenente i dati relativi ai game giocati per ogni set
            for (int i = 0; i < matchhistory.get(0).size(); i++) {
                int j = i + 1;
                games += "m.set" + j + "Games=" + matchhistory.get(0).get(i) + ", ";
            }

            //Costruisce la query contenente i dati relativi agli eventuali tiebreak giocati per ogni set
            for (int i = 0; i < matchhistory.get(1).size(); i++) {
                int j = i + 1;
                tiebreaks += "m.set" + j + "Tiebreaks=" + matchhistory.get(1).get(i) + ", ";
            }

            //Costruisce la query contenente i dati relativi ai fifteen per ogni set
            for (int i = 0; i < matchhistory.get(2).size(); i++) {
                int j = i + 1;
                fifteens += "m.set" + j + "Fifteens=" + matchhistory.get(2).get(i) + ", ";
            }
        }

        //Salva nelle variabili i dati relativi al match
        editionName = matchdata[0];
        date = matchdata[1];
        firstPlayer = matchdata[2];
        secondPlayer = matchdata[3];
        result = matchdata[4];
        firstPlayerResult = matchdata[5];
        secondPlayerResult = matchdata[6];
        location = matchdata[7];
        field = matchdata[8];
        round = matchdata[9];
        length = matchdata[10];

        //Costruisce la query contenente i dati relativi alle statistiche di partita e per ogni set
        if (!matchstatistics.isEmpty()) {
            statistics = "m.matchStat=" + matchstatistics.get(0) + ", ";
            for (int i = 1; i < matchstatistics.size(); i++) {
                statistics += "m.set" + i + "Stat=" + matchstatistics.get(i) + ", ";
            }
        }

        if (quotes.isEmpty()) {
            quotes.add("no data");
        }

        //Controlla se i giocatori esistono già nel database, se esistono va avanti mentre se non esistono li crea
        if (!checkExistence("Player", "playerName", firstPlayer)) {
            addPlayer(firstPlayer);
        }
        if (!checkExistence("Player", "playerName", secondPlayer)) {
            addPlayer(secondPlayer);
        }

        //Invia la query al database per creare il match
        try (Transaction tx = driver.session().beginTransaction()) {
            tx.run("MATCH (n:Edition), (l:Player), (k:Player) " +
                            "WHERE n.edName=$edName AND l.playerName=$firstPlayer and k.playerName=$secondPlayer " +
                            "CREATE (m:Game) SET m.date=$date, m.result=$result, m.firstPlayer=$firstPlayerGame, m.secondPlayer=$secondPlayerGame, " +
                            "m.location=$location, m.field=$field, m.round=$round, m.length=$length, " +
                            statistics + "" + games + "" + tiebreaks + "" + fifteens +
                            "m.quotes=$quotes " +
                            "CREATE (n)<-[:GameOf]-(m), (m)<-[:PlayedIn {result:$playerResult1}]-(l), (m)<-[:PlayedIn {result:$playerResult2}]-(k)",
                    parameters("edName", editionName, "firstPlayer", firstPlayer, "secondPlayer", secondPlayer,
                            "date", date, "result", result, "firstPlayerGame", firstPlayer, "secondPlayerGame", secondPlayer,
                            "location", location, "field", field, "round", round, "length", length, "quotes", quotes,
                            "playerResult1", firstPlayerResult, "playerResult2", secondPlayerResult));
            tx.commit();
        }
    }

    //Aggiunge un nodo di tipo Player al database con il nome inviato dal crawler
    public void addPlayer(String playerName) {
        try (Transaction tx = driver.session().beginTransaction()) {
            tx.run("CREATE (a:Player) " +
                    "SET a.playerName = $name", parameters("name", playerName));
            tx.commit();
        }
    }

    //Controlla se un dato nodo esiste e ritorna TRUE se lo trova mentre ritorna FALSE se non lo trova
    public boolean checkExistence(String label, String attribute, String attributeValue) {
        boolean check;
        Result rs;

        try (Transaction tx = driver.session().beginTransaction()) {
            rs = tx.run("MATCH (n:" + label + ") WHERE n." + attribute + "=\"" + attributeValue + "\" RETURN n");

            check = rs.hasNext();
            tx.commit();
        }
        return check;
    }
}