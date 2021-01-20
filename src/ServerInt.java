import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerInt extends Remote {
    String register (String nickUtente, String password) throws RemoteException;
}
