import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Pet creation (part cycling) and simple pet care game.
 * <p>
 * From project root compile <b>all</b> sources together (avoids mixed class versions):
 * {@code javac *.java && java PetCreationApp}
 * <p>
 * If you run on Java 8, compile with matching bytecode, e.g.
 * {@code javac -source 8 -target 8 PetCreationApp.java PetGameModel.java PetSpriteComposer.java}
 * and remove any old {@code .class} files first if you previously compiled with a newer JDK.
 */
public class PetCreationApp {

    private static final Path ASSETS = Paths.get("assets");
    private static final Path PARTS = ASSETS.resolve("parts");
    private static final Path PET_WINDOW_BG = ASSETS.resolve("bg.png");
    /** Native {@code bg.png} size (2730×1536) — preview panels keep this aspect ratio. */
    private static final int BG_NATIVE_WIDTH = 2730;
    private static final int BG_NATIVE_HEIGHT = 1536;
    private static final int PET_PANEL_DISPLAY_WIDTH = 360;
    /** Sprite scale relative to the largest size that fits the panel (smaller = tinier pet). */
    private static final float SPRITE_SIZE_FACTOR = 0.78f;
    /** Push sprite down so feet sit nearer the bottom of the bg (0 = centered). */
    private static final float SPRITE_DOWN_BIAS = 0.3f;

    private static final Color BUTTER_YELLOW = new Color(255, 244, 190);
    private static final Color COZY_TEXT = new Color(92, 72, 58);
    private static final Color COZY_BORDER = new Color(210, 188, 160);
    private static final Color COZY_BUTTON_BG = new Color(255, 246, 232);
    private static final Color COZY_BUTTON_HOVER_EDGE = new Color(225, 205, 180);

    private static final Color BAR_HUNGER = new Color(235, 175, 125);
    private static final Color BAR_AFFECTION = new Color(235, 155, 175);
    private static final Color BAR_WEIGHT = new Color(195, 170, 135);
    private static final Color BAR_HEALTH = new Color(155, 205, 165);
    private static final Color SOFT_NO = new Color(195, 100, 100);
    private static final Color SOFT_YES = new Color(100, 165, 115);
    private static final Color CHOICE_DISABLED = new Color(180, 170, 165);
    private static final Color GAME_OVER_TEXT = new Color(178, 58, 58);

    private static final float FONT_UI = 20f;
    private static final float FONT_EVENT = 28f;
    private static final float FONT_STATS = 20f;
    private static final float FONT_ARROW = 36f;
    private static final float FONT_CHOICE = 30f;
    private static final int ARROW_GAP = 48;
    private static final int PART_ROW_GAP = 22;
    private static final int PART_ROW_LABEL_GAP = 18;
    private static final int PART_ROW_SPACING = 16;
    private static final int PART_ROW_HEIGHT = 52;
    private static final int EVENT_TEXT_MAX_HEIGHT = 148;
    /** Extra pixels subtracted from wrap width so glyphs never clip at the edge. */
    private static final int EVENT_TEXT_WRAP_MARGIN = 20;
    /** Space below the name field before part rows. */
    private static final int NAME_BLOCK_BOTTOM_GAP = 20;
    private static final int PET_NAME_FIELD_HEIGHT = 44;
    private static final int CREATION_COLUMN_WIDTH = PET_PANEL_DISPLAY_WIDTH + 24;
    private static final int STAT_BAR_HEIGHT = 24;
    private static final int STAT_BAR_GAP = 10;

    private final List<Path> hatPaths;
    private final List<Path> legPaths;
    private final List<Integer> bodyIds;
    private final List<Integer> faceIds;

    private int hatIndex;
    private int legIndex;
    private int bodyIndex;
    private int faceIndex;

    private final JLabel hatStatus = new JLabel();
    private final JLabel bodyStatus = new JLabel();
    private final JLabel faceStatus = new JLabel();
    private final JLabel legStatus = new JLabel();

    private final CreationPreviewPanel creationPreview = new CreationPreviewPanel();
    private final GamePreviewPanel gamePreview = new GamePreviewPanel();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private final StatBarsPanel gameStatsBars = new StatBarsPanel();
    private final JTextPane eventCardText = new JTextPane();
    private JScrollPane eventScroll;
    private final JLabel lblNo = new JLabel("No");
    private final JLabel lblYes = new JLabel("Yes");
    private final JLabel lblStart = new JLabel("Start");
    private final JLabel lblRestart = new JLabel("Play again");
    private final JTextField petNameField = new JTextField();
    private JFrame mainFrame;
    private boolean eventChoicesActive;
    private boolean startLinkActive;

    private final List<EventCard> eventCards;
    private final Font baseFont;
    private EventCardDeck eventDeck;
    private EventCard currentEvent;

