import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Client {

    static final int DEFAULT_PORT = 2000;
    static final String DEFAULT_IP = "127.0.0.1";
    static final int MAX_SEG_SIZE = 512;

    public static void main(String[] args) {
        System.out.println("Mi sto connettendo a Worth all'indirizzo ip: " + DEFAULT_IP + " e porta: " + DEFAULT_PORT + " ...");
        SocketChannel client = null;
        try {
            SocketAddress address = new InetSocketAddress(DEFAULT_IP, DEFAULT_PORT);
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
                System.out.println("> ");
                Scanner scanner = new Scanner(System.in);
                String si = scanner.nextLine();
                if (si.length() > MAX_SEG_SIZE) {
                    System.out.println("Errore : la lunghezza massima del comando e' " + MAX_SEG_SIZE + " caratteri");
                    break;
                }
                if (si.equals("quit")) {
                    client.close();
                    scanner.close();
                    System.out.println("Closing connection to Server...");
                    break;
                }
                //INVIO COMANDO AL SERVER
                output.put(si.getBytes());
                output.flip();
                client.write(output);
                //LEGGO RISPOSTA DEL SERVER
                client.read(input);
                input.flip();
                System.out.println(new String(input.array(),StandardCharsets.UTF_8));
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
