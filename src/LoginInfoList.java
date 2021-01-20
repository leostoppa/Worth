import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class LoginInfoList {
    //OVERVIEW : LISTA DI TUTTE LE INFO PER IL LOGIN DEGLI UTENTI
    //HashMap<Username,Hash>
    private ConcurrentHashMap<String,String> listPassw;

    public LoginInfoList () {
        listPassw = new ConcurrentHashMap<>();
    }

    public void addHash (String username, String hash) throws UsernameAlreadyExistException {
        if (listPassw.putIfAbsent(username,hash) != null) throw new UsernameAlreadyExistException();
    }

    public String getHash (String username) {
        return listPassw.get(username);
    }


}
