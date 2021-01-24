import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public interface ClientInt extends Remote {
    //metodo invocato dal server per aggiornare la lista utenti
    public void updateUserListInterface(HashMap<String, String> userOnline, Set<String> listAllUser) throws RemoteException;

}
