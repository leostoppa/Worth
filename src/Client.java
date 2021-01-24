import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
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
import java.util.concurrent.ConcurrentHashMap;

public class Client extends RemoteObject implements ClientInt{

    private static final int DEFAULT_PORT_SERVER = 2000;
    private final static int DEFAULT_PORT_RMI = 1950;
    private final static int DEFAULT_PORT_CALLBACK = 5000;
    private static final String DEFAULT_IP = "127.0.0.1";
    private static final int MAX_SEG_SIZE = 512;
    //contiene la lista di coppie user-stato (stato = online or offline)
    private Map<String,String> listAllUser;

    public Client() throws RemoteException {
        listAllUser = new HashMap<>();
    }

    //metodo rmi usato dal server per aggiornare la lista utenti
    public void updateUserListInterface(HashMap<String, String> userOnline, Set<String> listAllUser) throws RemoteException {
        for (String s : listAllUser) {
            if (userOnline.containsValue(s)) this.listAllUser.put(s, "online");
            else this.listAllUser.put(s,"offline");
        }
        System.out.println("Lista Utenti aggiornata");
    }

    public static void main(String[] args) {
        System.out.println("Mi sto connettendo a Worth all'indirizzo ip: " + DEFAULT_IP + " e porta: " + DEFAULT_PORT_SERVER + " ...");
        Client client;
        SocketChannel socketClient = null;
        try {
            client = new Client();
            ClientInt stub = (ClientInt) UnicastRemoteObject.exportObject(client,0);
            SocketAddress address = new InetSocketAddress(DEFAULT_IP, DEFAULT_PORT_SERVER);
            socketClient = SocketChannel.open(address);
            socketClient.socket().setSoTimeout(5000);
            System.out.println("Connessione a Worth avvenuta con successo su " + socketClient);
            ByteBuffer inputWelcome = ByteBuffer.allocate(MAX_SEG_SIZE);
            socketClient.read(inputWelcome); //leggo messaggio di benvenuto dal server
            inputWelcome.flip();
            System.out.println(new String(inputWelcome.array(), StandardCharsets.UTF_8));
            ByteBuffer output = ByteBuffer.allocate(MAX_SEG_SIZE);
            while (true) {
                //finche' non inserisco quit leggi comandi
                ByteBuffer inputResponse = ByteBuffer.allocate(MAX_SEG_SIZE);
                output.clear();
                System.out.printf("> ");
                Scanner scanner = new Scanner(System.in);
                String si = scanner.nextLine();
                if (si.length() > MAX_SEG_SIZE) {
                    System.out.println("Errore : la lunghezza massima del comando e' " + MAX_SEG_SIZE + " caratteri");
                    break;
                }
                StringTokenizer tokenizer = new StringTokenizer(si);
                String cmd = tokenizer.nextToken();
                //CONTROLLO SINTASSI COMANDI
                switch (cmd) {
                    case "quit" : { //COMANDO LATO CLIENT
                        socketClient.close();
                        scanner.close();
                        System.out.println("Closing connection to Server...");
                        return; //se ho inserito comando quit non mando niente al server e termino il client
                    }
                    case "register" : { //register username passw - COMANDO LATO CLIENT
                        String username;
                        String passw;
                        try {
                            username = tokenizer.nextToken();
                            passw = tokenizer.nextToken();
                        } catch (NoSuchElementException e) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : register username password");
                            //System.out.println();
                            break;
                        }
                        //recupero metodo remoto (rmi) dal server
                        Registry r = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                        Remote remoteObject = r.lookup("WORTH-SERVER");
                        ServerInt serverObject = (ServerInt) remoteObject;
                        //chiamo il metodo e stampo la risposta
                        System.out.println(serverObject.register(username,passw));
                        System.out.println();
                        break;
                    }
                    //login username passw
                    case "login" : {
                        // TODO: 22/01/21 errore se c'e' gia' un altro utente loggato 
                        if (tokenizer.countTokens() != 2) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : login username password");
                            System.out.println();
                        }else{
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            String response = new String(inputResponse.array(), StandardCharsets.UTF_8);
                            System.out.println(response);
                            if (response.contains("logged in")) {
                                Registry registry = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                                ServerInt server = (ServerInt) registry.lookup("WORTH-SERVER");
                                //CLIENT SI REGISTRA PER LA CALLBACK
                                System.out.println("Mi sto registrando per la callback...");
                                server.registerForCallback(stub);
                                System.out.println("Registrazione effettuata");
                            }
                        }
                        break;
                    }
                    //logout
                    case "logout" : {
                        if (tokenizer.countTokens() != 0) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : logout");
                            System.out.println();
                        }else{
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            String response = new String(inputResponse.array(), StandardCharsets.UTF_8);
                            System.out.println(response);
                            if (response.contains("scollegato")) {
                                Registry registry = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                                ServerInt server = (ServerInt) registry.lookup("WORTH-SERVER");
                                server.unregisterForCallback(stub);
                            }
                        }
                        break;
                    }
                    //listUsers - COMANDO LATO CLIENT - USA STRUTTURA DATI LOCALE
                    case "listUsers" : {
                        if (tokenizer.countTokens() != 0) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : listUsers");
                            System.out.println();
                        } else {
                            System.out.println(client.listAllUser.toString());
                        }
                        break;
                    }
                    //listOnlineusers - COMANDO LATO CLIENT - USA STRUTTURA DATI LOCALE
                    case "listOnlineusers" : {
                        if (tokenizer.countTokens() != 0) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : listOnlineusers");
                            System.out.println();
                        } else {
                            for (Map.Entry<String,String> entry : client.listAllUser.entrySet()) {
                                if (entry.getValue().equals("online")) System.out.println(entry.getKey());
                            }
                        }
                        break;
                    }
                    //listProjects
                    case "listProjects" : {
                        if (tokenizer.countTokens() != 0) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : listProjects");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //createProject projectName
                    case "createProject" : {
                        if (tokenizer.countTokens() != 1) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : createProject");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //addMember projectName username
                    case "addMember" : {
                        if (tokenizer.countTokens() != 2) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : addMember projectName username");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //showMembers projectName
                    case "showMembers" : {
                        if (tokenizer.countTokens() != 1) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : showMembers projectName");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //showCards projectName
                    case "showCards" : {
                        if (tokenizer.countTokens() != 1) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : showCards projectName");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //showCard projectName cardName
                    case "showCard" : {
                        if (tokenizer.countTokens() != 2) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : showCard projectName cardName");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //addCard projectName cardName descrizione
                    case "addCard" : {
                        if (tokenizer.countTokens() < 3) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : addCard projectName cardName descrizione");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //moveCard projectName cardName listaPartenza listaDestinazione
                    case "moveCard" : {
                        if (tokenizer.countTokens() != 4) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : moveCard projectName cardName listaPartenza listaDestinazione");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //getCardHistory projectName cardName
                    case "getCardHistory" : {
                        if (tokenizer.countTokens() != 2) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : getCardHistory projectName cardName");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //readChat projectName
                    case "readChat" : {
                        if (tokenizer.countTokens() != 1) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : readChat projectName");
                            System.out.println();
                        } else {
                            //// TODO: 22/01/21 implementa chat con multicast UDP
                        }
                        break;
                    }
                    //sendChatMsg projectName messaggio
                    case "sendChatMsg" : {
                        if (tokenizer.countTokens() != 2) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : sendChatMsg projectName messaggio");
                            System.out.println();
                        } else {
                            //// TODO: 22/01/21 implementa chat con multicast UDP
                        }
                        break;
                    }
                    //cancelProject projectName
                    case "cancelProject" : {
                        if (tokenizer.countTokens() != 1) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : cancelProject projectName");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    default:
                        System.out.println("Errore : comando non supportato");
                        //// TODO: 22/01/21 stampa help message
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout scaduto, connessione con il server interrotta");
            if (socketClient != null) {
                try {
                    socketClient.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        } catch (ConnectException e) {
            System.out.println("Connessione con il server fallita!");
            if (socketClient != null) {
                try {
                    socketClient.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        } catch (RemoteException | NotBoundException e) {
            System.out.println("Errore : non e' stato possibile invocare il metodo remoto (rmi) register");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            if (socketClient != null) {
                try {
                    socketClient.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }
    }
}
