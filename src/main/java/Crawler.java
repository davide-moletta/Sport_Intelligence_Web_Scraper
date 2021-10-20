import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Crawler {
    //Crea un elemento Database usato per comunicare con il database di neo4j passando come parametri "URI", utente e password (default: "neo4j", "neo4j")
    public static Database database = new Database("bolt://localhost:7687", "neo4j", "admin");
    //Crea un driver che verrà usato per comandare il browser
    public static WebDriver driver;

    public static void main(String[] args) throws Exception {
        //Prepara il dirver di Selenium per Chrome e apre una scheda al link indicato
        System.setProperty("webdriver.chrome.driver", "C:\\Users\\david\\Desktop\\TESI\\chromedriver_win32\\chromedriver.exe");

        //La variabile options serve per passare delle impostazioni al driver, in questo caso permette di avviare il driver in modalità headless (senza effettivamente aprire chorme graficamente)
        //se rimossa il driver funzionerà ugualmente ma nella maniera tradizionale
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors", "--disable-extensions", "--no-sandbox", "--disable-dev-shm-usage");
        driver = new ChromeDriver(options);

        //Apre il browser al link indicato
        driver.get("https://www.diretta.it/tennis/atp-singolare/");

        //Richiama la funzione per pulire il database
        database.resetDB();

        findATPs();

        //Chiude tutte le connessioni con il database e con i driver selenium
        database.close();
        driver.close();
        driver.quit();
    }

    //Trova tutti i link dei campionati del tipo ATP singolare e li salva in una lista
    static void findATPs() throws InterruptedException {
        List<WebElement> WebATPs;
        List<String> ATPs = new ArrayList<>();

        //Clicca il pulsante per aprire tutta la lista dei campionati del tipo ATP per poi prendere i vari link
        driver.findElement(By.cssSelector("#lmenu_5724")).sendKeys(Keys.ENTER);
        Thread.sleep(100);

        //Prende gli elementi specificati dal sito
        WebATPs = driver.findElements(By.className("lmc__templateHref"));

        //Cicla tutti gli elementi trovati per creare i vari nodi e salvare i link ai campionati nella lista ATPs che verrà poi usata per trovare le edizioni
        for (WebElement WebATP : WebATPs) {
            //Crea un nodo con le informazioni del campionato
            database.addChampionships("ATP " + WebATP.getText());
            ATPs.add(WebATP.getAttribute("href"));
        }

        findEditions(ATPs);
    }

    //Apre tutti i link trovati precedentemente sulla pagina "archivio" in modo da trovare tutte le edizioni di ogni torneo e le salva in una lista
    static void findEditions(List<String> ATPs) throws InterruptedException {
        List<String> ATPeditions = new ArrayList<>(), editionNames = new ArrayList<>();
        List<WebElement> WebATPeditions;
        String ATPname, ATPyear;

        //Visita tutte le pagine degli ATP trovati precedentemente
        for (String atp : ATPs) {
            //Costruisce il link alla pagina "archivio" e reindirizza il driver selenium a quella pagina
            driver.get(atp + "/archivio/");

            //Prende gli elementi delle edizioni dal sito
            WebATPeditions = driver.findElements(By.xpath("//*[@class='leagueTable__season']/div/a"));
            //Prende il nome del campionato ATP per cui stanno venendo raccolte le edizioni
            ATPname = driver.findElement(By.className("teamHeader__name")).getText();

            //Cicla tutti gli elementi trovati
            for (WebElement WebATPedition : WebATPeditions) {
                //Divide la stringa del nome campionato per ottenere l'anno dell'edizione
                ATPyear = WebATPedition.getText().split(" ")[WebATPedition.getText().split(" ").length - 1];

                //Inserisce le edizioni nel database creando le relazioni con il rispettivo campionato
                database.addEdition(ATPname, WebATPedition.getText(), ATPyear);

                editionNames.add(WebATPedition.getText());

                //Prende il valore href dell'edizione e lo inserisce nella lista ATPeditions
                ATPeditions.add(WebATPedition.getAttribute("href"));
            }
        }
        findEditionsGame(ATPeditions, editionNames);
    }

    //Apre tutti i link delle edizioni sulla pagina "risultati" in modo da trovare tutte le informazioni per ogni partita svolta
    static void findEditionsGame(List<String> ATPeditions, List<String> editionNames) throws InterruptedException {
        String mainWindow, editionName, matchId, matchCode;
        List<WebElement> matches;
        String[] match_data;
        List<String> quotes = new ArrayList<>();
        List<List<String>> match_statistics = new ArrayList<>();
        List<List<List<String>>> match_history = new ArrayList<>();
        int i = 0;

        //Cicla tutte le edizioni per ogni torneo ATP
        for (String ATPedition : ATPeditions) {
            System.out.println(editionNames.get(i));
            //Apre la pagina dei risultati dell'edizione
            driver.get(ATPedition + "risultati/");

            //Salva la pagina principale
            mainWindow = driver.getWindowHandle();

            //Controlla se è presente il tasto per mostrare tutti i risultati (alcuni sono nascosti) e lo clicca finchè non sparisce
            while (checkElementExistence("//*[@id='live-table']/div[1]/div/div/a")) {
                driver.findElement(By.xpath("//*[@id='live-table']/div[1]/div/div/a")).sendKeys(Keys.ENTER);
                Thread.sleep(500);
            }

            //Prende tutti i match presenti sul sito per il torneo attuale
            matches = driver.findElements(By.xpath("//div[@title=\"Clicca per i dettagli dell'incontro!\"]"));
            editionName = editionNames.get(i);

            //Stampa l'edizione attuale (controllo per quando funziona in modalità headless)
            System.out.println("EDIZIONE: " + editionName + "\n");

            //Cicla tutti i match trovati
            for (WebElement match : matches) {
                //Ottiene l'id del match
                matchId = match.getAttribute("id");

                //Divide la stringa id per trovare il valore utilizzato per creare il link alla pagina informazioni
                matchCode = matchId.split("_")[2];

                //Apre la pagina alle informazioni del match su una nuova tab
                ((JavascriptExecutor) driver).executeScript("window.open('https://www.diretta.it/partita/" + matchCode + "/#informazioni-partita', '_blank');");

                //Dopo aver aperto la tab con le informazioni partita imposta il driver per raccogliere informazioni da quella pagina
                switchWindow(mainWindow);

                //Salva nel vettore le informazioni relative al match con questo formato
                //[editionName, date, firstPlayer, secondPlayer, result, firstPlayerResult, secondPlayerResult, location, field, round, length]
                match_data = matchData(editionName);

                //Controlla se esistono le tab per passare alla scheda statistiche, storico e quote, se le trova le apre e ne salva i dati
                if (checkElementExistence("//*[@href='#informazioni-partita/statistiche-partite']")) {
                    driver.findElement(By.xpath("//*[@href='#informazioni-partita/statistiche-partite']")).sendKeys(Keys.ENTER);
                    //Ottiene le statistiche della partita come una lista di liste di stringhe nel formato
                    //{STATISTICHE PARTITA, STATISTICHE SET 1, STATISTICHE SET 2, STATISTICHE SET 3, ...}
                    match_statistics = matchStatistics();
                }
                if (checkElementExistence("//*[@href='#informazioni-partita/cronologia-dell-incontro']")) {
                    driver.findElement(By.xpath("//*[@href='#informazioni-partita/cronologia-dell-incontro']")).sendKeys(Keys.ENTER);
                    //Ottiene tutti i dati della cronologia incontro come una lista di liste di liste di stringhe nel formato
                    //[[[STORICO SET 1], [STORICO SET 2], ...], [[TIEBREAK SET 1], [TIEBREAK SET 2], ...], [[FIFTEENS SET 1], [FIFTEENS SET 2], ...]]
                    //dove la prima lista indica i game, la seconda indica i tiebreak e la terza indica i fifteens, il tutto per ogni set svolto
                    match_history = matchHistory();
                }
                if (checkElementExistence("//*[@href='#comparazione-quote']")) {
                    driver.findElement(By.xpath("//*[@href='#comparazione-quote']")).sendKeys(Keys.ENTER);
                    //Ottiene le quote della partita come una lista nel formato [bookmaker1: quota1-quota2, bookmaker2: quota1-quota2, ...] per ogni bookmaker
                    quotes = matchQuotes();
                }

                //Invia i dati del match al database per salvarli
                database.addMatch(match_data, match_statistics, match_history, quotes);

                //Pulisce le liste prima di passare al match successivo
                match_statistics.clear();
                match_history.clear();
                quotes.clear();

                //Chiude la tab aperta e torna alla pagina principale per passare al prossimo match
                driver.close();
                driver.switchTo().window(mainWindow);
            }
            i++;
        }
    }

    //[editionName, date, firstPlayer, secondPlayer, result, firstPlayerResult, secondPlayerResult, location, field, round, length]
    //Apre la pagina "Informazioni partita", ne raccoglie i dati e li salva in un vettore
    static String[] matchData(String editionName) {
        String[] matchData = new String[11];

        //Salva il nome dell'edizione
        matchData[0] = editionName;
        //Salva la data e l'ora in cui si è svolto il match
        matchData[1] = driver.findElement(By.xpath("//*[@class='duelParticipant__startTime']/div")).getText();

        //Salva i nomi di entrambi i giocatori
        List<WebElement> players = driver.findElements(By.xpath("//*[@class='participant__participantNameWrapper']/div/a"));
        matchData[2] = players.get(0).getText();
        matchData[3] = players.get(1).getText();

        //Salva il punteggio finale del match
        String score = driver.findElement(By.xpath("//*[@class='detailScore__wrapper']")).getText();

        //Controlla se la partita è stata vinta per ritiro o normalmente
        if (driver.findElement(By.xpath("//*[@class='detailScore__status']/span")).getText().equals("Finale")) {
            if (score.equals("-")) {
                //NO DATA
                matchData[4] = "no data";
                matchData[5] = "no data";
                matchData[6] = "no data";
            } else {
                //VITTORIA NORMALE
                //Controlla quale dei due risultati è maggiore per capire chi ha vinto la partita e salva i risultati di entrambi i giocatori
                if (Integer.parseInt(score.split("\n")[0]) > Integer.parseInt(score.split("\n")[2])) {
                    matchData[5] = "Winner";
                    matchData[6] = "Loser";
                } else {
                    matchData[5] = "Loser";
                    matchData[6] = "Winner";
                }
                //Salva il risultato
                matchData[4] = score.split("\n")[0] + "-" + score.split("\n")[2];
            }
        } else {
            //VITTORIA RITIRO
            if (checkElementExistence("//*[@class='detailScore__status']/span/div/a")) {
                //Controlla quale dei due giocatori si è ritirato e in base al risultato salva chi ha perso e chi ha vinto
                String retiredPlayer = driver.findElement(By.xpath("//*[@class='detailScore__status']/span/div/a")).getText();
                if (retiredPlayer.equals(matchData[2])) {
                    matchData[4] = "Victory by withdrawal";
                    matchData[5] = "Retired";
                    matchData[6] = "Winner";
                } else if (retiredPlayer.equals(matchData[3])) {
                    matchData[4] = "Victory by withdrawal";
                    matchData[5] = "Winner";
                    matchData[6] = "Retired";
                } else {
                    matchData[4] = "no data";
                    matchData[5] = "no data";
                    matchData[6] = "no data";
                }
            } else {
                matchData[4] = "no data";
                matchData[5] = "no data";
                matchData[6] = "no data";
            }
        }

        //Salva i dati relativi al luogo in cui si è svolto il match (posizione 7), superficie (posizione 8) e round (posizione 9)
        matchData[7] = driver.findElement(By.xpath("//*[@class='tournamentHeader__country']/a")).getText().split(", ")[0];
        if (driver.findElement(By.xpath("//*[@class='tournamentHeader__country']/a")).getText().split(", ").length > 1) {
            String fieldAndInfo = driver.findElement(By.xpath("//*[@class='tournamentHeader__country']/a")).getText().split(", ")[1];
            if (fieldAndInfo.split(" - ").length > 1) {
                matchData[8] = fieldAndInfo.split(" - ")[0];
                matchData[9] = fieldAndInfo.split(" - ")[1];
            } else {
                matchData[8] = fieldAndInfo;
                matchData[9] = "no data";
            }
        } else {
            matchData[8] = "no data";
            matchData[9] = "no data";
        }

        //Se esiste salva la durata della partita
        if (checkElementExistence("//*[@class='smh__time smh__time--overall']")) {
            matchData[10] = driver.findElement(By.xpath("//*[@class='smh__time smh__time--overall']")).getText();
        } else {
            matchData[10] = "no data";
        }

        return matchData;
    }

    //Apre la pagina "Statistiche partita" e salva i dati della partita e di tutti i set presenti in una lista di liste
    static List<List<String>> matchStatistics() {
        List<List<String>> allStatistics = new ArrayList<>();
        List<WebElement> webStatistics;

        //Raccoglie tutte le tab delle statistiche per il match e per i vari set
        List<WebElement> tabs = driver.findElements(By.xpath("//*[@class='subTabs tabs__detail--sub']/a"));

        //Per ogni tab trovata la apre e raccoglie i dati richiesti
        for (WebElement tab : tabs) {
            tab.sendKeys(Keys.ENTER);

            List<String> statisticsData = new ArrayList<>();

            //Raccoglie gli elementi in una lista nel formato [Statistica: StatP1-StatP2]
            webStatistics = driver.findElements(By.xpath("//*[@class='statCategory']"));

            //Controlla che ci siano dei dati da raccogliere
            if (webStatistics.isEmpty()) {
                statisticsData.add("\"no data\"");
            } else {
                //Aggiunge i dati trovati alla lista per il set attuale
                for (WebElement webStatistic : webStatistics) {
                    statisticsData.add("\"" + webStatistic.getText().split("\n")[1] + ": " + webStatistic.getText().split("\n")[0] + "-" + webStatistic.getText().split("\n")[2] + "\"");
                }
            }
            //Aggiunge la lista creata alla lista allStatistics in modo da avere una lista per il match e una per ogni set
            allStatistics.add(statisticsData);
        }
        return allStatistics;
    }

    //Apre la pagina "Storico partita" e salva i dati di tutti i set presenti in una lista di liste di liste
    static List<List<List<String>>> matchHistory() {
        List<List<List<String>>> allHistoryData = new ArrayList<>();

        List<List<String>> allSets = new ArrayList<>();
        List<List<String>> allTiebreaks = new ArrayList<>();
        List<List<String>> allFifteens = new ArrayList<>();

        boolean tiebreak = false;

        //Raccoglie tutte le tab dello storico per i vari set
        List<WebElement> tabs = driver.findElements(By.xpath("//*[@class='subTabs tabs__detail--sub']/a"));

        //Per ogni tab trovata la apre e raccoglie i dati richiesti
        for (WebElement tab : tabs) {
            tab.sendKeys(Keys.ENTER);

            List<String> setData = new ArrayList<>();
            List<String> tiebreakData = new ArrayList<>();
            List<String> fifteenData = new ArrayList<>();

            //Raccoglie i dati dal sito
            List<WebElement> webHistories = driver.findElements(By.xpath("//*[@class='matchHistoryRow']/div[3]"));

            //Controlla se sono presenti dei dati
            if (webHistories.isEmpty()) {
                setData.add("\"no data\"");
                tiebreakData.add("\"no data\"");
                fifteenData.add("\"no data\"");
            } else {
                for (WebElement webHistory : webHistories) {
                    //Per ogni riga dello storico controlla se i dati vanno slavati nella lista del set o del tiebreak
                    if (!tiebreak && webHistory.getText().split("\n").length > 3) {
                        tiebreak = true;

                        //Se è stato giocato un tiebreak il dato dell'ultima riga si presentava in questo modo es. 56-77 quindi va controllato come salvarlo e dove
                        String[] games = webHistory.getText().split("\n");
                        int firstPlayerGames, secondPlayerGames;

                        //Controlla come è diviso il dato per capire quale numero va effettivamente raccolto
                        if (games[1].equals("-")) {
                            //Il primo non è diviso
                            firstPlayerGames = Integer.parseInt(games[0]) % 10;

                            if (games.length > 3) {
                                //Il secondo è diviso
                                secondPlayerGames = Integer.parseInt(games[2]);
                            } else {
                                //Il secondo non è diviso
                                secondPlayerGames = Integer.parseInt(games[2]) / 10;
                            }
                        } else {
                            //Il primo è diviso
                            firstPlayerGames = Integer.parseInt(games[1]);
                            if (games.length > 4) {
                                //Il secondo è diviso
                                secondPlayerGames = Integer.parseInt(games[3]);
                            } else {
                                //Il secondo non è diviso
                                secondPlayerGames = Integer.parseInt(games[3]) / 10;
                            }
                        }

                        setData.add("\"" + firstPlayerGames + "-" + secondPlayerGames + "\"");
                    } else {
                        if (tiebreak) {
                            //Salvo per i tiebreak
                            tiebreakData.add("\"" + webHistory.getText().split("\n")[0] + "-" + webHistory.getText().split("\n")[2] + "\"");
                        } else {
                            //Salvo lo storico normale
                            setData.add("\"" + webHistory.getText().split("\n")[0] + "-" + webHistory.getText().split("\n")[2] + "\"");
                        }
                    }
                }

                if (tiebreakData.isEmpty()) {
                    tiebreakData.add("\"no data\"");
                }

                //Raccoglie i dati relativi ai fifteen e li salva nella lista
                List<WebElement> webFifteens = driver.findElements(By.xpath("//*[@class='matchHistoryRow__fifteens']"));
                for (WebElement webFifteen : webFifteens) {
                    String[] fifteens = webFifteen.getText().split("\n");
                    String fifteen = "\"";

                    for (int i = 0; i < fifteens.length; i++) {
                        fifteen += fifteens[i];
                    }
                    fifteen += "\"";

                    fifteenData.add(fifteen);
                }
            }

            //Aggiunge alle liste le liste create per ogni set e setta il flag tiebreak a false
            allSets.add(setData);
            allTiebreaks.add(tiebreakData);
            allFifteens.add(fifteenData);

            tiebreak = false;
        }

        //Aggiunge le liste contenenti tutti i dati dei set nella lista allHistoryData e la ritorna
        allHistoryData.add(allSets);
        allHistoryData.add(allTiebreaks);
        allHistoryData.add(allFifteens);

        return allHistoryData;
    }

    //Apre la pagina "Comparazione quote", ne raccoglie i dati e li salva in una lista
    static List<String> matchQuotes() {
        List<String> matchQuotes = new ArrayList<>(), bookmakers = new ArrayList<>(), quotes = new ArrayList<>();

        //Prende gli elementi delle quote e dei bookmaker dal sito
        List<WebElement> rows = driver.findElements(By.xpath("//*[@class='ui-table__row']"));
        List<WebElement> webBookmakers = driver.findElements(By.xpath("//*[@class='oddsCell__bookmaker oddsCell__bookmakerCell ']/a"));

        //Se le liste sono vuote segnala che non sono stati trovati dati altrimenti raccoglie i dati trovati
        if (rows.isEmpty() || webBookmakers.isEmpty()) {
            matchQuotes.add("\"no data\"");
        } else {
            //Aggiunge alla lista bookmaker il nome del bookmaker per ogni quota
            for (WebElement webBookmaker : webBookmakers) {
                bookmakers.add(webBookmaker.getAttribute("title"));
            }

            //Aggiunge alla lista matchQuotes le quote trovate
            for (WebElement row : rows) {
                matchQuotes.add(row.getText());
            }

            //Crea la lista da ritornare del tipo {[bookmaker1:quota1-quota2], [bookmaker1:quota1-quota2], ...}
            for (int i = 0; i < matchQuotes.size(); i++) {
                if (!matchQuotes.get(i).split("\n")[0].equals("-")) {
                    quotes.add(bookmakers.get(i) + ": " + matchQuotes.get(i).split("\n")[0] + "-" + matchQuotes.get(i).split("\n")[1]);
                }
            }
        }

        return quotes;
    }

    //Controlla se un dato elemento esiste nella pagina e ritorna TRUE se lo trova e FALSE se non lo trova
    static boolean checkElementExistence(String xpath) {
        boolean check = false;

        //Ignora il delay di 3 secondi automatico se l'oggetto cercato non esiste
        driver.manage().timeouts().implicitlyWait(0, TimeUnit.MILLISECONDS);
        //Controlla se è presente il pulsante per passare alla scheda quote (alcuni match non lo hanno) e lo clicca
        if (driver.findElements(By.xpath(xpath)).size() != 0) {
            check = true;
        }
        //Reimposta il delay di 3 secondi originale
        driver.manage().timeouts().implicitlyWait(3, TimeUnit.SECONDS);

        return check;
    }

    //Permette di passare dalla pagina principale ad una tab aperta durante la raccolta informazioni
    static void switchWindow(String mainWindow) {
        //Ottiene tutte le tab aperte al momento della chiamata
        Set<String> allWindows = driver.getWindowHandles();

        //Cicla le tab aperte saltando quella principale (dove siamo già) e apre la tab secondaria
        Iterator<String> i = allWindows.iterator();
        while (i.hasNext()) {
            String childWindow = i.next();
            if (!childWindow.equalsIgnoreCase(mainWindow)) {
                //Passa alla tab indicata
                driver.switchTo().window(childWindow);
            }
        }
    }
}