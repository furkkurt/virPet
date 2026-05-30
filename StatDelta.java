/** Stat changes from an event card choice (missing JSON fields count as 0). */
public final class StatDelta {
    //SINIFIN ÖZELLİKLERİ
    public final int hunger;
    public final int affection;
    public final int weight;
    public final int health;

    //CONSTRUCTOR FONKSİYONU (YAPICI)
    //(sınıfla aynı isme sahip olan fonksiyon)
    //this bu sınıf demek
    public StatDelta(int hunger, int affection, int weight, int health) {
        this.hunger = hunger; //burdaki hunger parametre olan hunger
        this.affection = affection;
        this.weight = weight;
        this.health = health;
    }
    
    //hepsi 0 olan bir nesne
    public static final StatDelta ZERO = new StatDelta(0, 0, 0, 0);
}
