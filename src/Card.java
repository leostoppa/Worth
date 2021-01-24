import java.util.ArrayList;

public class Card {

    private String nome;
    private String descrizione;
    private ArrayList<String> history; //spostamenti della card
    private String stato;

    public Card (String nome, String descrizione) {
        this.nome = nome;
        this.descrizione = descrizione;
        history = new ArrayList<>();
        history.add("Card "+nome+" creata e inserita nella lista todo");
        stato = "todo";
    }

    public String getNome() {
        return nome;
    }

    public String getStato() {
        return stato; //lista in cui si trova la card
    }

    public void setStato(String stato) {
        this.stato = stato;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public ArrayList<String> getHistory() {
        return history;
    }

    public void addToHistory(String evento) {
        history.add(evento);
    }

}
