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
        driver.findElement(By.xpath("//*[@id='lmenu_5724']")).sendKeys(Keys.ENTER);
        Thread.sleep(500);

        //Prende gli elementi specificati dal sito
        WebATPs = driver.findElements(By.xpath("//*[@class='lmc__template ']/a"));

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
            ATPname = driver.findElement(By.xpath("//*[@class='teamHeader__name']")).getText();

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
        String[] matchdata;
        List<String> quotes = new ArrayList<>();
        List<List<String>> matchstatistics = new ArrayList<>(), matchhistory = new ArrayList<>();
        int i = 0;

        for (String ATPedition : ATPeditions) {
            //Apre la pagina dei risultati dell'edizione
            driver.get(ATPedition + "risultati/");

            //Salva la pagina principale
            mainWindow = driver.getWindowHandle();

            //Controlla se è presente il tasto per mostrare tutti i risultati (alcuni sono nascosti) e lo clicca finchè non sparisce
            while (checkElementExistence("//*[@id='live-table']/div[1]/div/div/a")) {
                driver.findElement(By.xpath("//*[@id='live-table']/div[1]/div/div/a")).sendKeys(Keys.ENTER);
                Thread.sleep(1000);
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
                //[editionName, date, firstPlayer, secondPlayer, result, firstPlayerResult, secondPlayerResult, location, duration]
                matchdata = matchData(editionName);

                //Controlla se esistono le tab per passare alla scheda statistiche, storico e quote, se le trova le apre e ne salva i dati
                if (checkElementExistence("//*[@href='#informazioni-partita/statistiche-partite']")) {
                    driver.findElement(By.xpath("//*[@href='#informazioni-partita/statistiche-partite']")).sendKeys(Keys.ENTER);
                    //Ottiene le statistiche della partita come una lista di liste di stringhe nel formato
                    //{STATISTICHE PARTITA, STATISTICHE SET 1, STATISTICHE SET 2, STATISTICHE SET 3, ...}
                    matchstatistics = matchStatistics();
                }
                if (checkElementExistence("//*[@href='#informazioni-partita/cronologia-dell-incontro']")) {
                    driver.findElement(By.xpath("//*[@href='#informazioni-partita/cronologia-dell-incontro']")).sendKeys(Keys.ENTER);
                    //Ottiene tutti i dati della cronologia incontro come una lista di liste di stringhe nel formato
                    // {{0-1,1-1,...}, {0-1,1-1,....}, ...} dove la prima lista indica il primo set mentre la seconda lista indica il secondo set etc.
                    matchhistory = matchHistory();
                }
                if (checkElementExistence("//*[@href='#comparazione-quote']")) {
                    driver.findElement(By.xpath("//*[@href='#comparazione-quote']")).sendKeys(Keys.ENTER);
                    //Ottiene le quote della partita come una lista nel formato [bookmaker1: quota1-quota2, bookmaker2: quota1-quota2, ...] per ogni bookmaker
                    quotes = matchQuotes();
                }

                //Invia i dati del match al database per salvarli
                database.addMatch(matchdata, matchstatistics, matchhistory, quotes);

                //Pulisce le liste prima di passare al match successivo
                matchstatistics.clear();
                matchhistory.clear();
                quotes.clear();

                //Chiude la tab aperta e torna alla pagina principale per passare al prossimo match
                driver.close();
                driver.switchTo().window(mainWindow);
            }
            i++;
        }
    }

    //Apre la pagina "Informazioni partita", ne raccoglie i dati e li salva in un vettore
    static String[] matchData(String editionName) {
        String[] matchData = new String[9];

        //Salva il nome dell'edizione
        matchData[0] = editionName;
        //Salva la data e l'ora in cui si è svolto il match
        matchData[1] = driver.findElement(By.xpath("//*[@class='startTime___2oy0czV']/div")).getText();

        //Salva i nomi di entrambi i giocatori
        List<WebElement> players = driver.findElements(By.xpath("//*[@class='participantNameWrapper___3cGNQoU']/div/a"));
        matchData[2] = players.get(0).getText();
        matchData[3] = players.get(1).getText();

        //Salva il punteggio finale del match
        String score = driver.findElement(By.xpath("//*[@class='wrapper___3rU3Jah']")).getText();

        //Controlla se la partita è stata vinta per ritiro o normalmente
        if (score.equals("-")) {
            //VITTORIA PER RITIRO
            //Se la partita finisce per ritiro imposta le stringhe come segue
            matchData[4] = "Retire"; //Risultato
            matchData[5] = "Retire"; //Risultato primo giocatore
            matchData[6] = "Retire"; //Risultato secondo giocatore
        } else {
            //VITTORIA NORMALE
            //Controlla quale dei due risultati è maggiore per capire chi ha vinto la partita
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

        //Salva i dati relativi alla superficie e il luogo in cui si è svolto il match
        matchData[7] = driver.findElement(By.xpath("//*[@class='country___24Qe-aj']/a")).getText();

        //Se esiste salva la durata della partita
        if (checkElementExistence("//*[@class='time___KK2Rji3 time--overall']")) {
            matchData[8] = driver.findElement(By.xpath("//*[@class='time___KK2Rji3 time--overall']")).getText();
        } else {
            matchData[8] = "DURATA MATCH NON TROVATA";
        }

        return matchData;
    }

    //Apre la pagina "Statistiche partita" e salva i dati della partita e di tutti i set presenti in una lista di liste
    static List<List<String>> matchStatistics() throws InterruptedException {
        List<List<String>> allStatistics = new ArrayList<>();

        //Raccoglie tutte le tab delle statistiche per il match e per i vari set
        List<WebElement> tabs = driver.findElements(By.xpath("//*[@class='subTabs']/a"));

        //Per ogni tab trovata la apre e richiama il metodo dataFetch
        for (WebElement tab : tabs) {
            tab.sendKeys(Keys.ENTER);
            Thread.sleep(500);

            allStatistics.add(dataFetch("//*[@class='statCategory___33LOZ_7']"));
        }

        return allStatistics;
    }

    //Apre la pagina "Storico partita" e salva i dati di tutti i set presenti in una lista di liste
    static List<List<String>> matchHistory() throws InterruptedException {
        List<List<String>> allGames = new ArrayList<>();

        //Raccoglie tutte le tab dello storico per i vari set
        List<WebElement> tabs = driver.findElements(By.xpath("//*[@class='subTabs']/a"));

        //Per ogni tab trovata la apre e richiama il metodo dataFetch
        for (WebElement tab : tabs) {
            tab.sendKeys(Keys.ENTER);
            Thread.sleep(500);

            allGames.add(dataFetch("//*[@class='matchHistory___3SdQ7EQ ']"));
        }

        return allGames;
    }

    //Raccoglie tutti gli elementi trovati con il dato XPATH e li salva in una lista che poi viene ritornata
    static List<String> dataFetch(String elementClass) {
        List<WebElement> webElements;
        List<String> data = new ArrayList<>();

        //Controlla a che classe appartengono gli elementi da raccogliere
        if (elementClass.equals("//*[@class='statCategory___33LOZ_7']")) {
            //Elementi del tipo statistica
            //Raccoglie gli elementi in una lista nel formato [Statistica: StatP1-StatP2]
            webElements = driver.findElements(By.xpath(elementClass));

            //Controlla che ci siano dei dati da raccogliere
            if (webElements.isEmpty()) {
                data.add("IMPOSSIBILE TROVARE QUESTO DATO");
            } else {
                for (WebElement element : webElements) {
                    data.add("\"" + element.getText().split("\n")[1] + ": " + element.getText().split("\n")[0] + "-" + element.getText().split("\n")[2] + "\"");
                }
            }
            return data;
        } else {
            //Elementi del tipo storico
            //Raccoglie gli elementi in una lista nel formato [P1-P2]
            webElements = driver.findElements(By.xpath(elementClass));

            //Controlla che ci siano dei dati da raccogliere
            if (webElements.isEmpty()) {
                data.add("IMPOSSIBILE TROVARE QUESTO DATO");
            } else {
                for (WebElement element : webElements) {
                    if (element.getText().split("\n")[0].equals("SERVIZIO PERSO")) {
                        data.add("\"" + element.getText().split("\n")[1] + "-" + element.getText().split("\n")[3] + "\"");
                    } else {
                        data.add("\"" + element.getText().split("\n")[0] + "-" + element.getText().split("\n")[2] + "\"");
                    }
                }
            }
            return data;
        }
    }

    //Apre la pagina "Comparazione quote", ne raccoglie i dati e li salva in una lista
    static List<String> matchQuotes() {
        List<String> matchQuotes = new ArrayList<>(), bookmakers = new ArrayList<>(), quotes = new ArrayList<>();

        //Prende gli elementi delle quote e dei bookmaker dal sito
        List<WebElement> rows = driver.findElements(By.xpath("//*[@class='ui table__row']"));
        List<WebElement> webBookmakers = driver.findElements(By.xpath("//*[@class='bookmakerPart___2Vexll_']/div/a"));

        //Se le liste sono vuote segnala che non sono stati trovati dati altrimenti raccoglie i dati trovati
        if (rows.isEmpty() || webBookmakers.isEmpty()) {
            matchQuotes.add("IMPOSSIBILE TROVARE QUESTO DATO");
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