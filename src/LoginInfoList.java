import java.util.concurrent.ConcurrentHashMap;
//-----------------NON PIU' USATA!!!-----------------//

public class LoginInfoList {
    //OVERVIEW : LISTA DI TUTTE LE INFO PER IL LOGIN DEGLI UTENTI
    //HashMap<Username,Hash>
    private ConcurrentHashMap<String,String> listUser;

    public LoginInfoList () {
        listUser = new ConcurrentHashMap<>();
    }

    public void addHash (String username, String hash) throws UsernameAlreadyExistException {
        if (listUser.putIfAbsent(username,hash) != null) throw new UsernameAlreadyExistException();

    }

    public String getHash (String username) {
        return listUser.get(username);
    }

    public boolean contain (String username) {
        return listUser.containsKey(username);
    }

    public ConcurrentHashMap<String, String> getListUser() {
        return listUser;
    }
}
