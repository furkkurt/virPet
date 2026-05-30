import java.nio.file.Path;

/**
 * Pet stats and actions. After each action (except internal), time passes: hunger and weight
 * shift, affection drifts down slowly; extreme weight damages health.
 */
public final class PetGameModel {

    //STATLARIN SINIRLARI
    public static final int STAT_MIN = 0;
    public static final int STAT_MAX = 100;

    /** Below this weight, body uses skinny art; above, fat art. */
    public static final int WEIGHT_SKINNY = 35;
    public static final int WEIGHT_FAT = 65;

    public static final int AFFECTION_SAD = 35;
    public static final int AFFECTION_HAPPY = 65;

    /** Outside this band, each time tick also drains health. */
    public static final int WEIGHT_HEALTH_LOW = 25;
    public static final int WEIGHT_HEALTH_HIGH = 75;

    private static final int TICK_HUNGER = 3;
    private static final int TICK_WEIGHT = -2;
    private static final int TICK_AFFECTION = -1;
    private static final int TICK_EXTREME_WEIGHT_HEALTH = -2;

    //Dosya yolları
    private final Path partsRoot;
    private final Path hatPath;
    private final Path legPath;
    private final int bodyStyleId;
    private final int faceStyleId;
    private final String petName;

    private int hunger;
    private int affection;
    private int weight;
    private int health;
    private boolean gameOver;
    private GameOverReason gameOverReason = GameOverReason.NONE;
    
    //Enum bu değerlerden birini seçeceğiz demek
    //bunların dışında bir seçersek [kazara] derleme aında derleyici bizi uyarır
    public enum GameOverReason {
        NONE,
        OBESITY,
        STARVATION,
        LEFT_HOME
    }
    
    //CONSTRUCTOR
    public PetGameModel(
            Path partsRoot, Path hatPath, Path legPath, int bodyStyleId, int faceStyleId, String petName) {
        this.partsRoot = partsRoot;
        this.hatPath = hatPath;
        this.legPath = legPath;
        this.bodyStyleId = bodyStyleId;
        this.faceStyleId = faceStyleId;
        //değer yoksa boş bir stringe dönüştürür bu sayede string türünde kalırız
        //boş değilse başta ve sondaki gereksiz boşlukları siler (trim[kırp] fonksiyonu)
        //TERNARY OPERATOR
        String trimmed = petName == null ? "" : petName.trim();
        //dönüştürdüğümüz string boşsa değeri 'Pet' olur. değilse direkt kırpılmış hali.
        this.petName = trimmed.isEmpty() ? "Pet" : trimmed;

        this.health = 100;
        this.affection = 70;
        this.hunger = 30;
        this.weight = 50;
    }
    
    //ENCAPCULATION
    public Path getHatPath() {
        return hatPath;
    }

    public Path getLegPath() {
        return legPath;
    }

    public int getHunger() {
        return hunger;
    }

    public int getAffection() {
        return affection;
    }

    public int getWeight() {
        return weight;
    }

    public int getHealth() {
        return health;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public GameOverReason getGameOverReason() {
        return gameOverReason;
    }

    public String getPetName() {
        return petName;
    }

    public String getGameOverMessage() {
        switch (gameOverReason) {
            case OBESITY:
                return petName + " died of obesity.";
            case STARVATION:
                return petName + " starved to death.";
            case LEFT_HOME:
                return petName + " left the house.";
            default:
                return "";
        }
    }

    /** Body PNG path from current weight and chosen body style id. */
    public Path resolveBodyPath() {
        //idStr id'nin integer'dan String'e dönmüş hali
        //klasörün ismi yazı olan 1 olduğu için tam sayıdan yazıya dönüştürdük
        String idStr = Integer.toString(bodyStyleId);

        //base içinde fat normal skinny versiyonları olan yol.
        //resolve bir klasöre girmek için Path sınıfının bir fonksiyonu
        Path base = partsRoot.resolve("bodies").resolve(idStr);

        if (weight < WEIGHT_SKINNY) {
            return base.resolve("skinny").resolve("body" + bodyStyleId + "S.png");
        }
        if (weight > WEIGHT_FAT) {
            return base.resolve("fat").resolve("body" + bodyStyleId + "F.png");
        }
        return base.resolve("normal").resolve("body" + bodyStyleId + ".png");
    }

    /** Face PNG path from current affection and chosen face style id. */
    public Path resolveFacePath() {
        String idStr = Integer.toString(faceStyleId);
        Path base = partsRoot.resolve("faces").resolve(idStr);
        if (affection < AFFECTION_SAD) {
            return base.resolve("sad").resolve("face" + faceStyleId + "S.png");
        }
        if (affection > AFFECTION_HAPPY) {
            return base.resolve("happy").resolve("face" + faceStyleId + "H.png");
        }
        return base.resolve("normal").resolve("face" + faceStyleId + ".png");
    }

    /** Applies an event card choice, then passive time passage (hunger up, etc.). */
    public void applyChoice(StatDelta delta) {
        //gameover true ise ya da delta'nın değerleri girilmediyse çık
        if (gameOver || delta == null) {
            return;
        }
        hunger = clamp(hunger + delta.hunger);
        affection = clamp(affection + delta.affection);
        weight = clamp(weight + delta.weight);
        health = clamp(health + delta.health);
        applyTimePassage();
    }

    private void applyTimePassage() {
        hunger = clamp(hunger + TICK_HUNGER);
        weight = clamp(weight + TICK_WEIGHT);
        affection = clamp(affection + TICK_AFFECTION);

        if (weight <= WEIGHT_HEALTH_LOW || weight >= WEIGHT_HEALTH_HIGH) {
            health = clamp(health + TICK_EXTREME_WEIGHT_HEALTH);
        }

        evaluateLossConditions();
    }

    private void evaluateLossConditions() {
        if (gameOver) {
            return;
        }
        if (affection <= 0) {
            affection = 0;
            gameOverReason = GameOverReason.LEFT_HOME;
            gameOver = true;
            return;
        }
        if (health <= 0) {
            health = 0;
            if (weight >= WEIGHT_HEALTH_HIGH) {
                gameOverReason = GameOverReason.OBESITY;
            } else if (weight <= WEIGHT_HEALTH_LOW) {
                gameOverReason = GameOverReason.STARVATION;
            } else {
                gameOverReason = weight >= 50 ? GameOverReason.OBESITY : GameOverReason.STARVATION;
            }
            gameOver = true;
        }
    }

    //min max arasında tutar
    private static int clamp(int v) {
        if (v < STAT_MIN) {
            return STAT_MIN;
        }
        if (v > STAT_MAX) {
            return STAT_MAX;
        }
        return v;
    }
}
