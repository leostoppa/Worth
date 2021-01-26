import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Map;

public class LettoreChat implements Runnable{

    private ArrayList<String> chat;
    private MulticastSocket ms;
    private InetAddress ipMulticast;

    public LettoreChat (ArrayList<String> chat, MulticastSocket ms, InetAddress ipMulticast) {
        this.chat = chat;
        this.ms = ms;
        this.ipMulticast = ipMulticast;
    }

    public void run() {
        byte [] buffer;
        while (true) {
            try {
                buffer = new byte[8192];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                ms.receive(dp);
                String s = new String(dp.getData());
                chat.add(s);
            } catch (IOException e) {
                e.printStackTrace();
                if (ms!=null) {
                    try {
                        ms.leaveGroup(ipMulticast);
                        ms.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                if (ms!=null) {
                    try {
                        ms.leaveGroup(ipMulticast);
                        ms.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
                return;
            }
        }
    }
}
