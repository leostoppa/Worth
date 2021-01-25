import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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

public class Client extends RemoteObject implements ClientInt{

    private static final int DEFAULT_PORT_SERVER = 2000;
    private final static int DEFAULT_PORT_RMI = 1950;
    private static final String DEFAULT_IP = "127.0.0.1";
    private static final int MAX_SEG_SIZE = 512;
    //contiene la lista di coppie user-stato (stato = online or offline)
    final private Map<String,String> listAllUser;

    public Client() throws RemoteException {
        listAllUser = new HashMap<>();
    }

    //metodo rmi usato dal server per aggiornare la lista utenti
    public void updateUserListInterface(HashMap<String, String> userOnline, ArrayList<String> listAllUser) throws RemoteException {
        for (String s : listAllUser) {
            if (userOnline.containsValue(s)) this.listAllUser.put(s, "online");
            else this.listAllUser.put(s,"offline");
        }
        //client.print("Lista Utenti aggiornata");
    }

    public static void main(String[] args) {
        System.out.println("Mi sto connettendo a Worth all'indirizzo ip: " + DEFAULT_IP + " e porta: " + DEFAULT_PORT_SERVER + " ...");
        Client client;
        SocketChannel socketClient = null;
        String stato = "offline";
        try {
            client = new Client();
            ClientInt stub = (ClientInt) UnicastRemoteObject.exportObject(client,0);
            SocketAddress address = new InetSocketAddress(DEFAULT_IP, DEFAULT_PORT_SERVER);
            socketClient = SocketChannel.open(address);
            System.out.println("Connessione a Worth avvenuta con successo su " + socketClient);
            ByteBuffer inputWelcome = ByteBuffer.allocate(MAX_SEG_SIZE);
            socketClient.read(inputWelcome); //leggo messaggio di benvenuto dal server
            inputWelcome.flip();
            client.print(new String(inputWelcome.array(), StandardCharsets.UTF_8));
            ByteBuffer output = ByteBuffer.allocate(MAX_SEG_SIZE);
            while (true) {
                //finche' non inserisco quit leggi comandi
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
                String cmd = tokenizer.nextToken();
                //CONTROLLO SINTASSI COMANDI
                switch (cmd) {
                    case "quit" : { //COMANDO LATO CLIENT
                        socketClient.close();
                        scanner.close();
                        client.print("Closing connection to Server...");
                        System.exit(0); //se ho inserito comando quit non mando niente al server e termino il client
                    }
                    case "register" : { //register username passw - COMANDO LATO CLIENT
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
                        client.print(serverObject.register(username,passw));
                        break;
                    }
                    //login username passw
                    case "login" : {
                        // TODO: 22/01/21 errore se c'e' gia' un altro utente loggato 
                        if (tokenizer.countTokens() != 2) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : login username password");
                        }else{
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            String response = new String(inputResponse.array(), StandardCharsets.UTF_8);
                            client.print(response);
                            if (response.contains("logged in")) {
                                stato = "online";
                                Registry registry = LocateRegistry.getRegistry(DEFAULT_PORT_RMI);
                                ServerInt server = (ServerInt) registry.lookup("WORTH-SERVER");
                                //CLIENT SI REGISTRA PER LA CALLBACK
                                //client.print("Mi sto registrando per la callback...");
                                server.registerForCallback(stub);
                                //client.print("Registrazione effettuata");
                            }
                        }
                        break;
                    }
                    //logout
                    case "logout" : {
                        if (tokenizer.countTokens() != 0) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : logout");
                        }else{
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            String response = new String(inputResponse.array(), StandardCharsets.UTF_8);
                            client.print(response);
                            if (response.contains("scollegato")) {
                                stato = "offline";
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
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : listUsers");
                        } else {
                            if (stato.equals("online")) {
                                if (client.listAllUser.isEmpty()) {
                                    client.print("Nessun utente registrato al servizio");
                                }else client.print(client.listAllUser.toString());
                            } else client.print("Errore : utente non loggato");
                        }
                        break;
                    }
                    //listOnlineusers - COMANDO LATO CLIENT - USA STRUTTURA DATI LOCALE
                    case "listOnlineusers" : {
                        if (tokenizer.countTokens() != 0) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : listOnlineusers");
                        } else {
                            if (stato.equals("online")) {
                                boolean zeroUserOnline = true;
                                for (Map.Entry<String, String> entry : client.listAllUser.entrySet()) {
                                    if (entry.getValue().equals("online")) {
                                        client.print(entry.getKey());
                                        zeroUserOnline = false;
                                    }
                                }
                                if (zeroUserOnline) client.print("Nessun utente online");
                            }else client.print("Errore : utente non loggato");
                        }
                        break;
                    }
                    //listProjects
                    case "listProjects" : {
                        if (tokenizer.countTokens() != 0) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : listProjects");
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
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : createProject");
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //addMember projectName username
                    case "addMember" : {
                        if (tokenizer.countTokens() != 2) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : addMember projectName username");
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //showMembers projectName
                    case "showMembers" : {
                        if (tokenizer.countTokens() != 1) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : showMembers projectName");
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
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : showCards projectName");
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
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : showCard projectName cardName");
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //addCard projectName cardName descrizione
                    case "addCard" : {
                        if (tokenizer.countTokens() < 3) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : addCard projectName cardName descrizione");
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //moveCard projectName cardName listaPartenza listaDestinazione
                    case "moveCard" : {
                        if (tokenizer.countTokens() != 4) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : moveCard projectName cardName listaPartenza listaDestinazione");
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            socketClient.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            socketClient.read(inputResponse);
                            inputResponse.flip();
                            client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //getCardHistory projectName cardName
                    case "getCardHistory" : {
                        if (tokenizer.countTokens() != 2) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : getCardHistory projectName cardName");
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
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : readChat projectName");
                        } else {
                            //// TODO: 22/01/21 implementa chat con multicast UDP
                        }
                        break;
                    }
                    //sendChatMsg projectName messaggio
                    case "sendChatMsg" : {
                        if (tokenizer.countTokens() != 2) {
                            client.print("Errore : parametri non corretti");
                            client.print("Formato comando : sendChatMsg projectName messaggio");
                        } else {
                            //// TODO: 22/01/21 implementa chat con multicast UDP
                        }
                        break;
                    }
                    //cancelProject projectName
                    case "cancelProject" : {
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
                            client.print(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    default:
                        client.print("Errore : comando non supportato");
                        //// TODO: 22/01/21 stampa help message
                }
            }
        } catch (ConnectException e) {
            System.out.println("< Connessione con il server fallita!");
            if (socketClient != null) {
                try {
                    socketClient.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        } catch (RemoteException | NotBoundException e) {
            System.out.println("< Errore : non e' stato possibile invocare il metodo remoto (rmi) register");
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

    private void print (String s) {
        System.out.println("< "+s);
    }

}
