import java.util.ArrayList;

public class TestMain {

    public static void main(String[] args) {
        Progetto progetto = new Progetto("prova");
        System.out.println("Progetto: "+progetto.getNome());
        System.out.println();
        //aggiungo e stampo membri progetto
        //ADD MEMBER +
        progetto.addMember("leo");
        progetto.addMember("berti");
        ArrayList<String> listMember = progetto.getListMembers();
        System.out.println("Membri:");
        for (String s : listMember) {
            System.out.println(s);
        }
        System.out.println();
        //ADD CARD + SHOW CARD
        progetto.addCard("progetta_interfaccia","progettare la ui dell'app");
        progetto.addCard("testa_features","testare le nuove funzionalita'");
        ArrayList<Card> listCard = progetto.getListCards();
        System.out.println("Cards:");
        for (Card c : listCard) {
            System.out.println(c.getNome());
            System.out.println(c.getDescrizione());
            System.out.println(c.getStato());
            System.out.println(c.getHistory());
            System.out.println();
        }
        //GET CARD GIUSTO
        progetto.addCard("terza_card","descrizione terza card");
        Card card3 = null;
        try {
            card3 = progetto.getCard("terza_card");
        } catch (CardNotFoundException e) {
            System.out.println("Card non trovata nel progetto!");
        }
        if (card3!=null) {
            System.out.println(card3.getNome());
            System.out.println(card3.getDescrizione());
            System.out.println(card3.getStato());
            System.out.println(card3.getHistory());
            System.out.println();
        }
        //GET CARD SBAGLIATA
        card3=null;
        try {
            card3 = progetto.getCard("nomesbagliato");
        } catch (CardNotFoundException e) {
            System.out.println("Card non trovata nel progetto!");
            System.out.println();
        }
        if (card3!=null) {
            System.out.println(card3.getNome());
            System.out.println(card3.getDescrizione());
            System.out.println(card3.getStato());
            System.out.println(card3.getHistory());
            System.out.println();
        }
        //MOVE CARD
        try {
            progetto.moveCard("progetta_interfaccia","todo","inprogress");
        } catch (CardNotFoundException e) {
            System.out.println("Card non trovata nel progetto!");
        } catch (WrongStartListException e) {
            System.out.println("Card non si trova nella lista di partenza!");
        } catch (ListNotFoundException e) {
            System.out.println("Error Lista - Lista deve essere una tra todo,inprogress,toberevised,done");
        } catch (IllegalMoveException e) {
            System.out.println("Movimento della carta non supportato!");
        }

        //GET CARD HISTORY
        try {
            ArrayList<String> cardHistory = progetto.getCardHistory("progetta_interfaccia");
            System.out.println("Storia della Card");
            for (String e : cardHistory) {
                System.out.println(e);
            }
        } catch (CardNotFoundException e) {
            System.out.println("Card non trovata nel progetto!");
        }

    }

}
