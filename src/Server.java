
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends RemoteServer implements ServerInt {

    //OVERVIEW : Server con multiplexing dei canali, metodo rmi per la registrazione, callback per lista utenti e multicast per le chat.
    //Persistenza delle registrazioni e dei progetti

    final static int DEFAULT_PORT_SERVER = 2000;
    final static int DEFAULT_PORT_RMI = 1950;
    final static int MAX_SEG_SIZE = 512;
    private String ipMulticast = "224.0.0.0";
    //HashMap <username, hash> -> contiene le info per il login
    //ACCESSO CONCORRENTE MULTITHREAD
    private ConcurrentHashMap<String,String> listUser;
    // contiene tutti i progetti creati
    //ACCESSO SEQUENZIALE
    private ArrayList<Progetto> listProgetti;
    //coppie socket add client - username --> con quale username il client si e' loggato
    //ACCESSO SEQUENZIALE
    final private HashMap<String,String> userOnline;
    //lista dei client registrati per la callback , coppie username - ClientInt
    final private HashMap<String,ClientInt> clients;

    public Server () throws RemoteException {

        //AVVIO IL SERVER -> RIPRISTINO LO STATO -> PERSISTENZA
        listUser = new ConcurrentHashMap<>();
        //setto file di login --> se non esiste lo creo, se esiste ripristino il contenuto
        File loginFile = new File ("Login.json");
        if (loginFile.length()>0) {
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<ConcurrentHashMap<String,String>>(){}.getType();
                Reader reader = Files.newBufferedReader(loginFile.toPath());
                listUser = gson.fromJson(reader,type);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            try {
                loginFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        listProgetti = new ArrayList<>();
        File backupDir = new File("progetti");
        if (backupDir.exists()) {
            File[] projectsDirList = backupDir.listFiles();
            if (projectsDirList != null) {
                for (File projectDir : projectsDirList) {
                    if (projectDir.isDirectory()) {
                        Progetto progetto = new Progetto(projectDir.getName());
                        progetto.setIpMulticast(getIpMulticast());
                        File[] listFilesProject = projectDir.listFiles();
                        if (listFilesProject != null) {
                            //aggiungi membri e card al progetto
                            for (File fileProject : listFilesProject) {
                                if (fileProject.getName().equals("listamembri.json")) {
                                    try {
                                        Gson gson = new Gson();
                                        Type type = new TypeToken<ArrayList<String>>() {}.getType();
                                        File file = new File("progetti/" + projectDir.getName() + "/listamembri.json");
                                        Reader reader = Files.newBufferedReader(file.toPath());
                                        progetto.setListaMembri(gson.fromJson(reader, type));
                                    } catch (IOException e) {
                                        System.out.println("Errore nel caricamento della lista membri");
                                        e.printStackTrace();
                                    }
                                } else if (fileProject.isDirectory()) {
                                    File[] cardFileList = fileProject.listFiles();
                                    if (cardFileList != null) {
                                        try {
                                            for (File cardFile : cardFileList) {
                                                Gson gson = new Gson();
                                                File file = new File("progetti/"+projectDir.getName()+"/"+fileProject.getName()+"/"+cardFile.getName());
                                                Reader reader = Files.newBufferedReader(file.toPath());
                                                progetto.setCard(gson.fromJson(reader, Card.class), fileProject.getName());
                                            }
                                        } catch (ListNotFoundException | IOException e) {
                                            e.printStackTrace();
                                        }
                                    } //else System.out.println("cartella card : "+fileProject.getName()+" vuota");
                                }
                            }
                            //aggiungi il progetto pronto alla lista
                            listProgetti.add(progetto);
                        } //else System.out.println("cartella progetto : "+progetto.getNome()+" vuota");
                    } //else System.out.println("NON SONO DIRECTORY");
                }
            } //else System.out.println("cartella progetti vuota");
        }else {
            backupDir.mkdir();
        }
        userOnline = new HashMap<>();
        clients = new HashMap<>();
    }

    //METODI RMI

    public String register(String username, String password) throws RemoteException {
        try {
            //HASH PASSWORD CON BCRYPT
            String hash = BCrypt.hashpw(password,BCrypt.gensalt());
            //OPERAZIONE ATOMICA SU CONCURRENT HASH MAP
            if (listUser.putIfAbsent(username, hash) != null) return "Errore : username gia' utilizzato";
            //RENDO PERMANENTE SU FILE
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter("Login.json");
            gson.toJson(listUser, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateClient();
        return "ok";
    }

    //controlla il socket address x vedere se registrare o no
    public synchronized void registerForCallback(String username, ClientInt clientInterface) throws RemoteException {
            if (clients.putIfAbsent(username,clientInterface)==null) {
            System.out.println("Nuovo client registrato per le Callback");
            updateClient();
            sendIpMulticast(username);
        }
    }

    public synchronized void unregisterForCallback(String username) throws RemoteException {
        if (clients.remove(username)!=null) {
            System.out.println("Rimossa registrazione client per le Callback");
        }else{
            System.out.println("Impossibile registrare client per le Callback");
        }
    }

    public synchronized void updateClient () throws RemoteException {
        ArrayList<String> arraylist = new ArrayList<>(listUser.keySet());
        ArrayList<String> userToRemove = new ArrayList<>();
        //System.out.println("Inizio Callbacks");
        for (Map.Entry<String,ClientInt> entry : clients.entrySet()) {
            //System.out.println("AGGIORNO CLIENT");
            try {
                entry.getValue().updateUserListInterface(userOnline, arraylist);
            } catch (RemoteException e) {
                System.out.println("Client interrotto");
                userToRemove.add(entry.getKey());
            }
        }
        for (String u : userToRemove) {
            unregisterForCallback(u);
        }
        userOnline.values().removeAll(userToRemove);
        //System.out.println("Callback completate");
    }

    public synchronized void sendIpMulticast (String username) throws RemoteException {
        //System.out.println("username client : "+username);
        ClientInt client = clients.get(username);
        HashMap<String,String> listIpMulticast = new HashMap<>();
        for (Progetto p : listProgetti) {
            if (p.getListMembers().contains(username)) listIpMulticast.put(p.getNome(),p.getIpMulticast());
        }
        //if (client == null) System.out.println("CLIENT E' NULL");
        if (client!=null) {
            client.setIpMulticast(listIpMulticast);
        } //else utente offline, verra' aggiornato al prossimo login
    }

    //SERVER
    public static void main(String[] args) {

        System.out.println("Avvio il server in ascolto sulla porta "+DEFAULT_PORT_SERVER);

        ServerSocketChannel serverSocketChannel;
        Selector selector;
        Server server;
        try {
            //CREO RMI
            server = new Server();
            ServerInt stub = (ServerInt) UnicastRemoteObject.exportObject(server,0);
            LocateRegistry.createRegistry(DEFAULT_PORT_RMI);
            Registry r = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
            r.rebind("WORTH-SERVER",stub);
            //setto socket e apro il selector
            serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.bind(new InetSocketAddress(DEFAULT_PORT_SERVER));
            serverSocketChannel.configureBlocking(false);
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            Set <SelectionKey> readyKeys = selector.selectedKeys();
            Iterator <SelectionKey> iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                //rimuove la chiave dal selected set , ma non dal registered set
                try {
                    if (key.isAcceptable()) {
                        //creo connessione tcp con il client
                        SocketChannel client = serverSocketChannel.accept();
                        System.out.println("Connessione accetta col client : " + client);
                        client.configureBlocking(false);
                        SelectionKey keyClient = client.register(selector, SelectionKey.OP_READ);
                        String s = "Benvenuto in Worth sviluppato da Leonardo Stoppani";
                        byte[] bytes = s.getBytes();
                        ByteBuffer output1 = ByteBuffer.allocate(bytes.length);
                        output1.put(bytes);
                        output1.flip();
                        client.write(output1);
                        System.out.println("SEND : Welcome msg\n");
                        //System.out.println(client.getRemoteAddress().toString());
                    } else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer input = ByteBuffer.allocate(MAX_SEG_SIZE);
                        if (client.read(input) == -1) { //leggo dal channel tutta la stringa
                            System.out.println("Chiudo la connessione con il client...");
                            server.userOnline.remove(client.getRemoteAddress().toString());
                            server.updateClient();
                            key.cancel();
                            key.channel().close();
                            continue;
                        }
                        input.flip();//preparo buffer alla lettura
                        String si = new String(input.array(), StandardCharsets.UTF_8);
                        //System.out.println("FROM CLIENT : " + si);
                        //System.out.println(si.length());
                        StringTokenizer tokenizer = new StringTokenizer(si);
                        String cmd = tokenizer.nextToken().trim();
                        //System.out.println("CMD : " + cmd);
                        //PARAMETRI CORRETTI PERCHE' GIA' CONTROLLATI DAL CLIENT
                        ByteBuffer response = ByteBuffer.allocate(MAX_SEG_SIZE);
                        switch (cmd) {
                            case "login": {
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = tokenizer.nextToken();
                                String passw = tokenizer.nextToken().trim();
                                ConcurrentHashMap<String, String> listUser = server.listUser;
                                String hash;
                                //OPERAZIONE ATOMICA SU CONCURRENT HASH MAP
                                if ((hash = listUser.get(username)) == null) {//utente non registrato (bug risolto listusers == null)
                                    String s = "Errore : utente " + username + " non esiste";
                                    response.put(s.getBytes());
                                } else { //utente registrato, controllo passw
                                    if (BCrypt.checkpw(passw, hash)) {
                                        if (!server.userOnline.containsValue(username)) { //utente puo' loggarsi su un solo client
                                            server.userOnline.put(clientAddress,username);
                                            response.put((username + " logged in").getBytes());
                                            server.updateClient();
                                            //System.out.println("PASSW OK");
                                        } else {
                                            response.put("Errore : utente online su un altro client, deve essere prima scollegato".getBytes());
                                            //System.out.println("ALTRO UTENTE GIA' LOGGATO");
                                        }
                                    } else {
                                        response.put(("Errore : password errata").getBytes());
                                        //System.out.println("PASSW ERRATA");
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "logout": {
                                String clientAddress = client.getRemoteAddress().toString();
                                String usernameLoggato = server.userOnline.get(clientAddress);
                                if (server.userOnline.remove(clientAddress) == null)
                                    response.put("Errore : utente non loggato".getBytes());
                                else {
                                    response.put((usernameLoggato + " scollegato").getBytes());
                                    server.updateClient();
                                }
                                key.attach(response);
                                break;
                            }
                            case "listProjects": {
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) {
                                    response.put("Errore : utente non loggato".getBytes());
                                } else {
                                    ArrayList<Progetto> listProgetti = server.listProgetti;
                                    StringBuilder sProgetti = new StringBuilder();
                                    for (Progetto p : listProgetti) {
                                        if (p.getListMembers().contains(username)) {
                                            sProgetti.append("< ").append(p.getNome());
                                            sProgetti.append("\n");
                                        }
                                    }
                                    if (sProgetti.length() == 0)
                                        response.put("Non fai parte di alcun progetto".getBytes());
                                    else response.put(sProgetti.toString().getBytes());
                                }
                                key.attach(response);
                                break;
                            }
                            case "createProject": {
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                String nameProject = tokenizer.nextToken().trim();
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    if (server.contains(nameProject))
                                        response.put("Errore : nome del progetto non disponibile".getBytes());
                                    else {
                                        Progetto p = new Progetto(nameProject);
                                        try {
                                            p.setIpMulticast(server.getIpMulticast());
                                            p.addMember(username);
                                            server.listProgetti.add(p);
                                            server.sendIpMulticast(username);
                                            response.put("Progetto creato con successo".getBytes());
                                            //---------SALVO MODIFICA SU FILE SYSTEM------------//
                                            File dirProgetto = new File("progetti/"+nameProject);
                                            if (dirProgetto.mkdir()) {
                                                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                                                File fileMembri = new File("progetti/"+nameProject+"/listamembri.json");
                                                if (fileMembri.createNewFile()) {
                                                    Writer writer = new FileWriter(fileMembri);
                                                    gson.toJson(p.getListMembers(), writer);
                                                    writer.flush();
                                                    writer.close();
                                                }else System.out.println("Fallito backup lista membri");
                                                File dirTodo = new File("progetti/"+nameProject+"/todo");
                                                dirTodo.mkdir();
                                                File dirInprogress = new File("progetti/"+nameProject+"/inprogress");
                                                dirInprogress.mkdir();
                                                File dirToberevised = new File("progetti/"+nameProject+"/toberevised");
                                                dirToberevised.mkdir();
                                                File dirDone = new File("progetti/"+nameProject+"/done");
                                                dirDone.mkdir();
                                                System.out.println("Creata directory progetto : "+nameProject);
                                            }else System.out.println("Fallita creazione directory progetto "+nameProject);
                                            //-------------------------------------------------//
                                        } catch (MemberAlreadyExistException e) {
                                            response.put("Utente e' gia' membro del progetto".getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "addMember": {
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                String projectName = tokenizer.nextToken();
                                String userToAdd = tokenizer.nextToken().trim();
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    if (!server.listUser.containsKey(userToAdd))
                                        response.put(("Errore : utente " + userToAdd + " non registrato").getBytes());
                                    else {
                                        Progetto p = server.getProgetto(projectName);
                                        if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                        else if (!p.getListMembers().contains(username))
                                            response.put("Errore : non sei un membro del progetto".getBytes());
                                        else {
                                            try {
                                                p.addMember(userToAdd);
                                                response.put(("Utente " + userToAdd + " aggiunto al progetto con successo").getBytes());
                                                //-------SALVO MODIFICA SU FILE SYSTEM----------//
                                                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                                                File fileMembri = new File("progetti/"+projectName+"/listamembri.json");
                                                Writer writer = new FileWriter(fileMembri);
                                                gson.toJson(p.getListMembers(), writer);
                                                writer.flush();
                                                writer.close();
                                                //--------------------------------------//
                                                if (server.userOnline.containsValue(userToAdd))
                                                    server.sendIpMulticast(userToAdd);
                                            } catch (MemberAlreadyExistException e) {
                                                response.put(("Utente " + userToAdd + " e' gia' un membro del progetto").getBytes());
                                            }
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "showMembers": {
                                String projectName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username))
                                        response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        ArrayList<String> listMembers = p.getListMembers();
                                        StringBuilder sList = new StringBuilder();
                                        for (String s : listMembers) {
                                            sList.append("<").append(s);
                                            sList.append("\n");
                                        }
                                        response.put(sList.toString().getBytes());
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "showCards": {
                                String projectName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username))
                                        response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        ArrayList<Card> listCards = p.getListCards();
                                        StringBuilder cList = new StringBuilder();
                                        for (Card c : listCards) {
                                            cList.append("< ").append("Card: ").append("nome=").append(c.getNome()).append(" ").append("stato=").append(c.getStato()).append("\n");
                                        }
                                        if (cList.length()==0) response.put(("Non ci sono card nel progetto "+projectName).getBytes());
                                        else response.put(cList.toString().getBytes());
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "showCard": {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username))
                                        response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            Card card = p.getCard(cardName);
                                            response.put(("Card: nome=" + card.getNome() + ", descrizione=" + card.getDescrizione() + ", stato=" + card.getStato()).getBytes());
                                        } catch (CardNotFoundException e) {
                                            response.put(("Errore : la card " + cardName + " non esiste nel progetto " + projectName).getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "addCard": {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken();
                                StringBuilder descrizione = new StringBuilder();
                                while (tokenizer.hasMoreTokens()) {
                                    descrizione.append(tokenizer.nextToken()).append(" ");
                                }
                                String descrizioneComp = descrizione.toString().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username))
                                        response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            Card card = new Card(cardName,descrizioneComp);
                                            p.addCard(card);
                                            response.put(("Card " + cardName + " aggiunta al progetto " + projectName + " con successo").getBytes());
                                            //----------SALVO MODIFICA SU FILE SYSTEM------------------//
                                            Gson gson = new GsonBuilder().setPrettyPrinting().create();
                                            File fileCard = new File("progetti/"+projectName+"/todo/"+cardName+".json");
                                            Writer writer = new FileWriter(fileCard);
                                            gson.toJson(card, writer);
                                            writer.flush();
                                            writer.close();
                                            //--------------------------------------------------------//
                                        } catch (CardAlreadyExistException e) {
                                            response.put(("Card " + cardName + " esiste gia' nel progetto " + projectName).getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "moveCard": {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken();
                                String listStart = tokenizer.nextToken();
                                String listDest = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username))
                                        response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            p.moveCard(cardName, listStart, listDest);
                                            response.put("ok".getBytes());
                                            //----------SALVO MODIFICA SU FILE SYSTEM------------------//
                                            Path source = Path.of("progetti/"+projectName+"/"+listStart+"/"+cardName+".json");
                                            Path dest = Path.of("progetti/"+projectName+"/"+listDest+"/"+cardName+".json");
                                            File tmp = new File("progetti/"+projectName+"/"+listDest+"/"+cardName+".json");
                                            tmp.createNewFile();
                                            Files.move(source,dest, StandardCopyOption.REPLACE_EXISTING);
                                            //--------------------------------------------------------//
                                        } catch (CardNotFoundException e) {
                                            response.put(("Errore : Card " + cardName + " non esiste nel progetto " + projectName).getBytes());
                                        } catch (WrongStartListException e) {
                                            response.put(("Errore : Card " + cardName + " non fa parte della lista " + listStart).getBytes());
                                        } catch (ListNotFoundException e) {
                                            response.put("Errore : liste non valide, le liste consentite sono todo,inprogress,toberevised,done".getBytes());
                                        } catch (IllegalMoveException e) {
                                            response.put(("Errore : spostamento card non consentito\n" +
                                                    "Spostamenti consentiti :\ntodo->inprogress\ninprogress->toberevised\ninprogress->done\ntoberevised->inprogress\ntoberevised->done").getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "getCardHistory": {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username))
                                        response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        try {
                                            ArrayList<String> cardHistory = p.getCardHistory(cardName);
                                            StringBuilder sList = new StringBuilder();
                                            for (String s : cardHistory) {
                                                sList.append("< ").append(s).append("\n");
                                            }
                                            response.put(sList.toString().getBytes());
                                        } catch (CardNotFoundException e) {
                                            response.put(("Errore : Card " + cardName + " non esiste nel progetto " + projectName).getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            case "cancelProject": {
                                String projectName = tokenizer.nextToken().trim();
                                String clientAddress = client.getRemoteAddress().toString();
                                String username = server.userOnline.get(clientAddress);
                                if (username == null) response.put("Errore : utente non loggato".getBytes());
                                else {
                                    Progetto p = server.getProgetto(projectName);
                                    if (p == null) response.put("Errore : il progetto non esiste".getBytes());
                                    else if (!p.getListMembers().contains(username))
                                        response.put("Errore : non sei un membro del progetto".getBytes());
                                    else {
                                        if (p.readyToCancel()) {
                                            server.listProgetti.remove(p);
                                            for (String userToUpdate :
                                                    p.getListMembers()) {
                                                server.sendIpMulticast(userToUpdate);
                                            }
                                            response.put(("Progetto " + projectName + " eliminato con successo").getBytes());
                                            //----------SALVO MODIFICA SU FILE SYSTEM------------//
                                            File dirToRemove = new File("progetti/"+projectName);
                                            if (server.deleteDirectory(dirToRemove)) System.out.println(projectName+" eliminato con successo");
                                            else System.out.println("Fallita rimozione progetto dal disco");
                                            //---------------------------------------------------//
                                        } else {
                                            response.put("Errore : per poter eliminare un progetto tutte le card devono essere nella lista done".getBytes());
                                        }
                                    }
                                }
                                key.attach(response);
                                break;
                            }
                            default:
                                response.put("Errore : Comando non riconosciuto dal server".getBytes());
                                key.attach(response);
                        }
                        key.interestOps(SelectionKey.OP_WRITE);
                    } else if (key.isWritable()) {
                        //mando il risultato del comando al client
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer output2 = (ByteBuffer) key.attachment();
                        if (output2 == null) {
                            System.out.println("Attachment vuoto --> response vuoto --> manca una risposta --> invio risposta default");
                            output2 = ByteBuffer.allocate(MAX_SEG_SIZE);
                            output2.put("Errore nel server : non e' stato possibile elaborare una risposta".getBytes());
                        }
                        output2.flip();
                        //System.out.println(new String(output2.array(),StandardCharsets.UTF_8));
                        client.write(output2);
                        //System.out.println("SEND : Response");
                        key.interestOps(SelectionKey.OP_READ);
                    }
                } catch (IOException e) {
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                    e.printStackTrace();
                }
            }
        }
    }
    //METODO DI SUPPORTO A UNIVOCITA' NOME PROGETTO - TRUE se esiste gia' un progetto di nome nameProject, FALSE altrimenti
    private boolean contains (String nameProject) {
        for (Progetto p : listProgetti) {
            if(p.getNome().equals(nameProject)) return true;
        }
        return false;
    }

    private Progetto getProgetto (String nameProject) {
        for (Progetto p : listProgetti) {
            if (p.getNome().equals(nameProject)) return p;
        }
        return null;
    }

    private String getIpMulticast () {
        while (!availableIp(ipMulticast)) {
            //incrementa di uno l'indirizzo
            //System.out.println(ipMulticast);
            String[] arrayString = ipMulticast.split("\\.");
            //System.out.println(arrayString.length);
            int primo = Integer.parseInt(arrayString[3]);
            int secondo = Integer.parseInt(arrayString[2]);
            int terzo = Integer.parseInt(arrayString[1]);
            int quarto = Integer.parseInt(arrayString[0]);
            if (primo<255) primo++;
            else if (secondo<255) {
                secondo++;
                primo=0;
            } else if (terzo<255) {
                terzo++;
                secondo=0;
                primo=0;
            } else if (quarto<239) {
                quarto++;
                terzo=0;
                secondo=0;
                primo=0;
            } else { //239.255.255.255 --> torno al primo indirizzo 224.0.0.0
                quarto=224;
                terzo=0;
                secondo=0;
                primo=0;
            }
            ipMulticast = quarto+"."+terzo+"."+secondo+"."+primo;
        }
        return ipMulticast;
    }

    private boolean availableIp (String ipMulticast) {
        for (Progetto p: listProgetti) {
            if (p.getIpMulticast().equals(ipMulticast)) return false;
        }
        return true;
    }

    boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

}
