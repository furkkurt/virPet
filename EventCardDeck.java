import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Random event cards; avoids repeating the same card twice in a row when possible. */
public final class EventCardDeck {

    private final List<EventCard> cards;
    private final Random random = new Random();
    private EventCard lastDrawn;

    public EventCardDeck(List<EventCard> cards) {
        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("cards must not be empty");
        }
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
    }

    public EventCard drawNext() {
        if (cards.size() == 1) {
            lastDrawn = cards.get(0);
            return lastDrawn;
        }
        EventCard next;
        do {
            next = cards.get(random.nextInt(cards.size()));
        } while (next == lastDrawn);
        lastDrawn = next;
        return next;
    }
}