    private PetGameModel gameModel;
    private boolean gameOverDialogShown;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            //Assetsi bulamazsa hata alırız onu fırlatalım.
            try {
                new PetCreationApp().showFrame();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Could not load assets: " + e.getMessage() + "\nRun from the virPet project folder.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static Font loadGameFont() throws IOException, FontFormatException {
        Path fontPath = findFirstFontInAssets();
        if (fontPath == null || !Files.isRegularFile(fontPath)) {
            return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(FONT_UI));
        }
        try (InputStream in = Files.newInputStream(fontPath)) {
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
            return base;
        }
    }

    private static Path findFirstFontInAssets() throws IOException {
        String[] patterns = {"*.ttf", "*.TTF", "*.otf", "*.OTF"};
        for (String pattern : patterns) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(ASSETS, pattern)) {
                for (Path p : stream) {
                    return p;
                }
            }
        }
        return null;
    }

    private static void applyGlobalFonts(Font base) {
        Font ui = base.deriveFont(Font.PLAIN, FONT_UI);
        UIManager.put("Button.font", ui);
        UIManager.put("Label.font", ui);
        UIManager.put("Panel.font", ui);
        UIManager.put("OptionPane.font", ui);
        UIManager.put("OptionPane.messageFont", base.deriveFont(Font.PLAIN, FONT_EVENT));
        UIManager.put("OptionPane.buttonFont", ui);
    }

    private Font eventCardFont;
    private Font statsFont;
    private Font arrowFont;

    private void initComponentFonts(Font base) {
        eventCardFont = base.deriveFont(Font.PLAIN, FONT_EVENT);
        statsFont = base.deriveFont(Font.PLAIN, FONT_STATS);
        //bold yapmış virgülden sonra boyunu yazıyor!
        arrowFont = base.deriveFont(Font.BOLD, FONT_ARROW);
        //html formatı yapınca renk boyut kullanabiliyoruz.!
        eventCardText.setContentType("text/html");
        eventCardText.setEditable(false);
        //yazının kendi arkaplanı olmaz
        eventCardText.setOpaque(false);
        eventCardText.setBorder(null);
        //mouse gelince mavi olmaz
        eventCardText.setFocusable(false);
        eventCardText.setFont(eventCardFont);
        styleCozyTextField(petNameField, base);
    }

    private static void styleCozyTextField(JTextField field, Font base) {
        field.setBackground(COZY_BUTTON_BG);
        field.setForeground(COZY_TEXT);
        //imleç rengi
        field.setCaretColor(COZY_TEXT);
        field.setFont(base.deriveFont(Font.PLAIN, FONT_UI));

        // İkisini birleştir: dışta çizgi, içte boşluk
        field.setBorder(BorderFactory.createCompoundBorder(
                // İnce çizgi kenarlık (renk, kalınlık, yuvarlatılmış köşe)
                BorderFactory.createLineBorder(COZY_BUTTON_HOVER_EDGE, 1, true),
                // İç boşluk kenarlığı (üst, sol, alt, sağ piksel)
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
    }

    private static void styleCozyButton(javax.swing.AbstractButton b) {
        b.setBackground(COZY_BUTTON_BG);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setForeground(COZY_TEXT);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COZY_BUTTON_HOVER_EDGE, 1, true),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));
    }

    private static TitledBorder cozyTitledBorder(String title) {
        // Başlıklı kenarlık: çerçevenin üstünde küçük bir etiket
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COZY_BORDER, 1, true), title);
        border.setTitleColor(COZY_TEXT);
        return border;
    }

    private static BufferedImage loadPetWindowBackground() {
        if (!Files.isRegularFile(PET_WINDOW_BG)) {
            return null;
        }
        try {
            //burası resme bakıyor gerisi cırt
            return ImageIO.read(PET_WINDOW_BG.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private static void butterPanel(JPanel p) {
        p.setBackground(BUTTER_YELLOW);
        //arkaplanı var yağlı
        p.setOpaque(true);
    }

    private PetCreationApp() throws IOException, FontFormatException {
        baseFont = loadGameFont();
        applyGlobalFonts(baseFont);
        hatPaths = scanNumberedPng(PARTS.resolve("hats"), "hat");
        legPaths = scanNumberedPng(PARTS.resolve("legs"), "legs");
        bodyIds = scanStyleIdsWithNormalBody(PARTS.resolve("bodies"));
        faceIds = scanStyleIdsWithNormalFace(PARTS.resolve("faces"));
        eventCards = EventCardLoader.load(ASSETS.resolve("event_cards.cards"));

        if (hatPaths.isEmpty() && legPaths.isEmpty() && bodyIds.isEmpty() && faceIds.isEmpty()) {
            throw new IOException("No parts found under assets/parts.");
        }
    }

    private Font choiceFont(int style, float size) {
        return eventCardFont.deriveFont(style, size);
    }

    private void showFrame() {
        initComponentFonts(baseFont);

        //Virtual Pet başlıklı uygulama pencersi oluşturulur
        mainFrame = new JFrame("Virtual pet");
        //uygulamayı kapatınca arkada terminalde çalışan program da komple duruyor.
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                GameAudio.shutdown();
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        butterPanel(root);

        butterPanel(cards);
        cards.add(buildCreationCard(), "creation");
        cards.add(buildGameCard(), "game");

        root.add(cards, BorderLayout.CENTER);
        mainFrame.setContentPane(root);

        updateStatusLabels();
        creationPreview.refreshImages();
        installTapSounds(mainFrame);

        mainFrame.pack();
        //pencereyi ekrnın tam ortasına koyar.
        mainFrame.setLocationRelativeTo(null);
        //pencereyi görünür kılar
        mainFrame.setVisible(true);
        GameAudio.startBgMusic(ASSETS);
    }

    private void playTap() {
        GameAudio.playTap(ASSETS);
    }

    private void installTapSounds(JFrame frame) {
        Toolkit.getDefaultToolkit().addAWTEventListener(
                e -> {
                    if (!(e instanceof MouseEvent)) {
                        return;
                    }
                    MouseEvent me = (MouseEvent) e;
                    if (me.getID() != MouseEvent.MOUSE_PRESSED) {
                        return;
                    }
                    Component src = me.getComponent();
                    if (src != null && SwingUtilities.isDescendingFrom(src, frame)) {
                        playTap();
                    }
                },
                AWTEvent.MOUSE_EVENT_MASK);
    }

    private JPanel buildCreationCard() {
        JPanel card = new JPanel(new BorderLayout());
        butterPanel(card);

        creationPreview.setOpaque(false);
        creationPreview.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel previewWindow = wrapPetPanelInWindow("Preview", creationPreview);

        JPanel nameBlock = buildNameBlock();

        JPanel creationCenter = new JPanel();
        creationCenter.setLayout(new javax.swing.BoxLayout(creationCenter, javax.swing.BoxLayout.Y_AXIS));
        butterPanel(creationCenter);
        creationCenter.add(previewWindow);
        creationCenter.add(nameBlock);

        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));
        butterPanel(south);
        south.setBorder(BorderFactory.createEmptyBorder(12, 0, 8, 0));
        south.setAlignmentX(Component.CENTER_ALIGNMENT);
        south.add(creationPartRow("Hat", hatStatus, () -> cycleHat(-1), () -> cycleHat(1)));
        south.add(javax.swing.Box.createVerticalStrut(PART_ROW_SPACING));
        south.add(creationPartRow("Body", bodyStatus, () -> cycleBody(-1), () -> cycleBody(1)));
        south.add(javax.swing.Box.createVerticalStrut(PART_ROW_SPACING));
        south.add(creationPartRow("Face", faceStatus, () -> cycleFace(-1), () -> cycleFace(1)));
        south.add(javax.swing.Box.createVerticalStrut(PART_ROW_SPACING));
        south.add(creationPartRow("Legs", legStatus, () -> cycleLeg(-1), () -> cycleLeg(1)));

        JPanel southColumn = new JPanel();
        southColumn.setLayout(new javax.swing.BoxLayout(southColumn, javax.swing.BoxLayout.Y_AXIS));
        butterPanel(southColumn);
        southColumn.setAlignmentX(Component.CENTER_ALIGNMENT);
        southColumn.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        southColumn.add(south);
        southColumn.add(javax.swing.Box.createVerticalStrut(24));
        setupStartLink();
        updateStartLinkState();
        southColumn.add(buildStartBlock());

        card.add(creationCenter, BorderLayout.CENTER);
        card.add(southColumn, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildGameCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        butterPanel(card);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        updateGameStatsBars();

        gamePreview.setOpaque(false);
        gamePreview.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel petWindow = wrapPetPanelInWindow("Your pet", gamePreview);

        JPanel eventPanel = new JPanel(new BorderLayout(8, 10));
        butterPanel(eventPanel);
        eventPanel.setBorder(BorderFactory.createCompoundBorder(
                cozyTitledBorder("Event"),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        setEventCardContent(eventCardHtml("..."));

        setupChoiceLabel(lblNo, () -> resolveEvent(false));
        setupChoiceLabel(lblYes, () -> resolveEvent(true));

        JPanel choices = new JPanel(new FlowLayout(FlowLayout.CENTER, 56, 0));
        butterPanel(choices);
        choices.add(lblNo);
        choices.add(lblYes);
        setupRestartLink();
        lblRestart.setVisible(false);
        choices.add(lblRestart);

        JScrollPane eventScrollPane = createEventScrollPane();
        JPanel eventTextHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        butterPanel(eventTextHolder);
        eventTextHolder.add(eventScrollPane);
        eventPanel.add(eventTextHolder, BorderLayout.CENTER);
        eventPanel.add(choices, BorderLayout.SOUTH);
        int eventW = eventPanelWidthPx();
        eventPanel.setPreferredSize(new Dimension(eventW, EVENT_TEXT_MAX_HEIGHT + 80));
        eventPanel.setMaximumSize(new Dimension(eventW, Integer.MAX_VALUE));

        JPanel top = new JPanel(new BorderLayout());
        butterPanel(top);
        top.add(gameStatsBars, BorderLayout.NORTH);

        JPanel centerCol = new JPanel(new BorderLayout(0, 18));
        butterPanel(centerCol);
        centerCol.add(petWindow, BorderLayout.CENTER);
        centerCol.add(eventPanel, BorderLayout.SOUTH);

        card.add(top, BorderLayout.NORTH);
        card.add(centerCol, BorderLayout.CENTER);

        setEventChoicesEnabled(false);
        return card;
    }

    private void resolveEvent(boolean yes) {
        if (gameModel == null || gameModel.isGameOver() || currentEvent == null) {
            return;
        }
        StatDelta delta = yes ? currentEvent.getYes() : currentEvent.getNo();
        gameModel.applyChoice(delta);
        afterGameStep();
        if (!gameModel.isGameOver()) {
            showNextEvent();
        }
    }

    private void showNextEvent() {
        if (eventDeck == null) {
            return;
        }
        currentEvent = eventDeck.drawNext();
        setEventCardContent(eventCardHtml(personalizeForPet(currentEvent.getText())));
    }

    private String readPetNameFromField() {
        String name = petNameField.getText().trim();
        return name.isEmpty() ? "Pet" : name;
    }

    /** Replaces generic pet references in event copy with the chosen name. */
    private String personalizeForPet(String text) {
        if (text == null) {
            return "";
        }
        String name = gameModel != null ? gameModel.getPetName() : readPetNameFromField();
        return text.replace("Your pet", name).replace("your pet", name);
    }

    private static int eventPanelWidthPx() {
        return CREATION_COLUMN_WIDTH;
    }

    private static int eventTextWidthPx() {
        // Panel padding, titled border inset, and vertical scrollbar reserve.
        return eventPanelWidthPx() - 88;
    }

    private JPanel buildNameBlock() {
        JPanel block = new JPanel();
        block.setLayout(new javax.swing.BoxLayout(block, javax.swing.BoxLayout.Y_AXIS));
        butterPanel(block);
        block.setAlignmentX(Component.CENTER_ALIGNMENT);
        block.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        Dimension blockSize = new Dimension(
                CREATION_COLUMN_WIDTH, PET_NAME_FIELD_HEIGHT + 36 + NAME_BLOCK_BOTTOM_GAP);
        block.setPreferredSize(blockSize);
        block.setMaximumSize(blockSize);

        JLabel nameTitle = new JLabel("Name");
        nameTitle.setForeground(COZY_TEXT);
        nameTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameTitle.setFont(statsFont.deriveFont(Font.BOLD, FONT_UI + 2f));

        Dimension fieldSize = new Dimension(PET_PANEL_DISPLAY_WIDTH, PET_NAME_FIELD_HEIGHT);
        petNameField.setPreferredSize(fieldSize);
        petNameField.setMinimumSize(fieldSize);
        petNameField.setMaximumSize(fieldSize);
        petNameField.setAlignmentX(Component.CENTER_ALIGNMENT);

        block.add(nameTitle);
        block.add(javax.swing.Box.createVerticalStrut(8));
        block.add(petNameField);
        block.add(javax.swing.Box.createVerticalStrut(NAME_BLOCK_BOTTOM_GAP));
        return block;
    }

    private JPanel buildStartBlock() {
        JPanel block = new JPanel();
        block.setLayout(new javax.swing.BoxLayout(block, javax.swing.BoxLayout.Y_AXIS));
        butterPanel(block);
        block.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        block.add(lblStart);
        return block;
    }

    private JPanel creationPartRow(String title, JLabel status, Runnable onPrev, Runnable onNext) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, PART_ROW_GAP, 0));
        butterPanel(row);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.setPreferredSize(new Dimension(CREATION_COLUMN_WIDTH, PART_ROW_HEIGHT));
        row.setMaximumSize(new Dimension(CREATION_COLUMN_WIDTH, PART_ROW_HEIGHT));

        JLabel label = new JLabel(title);
        label.setForeground(COZY_TEXT);
        label.setFont(baseFont.deriveFont(Font.PLAIN, FONT_UI));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, PART_ROW_LABEL_GAP));

        status.setForeground(COZY_TEXT);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        status.setFont(baseFont.deriveFont(Font.PLAIN, FONT_UI));
        status.setPreferredSize(new Dimension(40, 24));

        row.add(label);
        row.add(createArrowLink("<", onPrev));
        row.add(status);
        row.add(createArrowLink(">", onNext));
        return row;
    }

    private JScrollPane createEventScrollPane() {
        int textW = eventTextWidthPx();
        eventScroll = new JScrollPane(eventCardText);
        eventScroll.setBorder(null);
        eventScroll.setOpaque(false);
        eventScroll.getViewport().setOpaque(false);
        eventScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eventScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        Dimension scrollSize = new Dimension(textW, EVENT_TEXT_MAX_HEIGHT);
        eventScroll.setPreferredSize(scrollSize);
        eventScroll.setMaximumSize(scrollSize);
        eventScroll.setMinimumSize(new Dimension(textW, 48));
        eventCardText.setMaximumSize(new Dimension(textW, Integer.MAX_VALUE));
        return eventScroll;
    }

    private void setEventCardContent(String html) {
        eventCardText.setText(html);
        refreshEventTextLayout();
    }

    private void refreshEventTextLayout() {
        int textW = eventTextWidthPx();
        eventCardText.setSize(new Dimension(textW, Short.MAX_VALUE));
        Dimension pref = eventCardText.getPreferredSize();
        int height = Math.max(pref.height + 6, 40);
        eventCardText.setPreferredSize(new Dimension(textW, height));
        eventCardText.setMaximumSize(new Dimension(textW, Integer.MAX_VALUE));
        if (eventScroll != null) {
            eventScroll.getViewport().revalidate();
            eventScroll.revalidate();
        }
    }

    private String eventCardHtml(String text) {
        return eventCardHtmlStyled(text, COZY_TEXT, false);
    }

    private String eventCardHtmlStyled(String text, Color color, boolean bold) {
        float pt = eventFontSizeFor(text);
        Font font = eventFontForDisplay(pt, bold);
        int wrapW = eventWrapWidthPx();
        List<String> lines = wrapTextToLines(text, font, wrapW);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                body.append("<br>");
            }
            body.append(escapeHtml(lines.get(i)));
        }
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        String weight = bold ? "bold" : "normal";
        return "<html><body style='margin:0;padding:0'><p align='center' style='margin:0;font-size:"
                + Math.round(pt)
                + "pt;font-weight:"
                + weight
                + ";color:rgb("
                + r
                + ","
                + g
                + ","
                + b
                + ");font-family:"
                + eventCardFont.getFamily()
                + "'>"
                + body
                + "</p></body></html>";
    }

    private Font eventFontForDisplay(float pt, boolean bold) {
        int style = bold ? Font.BOLD : Font.PLAIN;
        return eventCardFont.deriveFont(style, pt);
    }

    private int eventWrapWidthPx() {
        return Math.max(120, eventTextWidthPx() - EVENT_TEXT_WRAP_MARGIN);
    }

    private static FontMetrics fontMetricsFor(Font font) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.dispose();
        return fm;
    }

    private static List<String> wrapTextToLines(String text, Font font, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        FontMetrics fm = fontMetricsFor(font);
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (fm.stringWidth(word) > maxWidth) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
                appendWordBrokenByWidth(lines, word, fm, maxWidth);
                continue;
            }
            if (current.length() == 0) {
                current.append(word);
            } else {
                String candidate = current + " " + word;
                if (fm.stringWidth(candidate) <= maxWidth) {
                    current = new StringBuilder(candidate);
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static void appendWordBrokenByWidth(
            List<String> lines, String word, FontMetrics fm, int maxWidth) {
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            chunk.append(word.charAt(i));
            if (fm.stringWidth(chunk.toString()) > maxWidth) {
                if (chunk.length() > 1) {
                    lines.add(chunk.substring(0, chunk.length() - 1));
                    chunk = new StringBuilder(String.valueOf(word.charAt(i)));
                } else {
                    lines.add(chunk.toString());
                    chunk = new StringBuilder();
                }
            }
        }
        if (chunk.length() > 0) {
            lines.add(chunk.toString());
        }
    }

    private float eventFontSizeFor(String text) {
        int len = text == null ? 0 : text.length();
        if (len > 115) {
            return 15f;
        }
        if (len > 95) {
            return 17f;
        }
        if (len > 75) {
            return 19f;
        }
        if (len > 55) {
            return 21f;
        }
        if (len > 40) {
            return 23f;
        }
        return 25f;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void afterGameStep() {
        updateGameStatsBars();
        gamePreview.refreshImages();
        setEventChoicesEnabled(!gameModel.isGameOver());
        if (gameModel.isGameOver() && !gameOverDialogShown) {
            gameOverDialogShown = true;
            showGameOverInEventPanel();
        }
    }

    private void showGameOverInEventPanel() {
        String message = gameModel.getGameOverMessage();
        if (message.isEmpty()) {
            message = gameModel.getPetName() + " starved to death.";
        }
        setEventCardContent(eventCardHtmlStyled(message, GAME_OVER_TEXT, true));
        GameAudio.stopBgMusic();
        PetGameModel.GameOverReason reason = gameModel.getGameOverReason();
        if (reason == PetGameModel.GameOverReason.LEFT_HOME) {
            GameAudio.playRunaway(ASSETS);
        } else if (reason == PetGameModel.GameOverReason.OBESITY
                || reason == PetGameModel.GameOverReason.STARVATION) {
            GameAudio.playDeath(ASSETS);
        }
        showRestartUi();
    }

    private void showRestartUi() {
        lblYes.setVisible(false);
        lblNo.setVisible(false);
        lblRestart.setVisible(true);
    }

    private void hideRestartUi() {
        lblRestart.setVisible(false);
        lblYes.setVisible(true);
        lblNo.setVisible(true);
    }

    private void returnToCreation() {
        gameModel = null;
        eventDeck = null;
        currentEvent = null;
        gameOverDialogShown = false;
        hideRestartUi();
        setEventChoicesEnabled(false);
        cardLayout.show(cards, "creation");
        updateStatusLabels();
        creationPreview.refreshImages();
        GameAudio.stopSfx();
        GameAudio.startBgMusic(ASSETS);
    }

    private void setupRestartLink() {
        lblRestart.setFont(choiceFont(Font.BOLD, FONT_CHOICE));
        lblRestart.setForeground(COZY_TEXT);
        lblRestart.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblRestart.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                returnToCreation();
            }
        });
    }

    private void setupStartLink() {
        lblStart.setFont(choiceFont(Font.BOLD, FONT_CHOICE));
        lblStart.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblStart.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (startLinkActive) {
                    startGame();
                }
            }
        });
    }

    private void updateStartLinkState() {
        startLinkActive = !bodyIds.isEmpty() && !faceIds.isEmpty();
        lblStart.setForeground(startLinkActive ? COZY_TEXT : CHOICE_DISABLED);
        lblStart.setCursor(startLinkActive
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
    }

    private void setupChoiceLabel(JLabel label, Runnable onClick) {
        label.setFont(choiceFont(Font.PLAIN, FONT_CHOICE));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (eventChoicesActive) {
                    onClick.run();
                }
            }
        });
    }

    private void setEventChoicesEnabled(boolean enabled) {
        eventChoicesActive = enabled && gameModel != null && currentEvent != null;
        lblYes.setForeground(eventChoicesActive ? SOFT_YES : CHOICE_DISABLED);
        lblNo.setForeground(eventChoicesActive ? SOFT_NO : CHOICE_DISABLED);
        lblYes.setCursor(eventChoicesActive
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
        lblNo.setCursor(eventChoicesActive
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
    }

    private void updateGameStatsBars() {
        if (gameModel == null) {
            gameStatsBars.clear();
            return;
        }
        gameStatsBars.setStats(
                gameModel.getHunger(),
                gameModel.getAffection(),
                gameModel.getWeight(),
                gameModel.getHealth());
    }

    private void startGame() {
        Path hat = currentHatPath();
        Path leg = currentLegPath();
        int bodyId = bodyIds.get(bodyIndex);
        int faceId = faceIds.get(faceIndex);
        gameModel = new PetGameModel(PARTS, hat, leg, bodyId, faceId, readPetNameFromField());
        eventDeck = new EventCardDeck(eventCards);
        gameOverDialogShown = false;
        hideRestartUi();
        updateGameStatsBars();
        gamePreview.refreshImages();
        showNextEvent();
        setEventChoicesEnabled(true);
        cardLayout.show(cards, "game");
    }

    private void cycleHat(int delta) {
        if (hatPaths.isEmpty()) {
            return;
        }
        hatIndex = floorMod(hatIndex + delta, hatPaths.size());
        creationPreview.refreshImages();
        updateStatusLabels();
    }

    private void cycleLeg(int delta) {
        if (legPaths.isEmpty()) {
            return;
        }
        legIndex = floorMod(legIndex + delta, legPaths.size());
        creationPreview.refreshImages();
        updateStatusLabels();
    }

    private void cycleBody(int delta) {
        if (bodyIds.isEmpty()) {
            return;
        }
        bodyIndex = floorMod(bodyIndex + delta, bodyIds.size());
        creationPreview.refreshImages();
        updateStatusLabels();
    }

    private void cycleFace(int delta) {
        if (faceIds.isEmpty()) {
            return;
        }
        faceIndex = floorMod(faceIndex + delta, faceIds.size());
        creationPreview.refreshImages();
        updateStatusLabels();
    }

    private static int floorMod(int x, int m) {
        int r = x % m;
        return r < 0 ? r + m : r;
    }

    private void updateStatusLabels() {
        hatStatus.setText(formatIndex(hatPaths.size(), hatIndex));
        legStatus.setText(formatIndex(legPaths.size(), legIndex));
        bodyStatus.setText(formatIndex(bodyIds.size(), bodyIndex));
        faceStatus.setText(formatIndex(faceIds.size(), faceIndex));
    }

    private JLabel createArrowLink(String text, Runnable onClick) {
        JLabel link = new JLabel(text);
        link.setFont(arrowFont);
        link.setForeground(COZY_TEXT);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }
        });
        return link;
    }

    private static String formatIndex(int n, int idx) {
        if (n <= 0) {
            return "(none)";
        }
        return (idx + 1) + "/" + n;
    }

    private static List<Path> scanNumberedPng(Path dir, String prefix) throws IOException {
        //aldığı dir parametresi bir klasör değilse boş bir liste dönüyor.
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)\\.png$", Pattern.CASE_INSENSITIVE);
        List<Integer> ids = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                Matcher m = pattern.matcher(name);
                if (m.matches()) {
                    ids.add(Integer.parseInt(m.group(1)));
                }
            }
        }
        Collections.sort(ids);
        List<Path> out = new ArrayList<>();
        for (int id : ids) {
            out.add(dir.resolve(prefix + id + ".png"));
        }
        return out;
    }


    private static List<Integer> scanStyleIdsWithNormalBody(Path bodiesRoot) throws IOException {
        if (!Files.isDirectory(bodiesRoot)) {
            return Collections.emptyList();
        }
        List<Integer> ids = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(bodiesRoot)) {
            //stream (klasörü gezen DirectoryStream nesnesi) içindeki her Path'a child diye hitap ediyoruz :)
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (!name.matches("\\d+")) {
                    continue;
                }
                int id = Integer.parseInt(name);
                Path png = child.resolve("normal").resolve("body" + id + ".png");
                if (Files.isRegularFile(png)) {
                    ids.add(id);
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    private static List<Integer> scanStyleIdsWithNormalFace(Path facesRoot) throws IOException {
        if (!Files.isDirectory(facesRoot)) {
            return Collections.emptyList();
        }
        List<Integer> ids = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(facesRoot)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if (!name.matches("\\d+")) {
                    continue;
                }
                int id = Integer.parseInt(name);
                Path png = child.resolve("normal").resolve("face" + id + ".png");
                if (Files.isRegularFile(png)) {
                    ids.add(id);
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    private Path currentHatPath() {
        return hatPaths.isEmpty() ? null : hatPaths.get(hatIndex);
    }

    private Path currentLegPath() {
        return legPaths.isEmpty() ? null : legPaths.get(legIndex);
    }

    private Path currentBodyPathNormal() {
        if (bodyIds.isEmpty()) {
            return null;
        }
        int id = bodyIds.get(bodyIndex);
        return PARTS.resolve("bodies").resolve(Integer.toString(id)).resolve("normal").resolve("body" + id + ".png");
    }

    private Path currentFacePathNormal() {
        if (faceIds.isEmpty()) {
            return null;
        }
        int id = faceIds.get(faceIndex);
        return PARTS.resolve("faces").resolve(Integer.toString(id)).resolve("normal").resolve("face" + id + ".png");
    }

    private final class CreationPreviewPanel extends JPanel {

        private final BufferedImage background = loadPetWindowBackground();
        private BufferedImage composite;

        CreationPreviewPanel() {
            setOpaque(false);
            lockPetPanelSize(this);
        }

        void refreshImages() {
            composite = PetSpriteComposer.compose(
                    currentHatPath(), currentLegPath(), currentBodyPathNormal(), currentFacePathNormal());
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                paintPanelBackground(g2, this, background);
                paintScaledSprite(g2, this, composite);
            } finally {
                g2.dispose();
            }
        }
    }

    private final class GamePreviewPanel extends JPanel {

        private final BufferedImage background = loadPetWindowBackground();
        private BufferedImage composite;

        GamePreviewPanel() {
            setOpaque(false);
            lockPetPanelSize(this);
        }

        void refreshImages() {
            if (gameModel == null) {
                composite = new BufferedImage(
                        PetSpriteComposer.SPRITE_W, PetSpriteComposer.SPRITE_H, BufferedImage.TYPE_INT_ARGB);
            } else {
                composite = PetSpriteComposer.compose(
                        gameModel.getHatPath(),
                        gameModel.getLegPath(),
                        gameModel.resolveBodyPath(),
                        gameModel.resolveFacePath());
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                paintPanelBackground(g2, this, background);
                paintScaledSprite(g2, this, composite);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Hunger / affection / weight / health as soft fill-only bars. */
    private final class StatBarsPanel extends JPanel {

        private int hunger;
        private int affection;
        private int weight;
        private int health;

        StatBarsPanel() {
            setOpaque(false);
            int h = 4 * (STAT_BAR_HEIGHT + STAT_BAR_GAP) + 8;
            setPreferredSize(new Dimension(380, h));
        }

        void setStats(int hunger, int affection, int weight, int health) {
            this.hunger = hunger;
            this.affection = affection;
            this.weight = weight;
            this.health = health;
            repaint();
        }

        void clear() {
            setStats(0, 0, 0, 0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setFont(statsFont);
                int y = 4;
                y = drawBar(g2, "Hunger", hunger, BAR_HUNGER, BAR_HUNGER, y);
                y = drawBar(g2, "Affection", affection, BAR_AFFECTION, BAR_AFFECTION, y);
                y = drawBar(g2, "Weight", weight, BAR_WEIGHT, BAR_WEIGHT, y);
                drawBar(g2, "Health", health, BAR_HEALTH, BAR_HEALTH, y);
            } finally {
                g2.dispose();
            }
        }

        private int drawBar(Graphics2D g2, String name, int value, Color fill, Color labelColor, int y) {
            int labelW = 100;
            int barX = labelW + 10;
            int barW = Math.max(120, getWidth() - barX - 40);
            int arc = 10;

            g2.setColor(labelColor);
            g2.drawString(name, 0, y + STAT_BAR_HEIGHT - 7);

            int clamped = Math.max(0, Math.min(100, value));
            int fillW = Math.max(arc, (int) ((clamped / 100.0) * barW));
            if (clamped > 0) {
                g2.setColor(fill);
                g2.fillRoundRect(barX, y, fillW, STAT_BAR_HEIGHT, arc, arc);
            }

            g2.setColor(labelColor);
            g2.drawString(Integer.toString(clamped), barX + barW + 8, y + STAT_BAR_HEIGHT - 7);

            return y + STAT_BAR_HEIGHT + STAT_BAR_GAP;
        }
    }

    private static Dimension petPanelDisplaySize() {
        int height = Math.round(PET_PANEL_DISPLAY_WIDTH * (float) BG_NATIVE_HEIGHT / BG_NATIVE_WIDTH);
        return new Dimension(PET_PANEL_DISPLAY_WIDTH, height);
    }

    private static void lockPetPanelSize(JPanel preview) {
        Dimension size = petPanelDisplaySize();
        preview.setPreferredSize(size);
        preview.setMinimumSize(size);
        preview.setMaximumSize(size);
    }

    /** Centers the preview at bg aspect ratio; avoids BorderLayout stretching it. */
    private static JPanel wrapPetPanelInWindow(String title, JPanel preview) {
        JPanel window = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        window.setOpaque(false);
        window.setBorder(BorderFactory.createCompoundBorder(
                cozyTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 4, 6, 4)));
        lockPetPanelSize(preview);
        window.add(preview);
        Dimension size = petPanelDisplaySize();
        window.setMaximumSize(new Dimension(size.width + 16, size.height + 32));
        return window;
    }

    private static void paintPanelBackground(Graphics2D g2, JPanel panel, BufferedImage background) {
        int w = panel.getWidth();
        int h = panel.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        g2.setClip(0, 0, w, h);
        if (background != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(background, 0, 0, w, h, null);
        } else {
            g2.setColor(BUTTER_YELLOW);
            g2.fillRect(0, 0, w, h);
        }
        g2.setClip(null);
    }

    private static void paintScaledSprite(Graphics2D g2, JPanel panel, BufferedImage composite) {
        if (composite == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) {
            return;
        }
        int pad = 10;
        int availW = panel.getWidth() - pad * 2;
        int availH = panel.getHeight() - pad * 2;
        int scale = Math.min(availW / PetSpriteComposer.SPRITE_W, availH / PetSpriteComposer.SPRITE_H);
        scale = Math.max(1, Math.round(scale * SPRITE_SIZE_FACTOR));

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int sw = PetSpriteComposer.SPRITE_W * scale;
        int sh = PetSpriteComposer.SPRITE_H * scale;
        int x = (panel.getWidth() - sw) / 2;
        int yCenter = (panel.getHeight() - sh) / 2;
        int yDown = yCenter + Math.round(panel.getHeight() * SPRITE_DOWN_BIAS);
        int y = Math.min(yDown, panel.getHeight() - pad - sh);
        y = Math.max(pad, y);
        g2.drawImage(composite, x, y, sw, sh, null);
    }
}
