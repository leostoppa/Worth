import java.util.concurrent.ConcurrentHashMap;
//-----------------NON PIU' USATA!!!-----------------//

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

    public boolean contain (String username) {
        return listPassw.containsKey(username);
    }

}
