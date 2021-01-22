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
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.StringTokenizer;

public class Client {

    static final int DEFAULT_PORT_SERVER = 2000;
    final static int DEFAULT_PORT_RMI = 1950;
    static final String DEFAULT_IP = "127.0.0.1";
    static final int MAX_SEG_SIZE = 512;

    public static void main(String[] args) {
        System.out.println("Mi sto connettendo a Worth all'indirizzo ip: " + DEFAULT_IP + " e porta: " + DEFAULT_PORT_SERVER + " ...");
        SocketChannel client = null;
        try {
            SocketAddress address = new InetSocketAddress(DEFAULT_IP, DEFAULT_PORT_SERVER);
            client = SocketChannel.open(address);
            System.out.println("Connessione a Worth avvenuta con successo su " + client);
            ByteBuffer inputWelcome = ByteBuffer.allocate(MAX_SEG_SIZE);
            client.read(inputWelcome); //leggo messaggio di benvenuto dal server
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
                        client.close();
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
                        if (tokenizer.countTokens() != 2) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : login username password");
                            System.out.println();
                        }else{
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //logout username
                    case "logout" : {
                        if (tokenizer.countTokens() != 1) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : logout username");
                            System.out.println();
                        }else{
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //listUsers
                    case "listUsers" : {
                        if (tokenizer.countTokens() != 0) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : listUsers");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                        break;
                    }
                    //listOnlineusers
                    case "listOnlineusers" : {
                        if (tokenizer.countTokens() != 0) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : listOnlineusers");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                    }
                    //addCard projectName cardName descrizione
                    case "addCard" : {
                        if (tokenizer.countTokens() != 3) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : addCard projectName cardName descrizione");
                            System.out.println();
                        } else {
                            //INVIO COMANDO AL SERVER
                            output.put(si.getBytes());
                            output.flip();
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
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
                            client.write(output);
                            //LEGGO RISPOSTA DEL SERVER
                            client.read(inputResponse);
                            inputResponse.flip();
                            System.out.println(new String(inputResponse.array(), StandardCharsets.UTF_8));
                        }
                    }
                    default:
                        System.out.println("Errore : comando non supportato");
                        //// TODO: 22/01/21 stampa help message
                }
            }
        } catch (ConnectException e) {
            System.out.println("Connessione con il server fallita!");
            if (client != null) {
                try {
                    client.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        } catch (RemoteException | NotBoundException e) {
            System.out.println("Errore : non e' stato possibile invocare il metodo remoto (rmi) register");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            if (client != null) {
                try {
                    client.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }
    }
}
