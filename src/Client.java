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
            ByteBuffer output = ByteBuffer.allocate(MAX_SEG_SIZE);
            ByteBuffer input = ByteBuffer.allocate(MAX_SEG_SIZE);
            client.read(input); //leggo messaggio di benvenuto dal server
            input.flip();
            System.out.println(new String(input.array(), StandardCharsets.UTF_8));
            while (true) {
                //finche' non inserisco quit leggi comandi
                input.clear();
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
                switch (cmd) {
                    case "quit" : {
                        client.close();
                        scanner.close();
                        System.out.println("Closing connection to Server...");
                        return; //se ho inserito comando quit non mando niente al server e termino il client
                    }
                    case "register" : { //register username passw
                        String username;
                        String passw;
                        try {
                            username = tokenizer.nextToken();
                            passw = tokenizer.nextToken();
                        } catch (NoSuchElementException e) {
                            System.out.println("Errore : parametri non corretti");
                            System.out.println("Formato comando : register username password");
                            System.out.println();
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
                }
                /*
                //TCP
                //INVIO COMANDO AL SERVER
                output.put(si.getBytes());
                output.flip();
                client.write(output);
                //LEGGO RISPOSTA DEL SERVER
                client.read(input);
                input.flip();
                System.out.println(new String(input.array(), StandardCharsets.UTF_8));
                 */
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
