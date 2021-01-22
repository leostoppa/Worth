
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
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends RemoteServer implements ServerInt {

    final static int DEFAULT_PORT_SERVER = 2000;
    final static int DEFAULT_PORT_RMI = 1950;
    final static int MAX_SEG_SIZE = 512;
    //HashMap <username, hash>
    private ConcurrentHashMap<String,String> listUser;

    public Server () throws RemoteException {
        listUser = new ConcurrentHashMap<>();
        //setto file di login --> se non esiste lo creo, se esiste ripristino il contenuto
        File loginFile = new File ("Login.json");
        if (loginFile.exists()) {
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
    }

    //metodo rmi x registrazione
    public String register(String username, String password) throws RemoteException {
        try {
            /*
            HASH CON CLASSE JAVA
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
             */
            //HASH PASSWORD CON BCRYPT
            String hash = BCrypt.hashpw(password,BCrypt.gensalt());
            //System.out.println("Hash generato e salvato : "+hash);
            //System.out.println(hash.length());
            System.out.println("Password inviata per register : "+password);
            System.out.println(password.length());
            if (listUser.putIfAbsent(username, hash) != null) return "Errore : username gia' utilizzato";
            //System.out.println(listUser.toString());
            //RENDO PERMANENTE SU FILE
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter("Login.json");
            gson.toJson(listUser, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "ok";
    }

    public static void main(String[] args) {

        System.out.printf("Server starting ... listening on port "+DEFAULT_PORT_SERVER);

        ServerSocketChannel serverSocketChannel;
        Selector selector;
        Server server;
        try {
            //creo rmi
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
                        System.out.println("Connection successfully accepted from "+client);
                        client.configureBlocking(false);
                        SelectionKey keyClient = client.register(selector,SelectionKey.OP_READ);
                        String s = "Benvenuto in Worth sviluppato da Leonardo Stoppani";
                        byte [] bytes = s.getBytes();
                        ByteBuffer output1 = ByteBuffer.allocate(bytes.length);
                        output1.put(bytes);
                        output1.flip();
                        client.write(output1);
                        System.out.println("SEND : Welcome msg\n");
                    }else if (key.isReadable()) {
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer input = ByteBuffer.allocate(MAX_SEG_SIZE);
                        if (client.read(input)==-1) { //leggo dal channel tutta la stringa
                            System.out.println("Closing connection with client...");
                            key.cancel();
                            key.channel().close();
                            continue;
                        }
                        input.flip();//preparo buffer alla lettura
                        String si = new String(input.array(), StandardCharsets.UTF_8);
                        System.out.println("FROM CLIENT : "+si);
                        //System.out.println(si.length());
                        // TODO: 21/01/21 parsing dei comandi + implementazione comandi
                        StringTokenizer tokenizer = new StringTokenizer(si);
                        String cmd = tokenizer.nextToken();
                        System.out.println("CMD : "+cmd);
                        //PARAMETRI CORRETTI PERCHE' GIA' CONTROLLATI DAL CLIENT
                        ByteBuffer response = ByteBuffer.allocate(MAX_SEG_SIZE);
                        switch (cmd) {
                            case "login" : {
                                String username = tokenizer.nextToken();
                                String passw = tokenizer.nextToken().trim();
                                System.out.println("Password inviata per login :"+passw);
                                System.out.println(passw.length());
                                String hash = server.listUser.get(username);
                                //System.out.println("Recupro hash da quelli salvati : "+hash);
                                //System.out.println(hash.length());
                                if (hash == null) {//utente non registrato
                                    String s = "Errore : utente " + username + " non esiste";
                                    response.put(s.getBytes());
                                } else { //utente registrato, controllo passw
                                    if (BCrypt.checkpw(passw, hash)) {
                                        response.put((username + " logged in").getBytes());
                                        System.out.println("PASSW OK");
                                    } else {
                                        response.put(("Errore : password errata").getBytes());
                                        System.out.println("PASSW ERRATA");
                                    }
                                }
                                key.attach(response);
                                //System.out.println(new String(response.array(),StandardCharsets.UTF_8));
                                //System.out.println("Hash nel file: "+hash);
                                //System.out.println("passw: "+passw);
                                break;
                            }
                            default:
                                response.put("Comando non riconosciuto dal server".getBytes());
                                key.attach(response);
                        }
                        key.interestOps(SelectionKey.OP_WRITE);
                    }else if (key.isWritable()) {
                        //mando il risultato del comando al client
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer output2 = ByteBuffer.allocate(MAX_SEG_SIZE);
                        output2 = (ByteBuffer) key.attachment();
                        output2.flip();
                        //System.out.println(new String(output2.array(),StandardCharsets.UTF_8));
                        client.write(output2);
                        System.out.println("SEND : Response");
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


}
