/** One Reigns-style dilemma: prompt text and stat deltas for yes / no. */
public final class EventCard {
    //KART SINIFININ ÖZELLİKLERİ
    private final String id;
    private final String text;
    private final StatDelta yes;
    private final StatDelta no;
    
    //CONSTRUCTOR FONKSİYONU (YAPICI)
    public EventCard(String id, String text, StatDelta yes, StatDelta no) {
        this.id = id;
        this.text = text;
        this.yes = yes;
        this.no = no;
    }
    
    //ENCAPCULATION
    //so we are not modifying the values we put in the text file. Read only.
    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public StatDelta getYes() {
        return yes;
    }

    public StatDelta getNo() {
        return no;
    }
}
