import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class Server extends RemoteServer implements ServerInt {

    private LoginInfoList listReg;

    //metodo rmi
    public String register(String nickUtente, String password) throws RemoteException {
        //if (nickUtente==null) return "Error - username must be insert!";
        //if (password==null) return "Error - password must be insert!";
        //errore se username gia' usato
        String hash = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
            hash = sb.toString();
            listReg.addHash(nickUtente,hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "Server Error during registration";
        } catch (UsernameAlreadyExistException e) {
            return "Error - username already exist! Please select another one.";
        }
        return "ok";
    }

    public static void main(String[] args) {



    }


}
