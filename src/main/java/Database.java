import org.neo4j.driver.*;

import java.util.List;

import static org.neo4j.driver.Values.parameters;

public class Database implements AutoCloseable {
    private final Driver driver;

    public Database(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    /* TRANSAZIONE TIPO PER INVIARE UNA QUERY AL DATABASE
        try (Transaction tx = driver.session().beginTransaction()) {
            tx.run("MATCH (n) DELETE n");
            tx.commit();
        }
     */

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
    //matchdata -> [editionName, date, firstPlayer, secondPlayer, result, firstPlayerResult, secondPlayerResult, location, duration]
    //matchstatistics -> {STATISTICHE PARTITA, STATISTICHE SET 1, STATISTICHE SET 2, STATISTICHE SET 3,...}
    //matchhistory -> [[0-1,1-1,...],[0-1,1-1,....],...]
    //quotes -> [bookmaker1: quota1-quota2, bookmaker2:quota1-quota2,...]
    public void addMatch(String[] matchdata, List<List<String>> matchstatistics, List<List<String>> matchhistory, List<String> quotes) {
        String editionName, date, firstPlayer, result, secondPlayer, firstPlayerResult, secondPlayerResult, location, duration;

        editionName = matchdata[0];
        date = matchdata[1];
        firstPlayer = matchdata[2];
        secondPlayer = matchdata[3];
        result = matchdata[4];
        firstPlayerResult = matchdata[5];
        secondPlayerResult = matchdata[6];
        location = matchdata[7];
        duration = matchdata[8];

        String statistics = "";
        if (!matchstatistics.isEmpty()) {
            statistics = "m.matchStat=" + matchstatistics.get(0) + ", ";

            for (int i = 1; i < matchstatistics.size(); i++) {
                statistics += "m.set" + i + "Stat=" + matchstatistics.get(i) + ", ";
            }
        }

        String history = "";
        if (!matchhistory.isEmpty()) {
            for (int i = 0; i < matchhistory.size(); i++) {
                history += "m.set" + i + 1 + "History=" + matchhistory.get(i) + ", ";
            }
        }

        if (quotes.isEmpty()) {
            quotes.add("QUOTE NON TROVATE PER QUESTA PARTITA");
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
                            "m.location=$location, m.duration=$duration, " +
                            statistics + "" + history +
                            "m.quotes=$quotes " +
                            "CREATE (n)<-[:GameOf]-(m), (m)<-[:PlayedIn {result:$playerResult1}]-(l), (m)<-[:PlayedIn {result:$playerResult2}]-(k)",
                    parameters("edName", editionName, "firstPlayer", firstPlayer, "secondPlayer", secondPlayer,
                            "date", date, "result", result, "firstPlayerGame", firstPlayer, "secondPlayerGame", secondPlayer,
                            "location", location, "duration", duration, "quotes", quotes, "playerResult1", firstPlayerResult, "playerResult2", secondPlayerResult));
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