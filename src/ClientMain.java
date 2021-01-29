
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class ClientMain extends RemoteObject implements ClientInt{

    private static final int DEFAULT_PORT_SERVER = 2000;
    private static final int DEFAULT_PORT_RMI = 1950;
    private static final String DEFAULT_IP = "127.0.0.1";
    private static final int MAX_SEG_SIZE = 512;
    private static final int DEFAULT_PORT_MULTICAST = 3000;
    private String user;
    private String stato;

    //LISTA DI COPPIE <USER,STATO> CON STATO=ONLINE/OFFLINE - AGGIORNATA DALLE CALLBACK DEL SERVER
    final private Map<String,String> listAllUser;
    //LISTA DI COPPIE <PROJECTNAME,IPMULTICASTCHAT> - AGGIORNATA DALLE CALLBACK DEL SERVER
    private Map<String,String> listIpMulticast;
    //LISTA DI COPPIE <PROJECTNAME,CHAT> - AGGIORNATA DALLE CALLBACK DEL SERVER
    private Map<String,ArrayList<String>> listChatProgetti; //STRUTTURA CONDIVISA DA THREAD
    //LISTA DI COPPIE <PROJECTNAME,SOCKETCHAT> - AGGIORNATA DAL CLIENT A SECONDA DELLO USER ONLINE
    private Map<String, MulticastSocket> listMulticastSocket;
    //LISTA DI THREADLETTORI ATTIVI - AGGIORNATA DAL CLIENT A SECONDA DELLO USER ONLINE
    private ArrayList<Thread> threads;

    public ClientMain() throws RemoteException {
        user=null;
        stato="offline";
        listAllUser = new HashMap<>();
        listIpMulticast = new HashMap<>();
        listChatProgetti = new HashMap<>();
        listMulticastSocket = new HashMap<>();
        threads = new ArrayList<>();
    }

    //METODO RMI CHIAMATO DAL SERVER PER AGGIORNARE LA LISTA DEGLI USER E IL LORO STATO
    public void updateUserList(HashMap<String, String> userOnline, ArrayList<String> listAllUser) throws RemoteException {
        synchronized (this.listAllUser) {
            for (String s : listAllUser) {
                if (userOnline.containsValue(s)) this.listAllUser.put(s, "online");
                else this.listAllUser.put(s, "offline");
            }
        }
        //client.print("Lista Utenti aggiornata");
    }

    //METODO RMI CHIAMATO DAL SERVER PER SETTARE LE CHAT DEI PROGETTI DI CUI LO USER ONLINE SUL CLIENT E' MEMBRO
    public void setIpMulticast(HashMap<String, String> listIpMulticast) throws RemoteException {
        this.listIpMulticast = listIpMulticast;
        try {
            for (Map.Entry<String,String> entry : listIpMulticast.entrySet()) {
                MulticastSocket ms = new MulticastSocket(DEFAULT_PORT_MULTICAST);
                ms.joinGroup(InetAddress.getByName(entry.getValue()));
                listMulticastSocket.put(entry.getKey(), ms);
                listChatProgetti.put(entry.getKey(),new ArrayList<>());
                Thread thread = new Thread(new LettoreChat(listChatProgetti.get(entry.getKey()),ms,InetAddress.getByName(entry.getValue())));
                thread.start();
                threads.add(thread);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("Mi sto connettendo a Worth ...");
        ClientMain client;
        SocketChannel socketClient = null;
        try {
            client = new ClientMain();
            ClientInt stub = (ClientInt) UnicastRemoteObject.exportObject(client, 0);
            SocketAddress address = new InetSocketAddress(DEFAULT_IP, DEFAULT_PORT_SERVER);
            socketClient = SocketChannel.open(address);
            //System.out.println("Connessione a Worth avvenuta con successo su " + socketClient);
            ByteBuffer inputWelcome = ByteBuffer.allocate(MAX_SEG_SIZE);
            socketClient.read(inputWelcome); //leggo messaggio di benvenuto dal server
            inputWelcome.flip();
            client.print(new String(inputWelcome.array(), StandardCharsets.UTF_8));
            ByteBuffer output = ByteBuffer.allocate(MAX_SEG_SIZE);
            while (true) {
                ByteBuffer inputResponse = ByteBuffer.allocate(MAX_SEG_SIZE);
                output.clear();
                System.out.print("> ");
                Scanner scanner = new Scanner(System.in);
                String si = scanner.nextLine();
                if (si.length() > MAX_SEG_SIZE) {
                    client.print("Errore : la lunghezza massima del comando e' " + MAX_SEG_SIZE + " caratteri");
                    break;
                }
                StringTokenizer tokenizer = new StringTokenizer(si);
                try {
                    String cmd = tokenizer.nextToken();
                    //CONTROLLO SINTASSI COMANDI - LATO CLIENT COSI' EVITO SOVRACCARICO SERVER PER RICHIESTE RISOLVIBILI FACILMENTE DAL CLIENT

                    //ALLA CHIAMATA DI METODI CHE RICHIEDONO IL LOGIN (TUTTI TRANNE QUIT, REGISTER, HELP E LIST_USER) E' IL SERVER A CONTROLLARE CHE SIA STATO EFFETTUATO,
                    //MODIFICHE AL CODICE DEL CLIENT NON PERMETTONO IL LOGIN SENZA PASSWORD!!!
                    switch (cmd) {
                        case "help": {
                            System.out.println("Worth : strumento per la gestione di progetti collaborativi\n" +
                                    "Comandi :\n" +
                                    "(-) quit - Chiudi lo strumento\n" +
                                    "(-) register username password - Registra un nuovo utente\n" +
                                    "(-) login username password - Accedi con un utente\n" +
                                    "(-) logout - Esci dal tuo account\n" +
                                    "(-) list_users - Visualizza gli utenti registrati al servizio e il loro stato online/offline\n" +
                                    "(-) list_onlineusers -Visualizza gli utenti regitsrati al servizio e online in questo momento\n" +
                                    "(-) list_projects - Visualizza la lista dei progetti di cui l'utente e' membro\n" +
                                    "(-) create_project projectname - Crea un nuovo progetto\n" +
                                    "(-) add_member projectname username - Aggiunge l'utente username al progetto projectname\n" +
                                    "(-) show_members projectname - Visualizza la lista dei membri del progetto\n" +
                                    "(-) show_cards projectname - Visualizza la lista di card associate ad un progetto projectname\n" +
                                    "(-) show_card projectname cardname - Visualizza le informazioni della card cardname associata al progetto projectname\n" +
                                    "(-) add_card projectname cardname descrizione - Aggiunge la card cardname con la sua descrizione al progetto projectname\n" +
                                    "(-) move_card projectname cardname listaPartenza listaDestinazione - Sposta la card cardname del progetto projectname dalla lista listaPartenza alla lista listaDestinazione\n" +
                                    "(-) get_card_history projectname cardname - Visualizza lo storico degli spostamenti della card cardname del progetto projectname\n" +
                                    "(-) read_chat projectname - Visualizza i nuovi messaggi della chat del progetto projectname\n" +
                                    "(-) send_msg projectname messaggio - Invia un messaggio sulla chat del progetto projectname\n" +
                                    "(-) cancel_project projectname - Cancella un progetto completato (tutte le card nella lista done)\n");
                            break;
                        }
                        //COMANDO LATO CLIENT
                        case "quit": {
                            if (tokenizer.hasMoreTokens()) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : quit");
                            } else {
                                socketClient.close();
                                scanner.close();
                                client.print("Abbandono il servizio ...");
                                Registry registry = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                                ServerInt server = (ServerInt) registry.lookup("WORTH-SERVER");
                                server.unregisterForCallback(client.user);
                                System.exit(0); //se ho inserito comando quit non mando niente al server e termino il client
                            }
                            break;
                        }
                        //register username passw - COMANDO LATO CLIENT
                        case "register": {
                            String username;
                            String passw;
                            try {
                                username = tokenizer.nextToken();
                                passw = tokenizer.nextToken();
                            } catch (NoSuchElementException e) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : register username password");
                                break;
                            }
                            //recupero metodo remoto (rmi) dal server
                            Registry r = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                            Remote remoteObject = r.lookup("WORTH-SERVER");
                            ServerInt serverObject = (ServerInt) remoteObject;
                            //chiamo il metodo e stampo la risposta
                            client.print(serverObject.register(username, passw));
                            break;
                        }
                        //login username passw
                        case "login": {
                            if (tokenizer.countTokens() != 2) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : login username password");
                            } else {
                                if (client.stato.equals("online")) client.print("Errore : c'e' un utente gia' collegato, deve essere prima scollegato");
                                else {
                                    String username = tokenizer.nextToken();
                                    //INVIO COMANDO AL SERVER
                                    output.put(si.getBytes());
                                    output.flip();
                                    socketClient.write(output);
                                    //LEGGO RISPOSTA DEL SERVER
                                    if (socketClient.read(inputResponse) == -1) {
                                        client.print("Connessione col server persa");
                                        System.exit(-1);
                                    }
                                    inputResponse.flip();
                                    String response = new String(inputResponse.array(), StandardCharsets.UTF_8);
                                    client.print(response);
                                    //SE LOGIN CON SUCCESSO SETTO LE VARIABILI LOCALI E MI REGISTRO ALLE CALLBACK
                                    if (response.contains("online")) {
                                        client.user = username;
                                        client.stato = "online";
                                        Registry registry = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                                        ServerInt server = (ServerInt) registry.lookup("WORTH-SERVER");
                                        //CLIENT SI REGISTRA PER LA CALLBACK
                                        //client.print("Mi sto registrando per la callback...");
                                        server.registerForCallback(client.user, stub);
                                        //client.print("Registrazione effettuata");
                                    }
                                }
                            }
                            break;
                        }
                        //logout
                        case "logout": {
                            if (tokenizer.countTokens() != 0) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : logout");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                String response = new String(inputResponse.array(), StandardCharsets.UTF_8);
                                client.print(response);
                                //SE LOGOUT CON SUCCESSO SETTO VARIABILI LOCALI, ELIMINO LA REGISTRAZIONE ALLE CALLBACK E TERMINO I THREAD LETTORI
                                if (response.contains("scollegato")) {
                                    client.stato = "offline";
                                    Registry registry = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                                    ServerInt server = (ServerInt) registry.lookup("WORTH-SERVER");
                                    server.unregisterForCallback(client.user);
                                    for (Thread t : client.threads) {
                                        t.interrupt();
                                    }
                                    client.threads = new ArrayList<>();
                                    client.user = null;
                                    client.listChatProgetti = new HashMap<>();
                                    client.listMulticastSocket = new HashMap<>();
                                }
                            }
                            break;
                        }
                        //listUsers - COMANDO LATO CLIENT - USA STRUTTURA DATI LOCALE
                        case "list_users": {
                            if (tokenizer.countTokens() != 0) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : listUsers");
                            } else {
                                if (client.stato.equals("online")) {
                                    synchronized (client.listAllUser) {
                                        if (client.listAllUser.isEmpty()) {
                                            client.print("Nessun utente registrato al servizio");
                                        } else {
                                            for (Map.Entry<String,String> entry : client.listAllUser.entrySet()) {
                                                client.print(entry.getKey()+" : "+entry.getValue());
                                            }
                                        }
                                    }
                                } else client.print("Errore : utente non loggato");
                            }
                            break;
                        }
                        //listOnlineusers - COMANDO LATO CLIENT - USA STRUTTURA DATI LOCALE
                        case "list_onlineusers": {
                            if (tokenizer.countTokens() != 0) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : listOnlineusers");
                            } else {
                                if (client.stato.equals("online")) {
                                    boolean zeroUserOnline = true;
                                    synchronized (client.listAllUser) {
                                        for (Map.Entry<String, String> entry : client.listAllUser.entrySet()) {
                                            if (entry.getValue().equals("online")) {
                                                client.print(entry.getKey());
                                                zeroUserOnline = false;
                                            }
                                        }
                                    }
                                    if (zeroUserOnline) client.print("Nessun utente online");
                                } else client.print("Errore : utente non loggato");
                            }
                            break;
                        }
                        //listProjects
                        case "list_projects": {
                            if (tokenizer.countTokens() != 0) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : listProjects");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //createProject projectName
                        case "create_project": {
                            if (tokenizer.countTokens() != 1) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : createProject");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //addMember projectName username
                        case "add_member": {
                            if (tokenizer.countTokens() != 2) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : addMember projectName username");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //showMembers projectName
                        case "show_members": {
                            if (tokenizer.countTokens() != 1) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : showMembers projectName");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //showCards projectName
                        case "show_cards": {
                            if (tokenizer.countTokens() != 1) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : showCards projectName");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //showCard projectName cardName
                        case "show_card": {
                            if (tokenizer.countTokens() != 2) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : showCard projectName cardName");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //addCard projectName cardName descrizione
                        case "add_card": {
                            if (tokenizer.countTokens() < 3) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : addCard projectName cardName descrizione");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //moveCard projectName cardName listaPartenza listaDestinazione
                        case "move_card": {
                            if (tokenizer.countTokens() != 4) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : moveCard projectName cardName listaPartenza listaDestinazione");
                            } else {
                                String projectName = tokenizer.nextToken();
                                String cardName = tokenizer.nextToken();
                                String listPart = tokenizer.nextToken();
                                String listDest = tokenizer.nextToken().trim();
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                String s = new String(inputResponse.array(), StandardCharsets.UTF_8);
                                client.print(s);
                                if (s.contains("successo")) {
                                    MulticastSocket ms = client.listMulticastSocket.get(projectName);
                                    if (ms != null) {
                                        byte[] data;
                                        data = ("Card "+cardName+" del progetto "+projectName+" spostata dalla lista "+listPart+" alla lista "+listDest).getBytes();
                                        InetAddress ia = InetAddress.getByName(client.listIpMulticast.get(projectName));
                                        DatagramPacket dp = new DatagramPacket(data, data.length, ia, client.DEFAULT_PORT_MULTICAST);
                                        ms.send(dp); }
                                }
                            }
                            break;
                        }
                        //getCardHistory projectName cardName
                        case "get_card_history": {
                            if (tokenizer.countTokens() != 2) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : getCardHistory projectName cardName");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                if (socketClient.read(inputResponse) == -1) {
                                    client.print("Connessione col server persa");
                                    System.exit(-1);
                                }
                                inputResponse.flip();
                                System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                            }
                            break;
                        }
                        //readChat projectName
                        case "read_chat": {
                            try {
                                String projectName = tokenizer.nextToken().trim();
                                if (client.stato.equals("online")) {
                                    if (client.listChatProgetti.containsKey(projectName)) {
                                        for (String s : client.listChatProgetti.get(projectName)) {
                                            client.print(s);
                                        }
                                        client.listChatProgetti.get(projectName).clear();
                                    } else {
                                        client.print("Errore : non sei un membro del progetto " + projectName + " o non esiste");
                                        client.print("Fai parte dei seguenti progetti : " + client.listChatProgetti.keySet());
                                    }
                                } else client.print("Errore : utente non loggato");
                            } catch (NoSuchElementException e) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : readChat projectName");
                            }
                            break;
                        }
                        //sendChatMsg projectName messaggio
                        case "send_msg": {
                            try {
                                String projectName = tokenizer.nextToken();
                                StringBuilder messaggio = new StringBuilder();
                                while (tokenizer.hasMoreTokens()) {
                                    messaggio.append(tokenizer.nextToken()).append(" ");
                                }
                                String messsCompatto = client.user + " : " + messaggio.toString().trim();
                                if (client.stato.equals("online")) {
                                    MulticastSocket ms = client.listMulticastSocket.get(projectName);
                                    if (ms != null) {
                                        byte[] data;
                                        data = messsCompatto.getBytes();
                                        InetAddress ia = InetAddress.getByName(client.listIpMulticast.get(projectName));
                                        DatagramPacket dp = new DatagramPacket(data, data.length, ia, DEFAULT_PORT_MULTICAST);
                                        ms.send(dp);
                                        client.print("Messaggio inviato");
                                    } else
                                        client.print("Errore : non fai parte del progetto " + projectName + " o non esiste");
                                } else client.print("Errore : utente non loggato");
                            } catch (NoSuchElementException e) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : sendChatMsg projectName messaggio");
                            }
                            break;
                        }
                        //cancelProject projectName
                        case "cancel_project": {
                            if (tokenizer.countTokens() != 1) {
                                client.print("Errore : parametri non corretti");
                                client.print("Formato comando : cancelProject projectName");
                            } else {
                                //INVIO COMANDO AL SERVER
                                output.put(si.getBytes());
                                output.flip();
                                socketClient.write(output);
                                //LEGGO RISPOSTA DEL SERVER
                                socketClient.read(inputResponse);
                                inputResponse.flip();
                                String s = new String(inputResponse.array(), StandardCharsets.UTF_8);
                                client.print(s);
                            }
                            break;
                        }
                        default:
                            client.print("Errore : comando non supportato");
                            client.print("Usare il comando help per vedere tutti i comandi supportati");
                    }
                } catch (NoSuchElementException ignored) {
                }
            }
        } catch (NotBoundException | IOException e) {
            System.out.println("< Connessione con il server fallita!");
            System.exit(-1);
        }
    }

    private void print (String s) {
        System.out.println("< "+s);
    }

}
