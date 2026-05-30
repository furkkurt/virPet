package com.virpet;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** VirPet Android — open androidPort/ in Android Studio, Build APK. */
public class VirPetActivity extends Activity {

    private static final int BG_NATIVE_WIDTH = 2730;
    private static final int BG_NATIVE_HEIGHT = 1536;
    private static final float SPRITE_SIZE_FACTOR = 0.78f;
    private static final float SPRITE_DOWN_BIAS = 0.22f;

    private static final int BUTTER_YELLOW = 0xFFFFF4BE;
    private static final int COZY_TEXT = 0xFF5C483A;
    private static final int COZY_BORDER = 0xFFD2BCA0;
    private static final int COZY_FIELD_BG = 0xFFFFF6E8;
    private static final int BAR_HUNGER = 0xFFEBAF7D;
    private static final int BAR_AFFECTION = 0xFFEB9BAF;
    private static final int BAR_WEIGHT = 0xFFC3AA87;
    private static final int BAR_HEALTH = 0xFF9BCDA5;
    private static final int SOFT_NO = 0xFFC36464;
    private static final int SOFT_YES = 0xFF64A573;
    private static final int CHOICE_DISABLED = 0xFFB4AAA5;
    private static final int GAME_OVER_TEXT = 0xFFB23A3A;

    private static final float FONT_UI_SP = 16f;
    private static final float FONT_EVENT_SP = 20f;
    private static final float FONT_STATS_SP = 16f;
    private static final float FONT_ARROW_SP = 28f;
    private static final float FONT_CHOICE_SP = 22f;
    private static final int ARROW_GAP_DP = 40;
    private static final int EVENT_WRAP_MARGIN_DP = 24;
    /** Cap creation preview height so part rows stay on screen without scrolling. */
    private static final int CREATION_PREVIEW_MAX_HEIGHT_DP = 200;
    private static final int GAME_PREVIEW_MAX_HEIGHT_DP = 240;
    private static final String ASSET_BG = "bg.mp3";
    private static final String ASSET_TAP = "tap.mp3";
    private static final String ASSET_DEATH = "death.mp3";
    private static final String ASSET_RUNAWAY = "runaway.mp3";

    private AssetManager assets;
    private GameSounds sounds;
    private Typeface gameFont;
    private Bitmap windowBg;

    private List<String> hatPaths = new ArrayList<>();
    private List<String> legPaths = new ArrayList<>();
    private List<Integer> bodyIds = new ArrayList<>();
    private List<Integer> faceIds = new ArrayList<>();
    private List<EventCard> eventCards = new ArrayList<>();

    private int hatIndex, legIndex, bodyIndex, faceIndex;
    private PetGameModel gameModel;
    private EventCardDeck eventDeck;
    private EventCard currentEvent;
    private boolean gameOverShown;
    private boolean eventChoicesActive;
    private boolean startLinkActive;

    private LinearLayout creationScreen;
    private LinearLayout gameScreen;
    private PetPreviewView creationPreview;
    private PetPreviewView gamePreview;
    private StatBarsView statBars;
    private EditText petNameField;
    private TextView hatStatus, bodyStatus, faceStatus, legStatus;
    private TextView lblStart, lblRestart, eventText, lblYes, lblNo;
    private ScrollView gameEventScroll;
    private LinearLayout eventTextContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assets = getAssets();
        try {
            gameFont = loadGameFont(assets);
            windowBg = decodeBitmap("bg.png");
            hatPaths = scanNumberedPng("parts/hats", "hat");
            legPaths = scanNumberedPng("parts/legs", "legs");
            bodyIds = scanBodyIds();
            faceIds = scanFaceIds();
            eventCards = EventCardLoader.load(assets, "event_cards.cards");
        } catch (IOException e) {
            TextView err = new TextView(this);
            err.setText("Asset error: " + e.getMessage());
            err.setPadding(dp(24), dp(24), dp(24), dp(24));
            setContentView(err);
            return;
        }
        sounds = new GameSounds();
        sounds.load(assets);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BUTTER_YELLOW);
        creationScreen = buildCreationScreen();
        gameScreen = buildGameScreen();
        gameScreen.setVisibility(View.GONE);
        root.addView(creationScreen, matchParent());
        root.addView(gameScreen, matchParent());
        setContentView(root);
        updateStatusLabels();
        creationPreview.refreshSprite();
        updateStartLinkState();
        sounds.startBg();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && sounds != null) {
            sounds.playTap();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() {
        if (sounds != null) {
            sounds.release();
            sounds = null;
        }
        super.onDestroy();
    }

    // --- UI build ---

    private LinearLayout buildCreationScreen() {
        LinearLayout col = column();
        col.setPadding(0, dp(8), 0, dp(16));
        creationPreview = new PetPreviewView(false);
        col.addView(titled("Preview", creationPreview), wrapW());
        TextView nameTitle = text("Name", FONT_UI_SP + 2, true);
        nameTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ntp = wrapW();
        ntp.topMargin = dp(12);
        col.addView(nameTitle, ntp);
        petNameField = new EditText(this);
        petNameField.setTextColor(COZY_TEXT);
        petNameField.setHintTextColor(CHOICE_DISABLED);
        petNameField.setBackgroundColor(COZY_FIELD_BG);
        petNameField.setPadding(dp(12), dp(10), dp(12), dp(10));
        petNameField.setMinHeight(dp(44));
        petNameField.setFocusableInTouchMode(true);
        petNameField.setFocusable(true);
        petNameField.setTypeface(gameFont);
        petNameField.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_UI_SP);
        LinearLayout.LayoutParams flp = wrapW();
        flp.topMargin = dp(8);
        col.addView(petNameField, flp);
        col.addView(spacer(dp(20)), wrapW());
        LinearLayout parts = column();
        parts.addView(partRow("Hat", hatStatus = text("", FONT_UI_SP, false), this::cycleHat));
        parts.addView(spacer(dp(8)), wrapW());
        parts.addView(partRow("Body", bodyStatus = text("", FONT_UI_SP, false), this::cycleBody));
        parts.addView(spacer(dp(8)), wrapW());
        parts.addView(partRow("Face", faceStatus = text("", FONT_UI_SP, false), this::cycleFace));
        parts.addView(spacer(dp(8)), wrapW());
        parts.addView(partRow("Legs", legStatus = text("", FONT_UI_SP, false), this::cycleLeg));
        col.addView(parts, wrapW());
        col.addView(spacer(dp(12)), wrapW());
        lblStart = text("Start", FONT_CHOICE_SP, true);
        lblStart.setGravity(Gravity.CENTER);
        lblStart.setOnClickListener(v -> { if (startLinkActive) startGame(); });
        col.addView(lblStart, wrapW());
        return wrapInCenteredScroll(col);
    }

    private LinearLayout buildGameScreen() {
        LinearLayout col = column();
        col.setPadding(0, dp(8), 0, dp(16));
        statBars = new StatBarsView();
        col.addView(statBars, wrapW());
        col.addView(spacer(dp(8)), wrapW());
        gamePreview = new PetPreviewView(true);
        col.addView(titled("Your pet", gamePreview), wrapW());
        col.addView(spacer(dp(12)), wrapW());
        LinearLayout eventBox = column();
        eventBox.setPadding(dp(12), dp(14), dp(12), dp(12));
        eventBox.setBackground(borderDrawable());
        eventBox.addView(text("Event", FONT_UI_SP, false), wrapW());
        eventText = text("...", FONT_EVENT_SP, false);
        eventText.setGravity(Gravity.CENTER);
        eventText.setHorizontallyScrolling(false);
        int eventInnerW = eventWrapWidthPx();
        eventTextContainer = column();
        eventTextContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        eventTextContainer.addView(
                eventText,
                new LinearLayout.LayoutParams(eventInnerW, ViewGroup.LayoutParams.WRAP_CONTENT));
        gameEventScroll = new ScrollView(this);
        gameEventScroll.setFillViewport(true);
        gameEventScroll.addView(
                eventTextContainer,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams elp = wrapW();
        elp.height = dp(168);
        elp.topMargin = dp(6);
        eventBox.addView(gameEventScroll, elp);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER);
        lblNo = text("No", FONT_CHOICE_SP, false);
        lblYes = text("Yes", FONT_CHOICE_SP, false);
        lblRestart = text("Play again", FONT_CHOICE_SP, true);
        lblRestart.setVisibility(View.GONE);
        lblRestart.setTextColor(COZY_TEXT);
        lblNo.setTextColor(SOFT_NO);
        lblYes.setTextColor(SOFT_YES);
        lblNo.setGravity(Gravity.CENTER);
        lblYes.setGravity(Gravity.CENTER);
        lblRestart.setGravity(Gravity.CENTER);
        lblNo.setOnClickListener(v -> resolveEvent(false));
        lblYes.setOnClickListener(v -> resolveEvent(true));
        lblRestart.setOnClickListener(v -> returnToCreation());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        choices.addView(lblNo, half);
        choices.addView(lblYes, half);
        choices.addView(lblRestart, half);
        LinearLayout.LayoutParams chp = wrapW();
        chp.topMargin = dp(10);
        eventBox.addView(choices, chp);
        col.addView(eventBox, wrapW());
        setEventChoicesEnabled(false);
        return wrapInCenteredScroll(col);
    }

    private LinearLayout wrapInCenteredScroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout wrapper = column();
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setPadding(dp(16), dp(8), dp(16), dp(12));
        LinearLayout.LayoutParams contentLp =
                new LinearLayout.LayoutParams(contentWidthPx(), ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.gravity = Gravity.CENTER_HORIZONTAL;
        wrapper.addView(content, contentLp);
        scroll.addView(
                wrapper,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout outer = column();
        outer.addView(scroll, matchParent());
        return outer;
    }

    private interface DeltaCycle { void cycle(int delta); }

    private LinearLayout partRow(String title, TextView status, DeltaCycle cycle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(title, FONT_UI_SP, false), new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout arrows = new LinearLayout(this);
        arrows.setOrientation(LinearLayout.HORIZONTAL);
        arrows.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView bPrev = arrow("<");
        TextView bNext = arrow(">");
        bPrev.setOnClickListener(v -> cycle.cycle(-1));
        bNext.setOnClickListener(v -> cycle.cycle(1));
        arrows.addView(bPrev);
        arrows.addView(spacer(dp(ARROW_GAP_DP)), new LinearLayout.LayoutParams(dp(ARROW_GAP_DP), 1));
        arrows.addView(bNext);
        row.addView(arrows, ap);
        status.setGravity(Gravity.END);
        row.addView(status, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout titled(String title, View child) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(borderDrawable());
        box.setPadding(dp(6), dp(8), dp(6), dp(8));
        TextView t = text(title, FONT_UI_SP, false);
        t.setPadding(dp(4), 0, 0, dp(4));
        box.addView(t, wrapW());
        LinearLayout.LayoutParams childLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        childLp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(child, childLp);
        LinearLayout.LayoutParams boxLp = wrapW();
        boxLp.gravity = Gravity.CENTER_HORIZONTAL;
        box.setLayoutParams(boxLp);
        return box;
    }

    // --- Game flow ---

    private void startGame() {
        if (!startLinkActive || bodyIds.isEmpty() || faceIds.isEmpty() || eventCards.isEmpty()) {
            return;
        }
        hideKeyboard();
        hatIndex = clampIndex(hatIndex, hatPaths.size());
        legIndex = clampIndex(legIndex, legPaths.size());
        bodyIndex = clampIndex(bodyIndex, bodyIds.size());
        faceIndex = clampIndex(faceIndex, faceIds.size());

        String hat = hatPaths.isEmpty() ? null : hatPaths.get(hatIndex);
        String leg = legPaths.isEmpty() ? null : legPaths.get(legIndex);
        int bodyId = bodyIds.get(bodyIndex);
        int faceId = faceIds.get(faceIndex);
        gameModel = new PetGameModel(hat, leg, bodyId, faceId, readPetName());
        eventDeck = new EventCardDeck(eventCards);
        gameOverShown = false;
        hideRestartUi();
        creationScreen.setVisibility(View.GONE);
        gameScreen.setVisibility(View.VISIBLE);
        updateGameStats();
        gamePreview.refreshSprite();
        showNextEvent();
        setEventChoicesEnabled(true);
    }

    private void hideKeyboard() {
        if (petNameField == null) {
            return;
        }
        petNameField.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(petNameField.getWindowToken(), 0);
        }
    }

    private static int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        int r = index % size;
        return r < 0 ? r + size : r;
    }

    private void resolveEvent(boolean yes) {
        if (gameModel == null || gameModel.isGameOver() || currentEvent == null) return;
        StatDelta d = yes ? currentEvent.yes : currentEvent.no;
        gameModel.applyChoice(d);
        afterStep();
        if (!gameModel.isGameOver()) showNextEvent();
    }

    private void afterStep() {
        updateGameStats();
        gamePreview.refreshSprite();
        setEventChoicesEnabled(!gameModel.isGameOver());
        if (gameModel.isGameOver() && !gameOverShown) {
            gameOverShown = true;
            String msg = gameModel.getGameOverMessage();
            if (msg.isEmpty()) msg = gameModel.petName + " starved to death.";
            setEventText(msg, GAME_OVER_TEXT, true);
            if (sounds != null) {
                sounds.stopBg();
                if (gameModel.reason == PetGameModel.GameOverReason.LEFT_HOME) {
                    sounds.playRunaway();
                } else if (gameModel.reason == PetGameModel.GameOverReason.OBESITY
                        || gameModel.reason == PetGameModel.GameOverReason.STARVATION) {
                    sounds.playDeath();
                }
            }
            showRestartUi();
        }
    }

    private void showRestartUi() {
        if (lblYes != null) {
            lblYes.setVisibility(View.GONE);
        }
        if (lblNo != null) {
            lblNo.setVisibility(View.GONE);
        }
        if (lblRestart != null) {
            lblRestart.setVisibility(View.VISIBLE);
        }
    }

    private void hideRestartUi() {
        if (lblRestart != null) {
            lblRestart.setVisibility(View.GONE);
        }
        if (lblYes != null) {
            lblYes.setVisibility(View.VISIBLE);
        }
        if (lblNo != null) {
            lblNo.setVisibility(View.VISIBLE);
        }
    }

    private void returnToCreation() {
        gameModel = null;
        eventDeck = null;
        currentEvent = null;
        gameOverShown = false;
        hideRestartUi();
        setEventChoicesEnabled(false);
        gameScreen.setVisibility(View.GONE);
        creationScreen.setVisibility(View.VISIBLE);
        updateStatusLabels();
        creationPreview.refreshSprite();
        if (sounds != null) {
            sounds.stopGameOverSfx();
            sounds.startBg();
        }
    }

    private void showNextEvent() {
        if (eventDeck == null || eventCards.isEmpty()) {
            return;
        }
        currentEvent = eventDeck.drawNext();
        if (currentEvent == null || currentEvent.text == null) {
            return;
        }
        setEventText(personalize(currentEvent.text), COZY_TEXT, false);
    }

    private String personalize(String text) {
        if (text == null) return "";
        String name = gameModel != null ? gameModel.petName : readPetName();
        return text.replace("Your pet", name).replace("your pet", name);
    }

    private void setEventText(String raw, int color, boolean bold) {
        float sp = eventFontSp(raw);
        Paint paint = new Paint();
        setTypefacePaint(paint, bold);
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        int maxW = eventWrapWidthPx();
        List<String> lines = wrapLines(raw, paint, maxW);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        int wrapW = eventWrapWidthPx();
        eventText.setText(sb.toString());
        eventText.setTextColor(color);
        eventText.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        eventText.setTypeface(gameFont, bold ? Typeface.BOLD : Typeface.NORMAL);
        eventText.setMaxWidth(wrapW);
        if (eventTextContainer != null) {
            ViewGroup.LayoutParams lp = eventText.getLayoutParams();
            if (lp != null) {
                lp.width = wrapW;
                eventText.setLayoutParams(lp);
            }
            eventTextContainer.requestLayout();
        }
    }

    private float eventFontSp(String text) {
        int len = text == null ? 0 : text.length();
        if (len > 115) return 14f;
        if (len > 95) return 15f;
        if (len > 75) return 16f;
        if (len > 55) return 18f;
        if (len > 40) return 19f;
        return FONT_EVENT_SP;
    }

    private static List<String> wrapLines(String text, Paint paint, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.trim().split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            if (paint.measureText(word) > maxWidth) {
                if (cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(); }
                breakWord(lines, word, paint, maxWidth);
                continue;
            }
            if (cur.length() == 0) cur.append(word);
            else {
                String cand = cur + " " + word;
                if (paint.measureText(cand) <= maxWidth) cur = new StringBuilder(cand);
                else { lines.add(cur.toString()); cur = new StringBuilder(word); }
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private static void breakWord(List<String> lines, String word, Paint paint, int maxW) {
        StringBuilder ch = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            ch.append(word.charAt(i));
            if (paint.measureText(ch.toString()) > maxW) {
                if (ch.length() > 1) {
                    lines.add(ch.substring(0, ch.length() - 1));
                    ch = new StringBuilder(String.valueOf(word.charAt(i)));
                } else { lines.add(ch.toString()); ch = new StringBuilder(); }
            }
        }
        if (ch.length() > 0) lines.add(ch.toString());
    }

    private void setEventChoicesEnabled(boolean on) {
        eventChoicesActive = on && gameModel != null && currentEvent != null;
        if (lblYes != null) {
            lblYes.setTextColor(eventChoicesActive ? SOFT_YES : CHOICE_DISABLED);
            lblYes.setEnabled(eventChoicesActive);
        }
        if (lblNo != null) {
            lblNo.setTextColor(eventChoicesActive ? SOFT_NO : CHOICE_DISABLED);
            lblNo.setEnabled(eventChoicesActive);
        }
    }

    private void updateStartLinkState() {
        startLinkActive = !bodyIds.isEmpty() && !faceIds.isEmpty();
        lblStart.setTextColor(startLinkActive ? COZY_TEXT : CHOICE_DISABLED);
    }

    private void updateGameStats() {
        if (gameModel == null) statBars.clear();
        else statBars.set(gameModel.hunger, gameModel.affection, gameModel.weight, gameModel.health);
    }

    private void cycleHat(int d) { if (!hatPaths.isEmpty()) { hatIndex = floorMod(hatIndex + d, hatPaths.size()); creationPreview.refreshSprite(); updateStatusLabels(); } }
    private void cycleLeg(int d) { if (!legPaths.isEmpty()) { legIndex = floorMod(legIndex + d, legPaths.size()); creationPreview.refreshSprite(); updateStatusLabels(); } }
    private void cycleBody(int d) { if (!bodyIds.isEmpty()) { bodyIndex = floorMod(bodyIndex + d, bodyIds.size()); creationPreview.refreshSprite(); updateStatusLabels(); } }
    private void cycleFace(int d) { if (!faceIds.isEmpty()) { faceIndex = floorMod(faceIndex + d, faceIds.size()); creationPreview.refreshSprite(); updateStatusLabels(); } }

    private void updateStatusLabels() {
        hatStatus.setText(fmt(hatPaths.size(), hatIndex));
        legStatus.setText(fmt(legPaths.size(), legIndex));
        bodyStatus.setText(fmt(bodyIds.size(), bodyIndex));
        faceStatus.setText(fmt(faceIds.size(), faceIndex));
    }

    private String readPetName() {
        String n = petNameField.getText().toString().trim();
        return n.isEmpty() ? "Pet" : n;
    }

    private static int floorMod(int x, int m) { int r = x % m; return r < 0 ? r + m : r; }
    private static String fmt(int n, int i) { return n <= 0 ? "(none)" : (i + 1) + " / " + n; }

    // --- Asset scan ---

    private List<String> scanNumberedPng(String dir, String prefix) throws IOException {
        String[] names = assets.list(dir);
        if (names == null) return Collections.emptyList();
        Pattern p = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)\\.png$", Pattern.CASE_INSENSITIVE);
        List<Integer> ids = new ArrayList<>();
        for (String name : names) {
            Matcher m = p.matcher(name);
            if (m.matches()) ids.add(Integer.parseInt(m.group(1)));
        }
        Collections.sort(ids);
        List<String> out = new ArrayList<>();
        for (int id : ids) out.add(dir + "/" + prefix + id + ".png");
        return out;
    }

    private List<Integer> scanBodyIds() throws IOException {
        String[] dirs = assets.list("parts/bodies");
        if (dirs == null) return Collections.emptyList();
        List<Integer> ids = new ArrayList<>();
        for (String name : dirs) {
            if (!name.matches("\\d+")) continue;
            int id = Integer.parseInt(name);
            String path = "parts/bodies/" + id + "/normal/body" + id + ".png";
            try { assets.open(path).close(); ids.add(id); } catch (IOException ignored) {}
        }
        Collections.sort(ids);
        return ids;
    }

    private List<Integer> scanFaceIds() throws IOException {
        String[] dirs = assets.list("parts/faces");
        if (dirs == null) return Collections.emptyList();
        List<Integer> ids = new ArrayList<>();
        for (String name : dirs) {
            if (!name.matches("\\d+")) continue;
            int id = Integer.parseInt(name);
            String path = "parts/faces/" + id + "/normal/face" + id + ".png";
            try { assets.open(path).close(); ids.add(id); } catch (IOException ignored) {}
        }
        Collections.sort(ids);
        return ids;
    }

    private Bitmap decodeBitmap(String path) throws IOException {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;
        InputStream in = assets.open(path);
        try {
            return BitmapFactory.decodeStream(in, null, opts);
        } finally {
            in.close();
        }
    }

    private String currentHat() { return hatPaths.isEmpty() ? null : hatPaths.get(hatIndex); }
    private String currentLeg() { return legPaths.isEmpty() ? null : legPaths.get(legIndex); }
    private String currentBodyNormal() {
        if (bodyIds.isEmpty()) return null;
        int id = bodyIds.get(bodyIndex);
        return "parts/bodies/" + id + "/normal/body" + id + ".png";
    }
    private String currentFaceNormal() {
        if (faceIds.isEmpty()) return null;
        int id = faceIds.get(faceIndex);
        return "parts/faces/" + id + "/normal/face" + id + ".png";
    }

    private int contentWidthPx() {
        int screen = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(280), screen - dp(32));
    }

    private int panelWidthPx() {
        return contentWidthPx();
    }

    private int panelHeightPx() {
        return Math.round(panelWidthPx() * (float) BG_NATIVE_HEIGHT / BG_NATIVE_WIDTH);
    }

    private int creationPanelHeightPx() {
        return Math.min(panelHeightPx(), dp(CREATION_PREVIEW_MAX_HEIGHT_DP));
    }

    private int creationPanelWidthPx() {
        int h = creationPanelHeightPx();
        return Math.round(h * (float) BG_NATIVE_WIDTH / BG_NATIVE_HEIGHT);
    }

    private int gamePanelHeightPx() {
        return Math.min(panelHeightPx(), dp(GAME_PREVIEW_MAX_HEIGHT_DP));
    }

    private int gamePanelWidthPx() {
        int h = gamePanelHeightPx();
        return Math.round(h * (float) BG_NATIVE_WIDTH / BG_NATIVE_HEIGHT);
    }

    private int eventWrapWidthPx() {
        return Math.max(dp(200), contentWidthPx() - dp(EVENT_WRAP_MARGIN_DP) * 2);
    }

    // --- View helpers ---

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    private LinearLayout.LayoutParams wrapW() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(contentWidthPx(), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BUTTER_YELLOW);
        return l;
    }
    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));
        return v;
    }
    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(COZY_TEXT);
        t.setTypeface(gameFont, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }
    private TextView arrow(String s) {
        TextView t = text(s, FONT_ARROW_SP, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), 0, dp(8), 0);
        return t;
    }
    private void setTypefacePaint(Paint p, boolean bold) {
        p.setTypeface(bold ? Typeface.create(gameFont, Typeface.BOLD) : gameFont);
    }

    private static Typeface loadGameFont(AssetManager assets) throws IOException {
        String name = findFirstFontInAssets(assets);
        return name != null ? Typeface.createFromAsset(assets, name) : Typeface.DEFAULT;
    }

    private static String findFirstFontInAssets(AssetManager assets) throws IOException {
        String[] files = assets.list("");
        if (files == null) {
            return null;
        }
        String[] exts = {".ttf", ".TTF", ".otf", ".OTF"};
        for (String file : files) {
            for (String ext : exts) {
                if (file.endsWith(ext)) {
                    return file;
                }
            }
        }
        return null;
    }
    private android.graphics.drawable.GradientDrawable borderDrawable() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(BUTTER_YELLOW);
        g.setStroke(dp(1), COZY_BORDER);
        g.setCornerRadius(dp(4));
        return g;
    }

    // --- Custom views ---

    private class PetPreviewView extends View {
        private final boolean gameMode;
        private Bitmap sprite;

        PetPreviewView(boolean gameMode) {
            super(VirPetActivity.this);
            this.gameMode = gameMode;
            setBackgroundColor(BUTTER_YELLOW);
        }

        void refreshSprite() {
            Bitmap next;
            if (gameMode && gameModel != null) {
                next = PetSpriteComposer.compose(assets,
                        gameModel.hatPath, gameModel.legPath,
                        gameModel.resolveBodyPath(), gameModel.resolveFacePath());
            } else {
                next = PetSpriteComposer.compose(assets,
                        currentHat(), currentLeg(), currentBodyNormal(), currentFaceNormal());
            }
            if (sprite != null && sprite != next) {
                sprite.recycle();
            }
            sprite = next;
            invalidate();
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w;
            int h;
            if (gameMode) {
                h = gamePanelHeightPx();
                w = gamePanelWidthPx();
            } else {
                h = creationPanelHeightPx();
                w = creationPanelWidthPx();
            }
            setMeasuredDimension(w, h);
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (windowBg != null) {
                Rect dst = new Rect(0, 0, w, h);
                canvas.drawBitmap(windowBg, null, dst, null);
            } else {
                canvas.drawColor(BUTTER_YELLOW);
            }
            if (sprite == null) return;
            int pad = dp(10);
            int availW = w - pad * 2, availH = h - pad * 2;
            int scale = Math.min(availW / PetSpriteComposer.SPRITE_W, availH / PetSpriteComposer.SPRITE_H);
            scale = Math.max(1, Math.round(scale * SPRITE_SIZE_FACTOR));
            int sw = PetSpriteComposer.SPRITE_W * scale, sh = PetSpriteComposer.SPRITE_H * scale;
            int x = (w - sw) / 2;
            int yCenter = (h - sh) / 2;
            int y = Math.min(yCenter + Math.round(h * SPRITE_DOWN_BIAS), h - pad - sh);
            y = Math.max(pad, y);
            Rect dst = new Rect(x, y, x + sw, y + sh);
            canvas.drawBitmap(sprite, null, dst, null);
        }
    }

    private class StatBarsView extends View {
        private int hunger, affection, weight, health;
        StatBarsView() {
            super(VirPetActivity.this);
            setBackgroundColor(BUTTER_YELLOW);
        }
        void set(int h, int a, int w, int he) { hunger = h; affection = a; weight = w; health = he; invalidate(); }
        void clear() { set(0, 0, 0, 0); }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(panelWidthPx(), dp(4 * 28 + 8));
        }
        @Override protected void onDraw(Canvas c) {
            int viewW = getWidth();
            if (viewW <= 0) {
                return;
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            setTypefacePaint(p, false);
            p.setTextSize(FONT_STATS_SP * getResources().getDisplayMetrics().scaledDensity);
            int y = dp(4);
            y = bar(c, p, "Hunger", hunger, BAR_HUNGER, y, viewW);
            y = bar(c, p, "Affection", affection, BAR_AFFECTION, y, viewW);
            y = bar(c, p, "Weight", weight, BAR_WEIGHT, y, viewW);
            bar(c, p, "Health", health, BAR_HEALTH, y, viewW);
        }
        private int bar(Canvas c, Paint p, String name, int val, int color, int y, int viewW) {
            int labelW = dp(72);
            int barX = labelW + dp(8);
            int barW = Math.max(dp(16), viewW - barX - dp(40));
            int barH = dp(20);
            p.setColor(color);
            c.drawText(name, 0, y + barH - dp(6), p);
            int clamped = Math.max(0, Math.min(100, val));
            int fillW = Math.max(dp(4), (int) (barW * (clamped / 100.0f)));
            if (clamped > 0 && fillW > 0) {
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(barX, y, barX + fillW, y + barH, dp(8), dp(8), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawText(Integer.toString(clamped), barX + barW + dp(6), y + barH - dp(6), p);
            return y + barH + dp(8);
        }
    }

    // --- Nested model (ported from desktop) ---

    static final class StatDelta {
        final int hunger, affection, weight, health;
        StatDelta(int h, int a, int w, int he) { hunger = h; affection = a; weight = w; health = he; }
        static final StatDelta ZERO = new StatDelta(0, 0, 0, 0);
    }

    static final class EventCard {
        final String id, text;
        final StatDelta yes, no;
        EventCard(String id, String text, StatDelta yes, StatDelta no) {
            this.id = id; this.text = text; this.yes = yes; this.no = no;
        }
    }

    static final class EventCardDeck {
        private final List<EventCard> cards;
        private final Random random = new Random();
        private EventCard last;
        EventCardDeck(List<EventCard> cards) {
            if (cards == null || cards.isEmpty()) throw new IllegalArgumentException("empty");
            this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        }
        EventCard drawNext() {
            if (cards.size() == 1) return last = cards.get(0);
            EventCard next;
            do { next = cards.get(random.nextInt(cards.size())); } while (next == last);
            return last = next;
        }
    }

    static final class PetGameModel {
        static final int STAT_MIN = 0, STAT_MAX = 100;
        static final int WEIGHT_SKINNY = 35, WEIGHT_FAT = 65;
        static final int AFFECTION_SAD = 35, AFFECTION_HAPPY = 65;
        static final int WEIGHT_HEALTH_LOW = 25, WEIGHT_HEALTH_HIGH = 75;

        final String hatPath, legPath;
        final int bodyStyleId, faceStyleId;
        final String petName;
        int hunger, affection, weight, health;
        boolean gameOver;
        GameOverReason reason = GameOverReason.NONE;

        enum GameOverReason { NONE, OBESITY, STARVATION, LEFT_HOME }

        PetGameModel(String hat, String leg, int bodyId, int faceId, String name) {
            hatPath = hat; legPath = leg;
            bodyStyleId = bodyId; faceStyleId = faceId;
            String t = name == null ? "" : name.trim();
            petName = t.isEmpty() ? "Pet" : t;
            health = 100; affection = 70; hunger = 30; weight = 50;
        }

        String resolveBodyPath() {
            String base = "parts/bodies/" + bodyStyleId;
            if (weight < WEIGHT_SKINNY) return base + "/skinny/body" + bodyStyleId + "S.png";
            if (weight > WEIGHT_FAT) return base + "/fat/body" + bodyStyleId + "F.png";
            return base + "/normal/body" + bodyStyleId + ".png";
        }

        String resolveFacePath() {
            String base = "parts/faces/" + faceStyleId;
            if (affection < AFFECTION_SAD) return base + "/sad/face" + faceStyleId + "S.png";
            if (affection > AFFECTION_HAPPY) return base + "/happy/face" + faceStyleId + "H.png";
            return base + "/normal/face" + faceStyleId + ".png";
        }

        void applyChoice(StatDelta d) {
            if (gameOver || d == null) return;
            hunger = clamp(hunger + d.hunger);
            affection = clamp(affection + d.affection);
            weight = clamp(weight + d.weight);
            health = clamp(health + d.health);
            tick();
        }

        private void tick() {
            hunger = clamp(hunger + 3);
            weight = clamp(weight - 2);
            affection = clamp(affection - 1);
            if (weight <= WEIGHT_HEALTH_LOW || weight >= WEIGHT_HEALTH_HIGH) health = clamp(health - 2);
            evalLoss();
        }

        private void evalLoss() {
            if (gameOver) return;
            if (affection <= 0) { affection = 0; reason = GameOverReason.LEFT_HOME; gameOver = true; return; }
            if (health <= 0) {
                health = 0;
                if (weight >= WEIGHT_HEALTH_HIGH) reason = GameOverReason.OBESITY;
                else if (weight <= WEIGHT_HEALTH_LOW) reason = GameOverReason.STARVATION;
                else reason = weight >= 50 ? GameOverReason.OBESITY : GameOverReason.STARVATION;
                gameOver = true;
            }
        }

        boolean isGameOver() { return gameOver; }

        String getGameOverMessage() {
            switch (reason) {
                case OBESITY: return petName + " died of obesity.";
                case STARVATION: return petName + " starved to death.";
                case LEFT_HOME: return petName + " left the house.";
                default: return "";
            }
        }

        private static int clamp(int v) { return Math.max(STAT_MIN, Math.min(STAT_MAX, v)); }
    }

    static final class PetSpriteComposer {
        static final int SPRITE_W = 16, SPRITE_H = 48;
        static Bitmap compose(AssetManager am, String hat, String leg, String body, String face) {
            Bitmap out = Bitmap.createBitmap(SPRITE_W, SPRITE_H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            drawAt(c, am, leg, 0, 32);
            drawAt(c, am, body, 0, 16);
            drawAt(c, am, face, 0, 16);
            drawAt(c, am, hat, 0, 0);
            return out;
        }
        private static void drawAt(Canvas c, AssetManager am, String path, int x, int y) {
            if (path == null) return;
            try (InputStream in = am.open(path)) {
                Bitmap img = BitmapFactory.decodeStream(in);
                if (img != null) c.drawBitmap(img, x, y, null);
            } catch (IOException ignored) {}
        }
    }

    static final class EventCardLoader {
        private EventCardLoader() {}

        static List<EventCard> load(AssetManager am, String assetPath) throws IOException {
            List<EventCard> out = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(am.open(assetPath), StandardCharsets.UTF_8))) {
                String line;
                String cardId = null;
                StringBuilder text = null;
                StatDelta yes = StatDelta.ZERO;
                StatDelta no = StatDelta.ZERO;
                boolean inText = false;

                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    if (t.startsWith("card ")) {
                        cardId = t.substring(5).trim();
                        text = null;
                        yes = StatDelta.ZERO;
                        no = StatDelta.ZERO;
                        inText = false;
                    } else if ("text".equals(t)) {
                        inText = true;
                        text = new StringBuilder();
                    } else if ("end".equals(t)) {
                        out.add(new EventCard(cardId, text.toString(), yes, no));
                        cardId = null;
                        inText = false;
                    } else if (t.startsWith("yes")) {
                        inText = false;
                        yes = parseDelta(t.substring(3).trim());
                    } else if (t.startsWith("no")) {
                        inText = false;
                        no = parseDelta(t.substring(2).trim());
                    } else if (inText) {
                        if (text.length() > 0) {
                            text.append('\n');
                        }
                        text.append(line);
                    }
                }
            }
            return out;
        }

        private static StatDelta parseDelta(String rest) {
            int hunger = 0, affection = 0, weight = 0, health = 0;
            if (rest.isEmpty()) {
                return StatDelta.ZERO;
            }
            for (String token : rest.split("\\s+")) {
                int eq = token.indexOf('=');
                if (eq < 1) {
                    continue;
                }
                int v = Integer.parseInt(token.substring(eq + 1));
                switch (token.substring(0, eq)) {
                    case "hunger": hunger = v; break;
                    case "affection": affection = v; break;
                    case "weight": weight = v; break;
                    case "health": health = v; break;
                    default: break;
                }
            }
            return new StatDelta(hunger, affection, weight, health);
        }
    }

    /** Background music and one-shot SFX from assets. */
    private final class GameSounds {
        private MediaPlayer bgPlayer;
        private MediaPlayer tapPlayer;
        private MediaPlayer deathPlayer;
        private MediaPlayer runawayPlayer;

        void load(AssetManager am) {
            bgPlayer = openSound(am, ASSET_BG);
            tapPlayer = openSound(am, ASSET_TAP);
            deathPlayer = openSound(am, ASSET_DEATH);
            runawayPlayer = openSound(am, ASSET_RUNAWAY);
            if (bgPlayer != null) {
                bgPlayer.setLooping(true);
            }
        }

        void startBg() {
            if (bgPlayer == null) {
                return;
            }
            try {
                if (bgPlayer.isPlaying()) {
                    return;
                }
                bgPlayer.seekTo(0);
                bgPlayer.start();
            } catch (IllegalStateException ignored) {
                // player not ready
            }
        }

        void stopBg() {
            if (bgPlayer == null) {
                return;
            }
            try {
                if (bgPlayer.isPlaying()) {
                    bgPlayer.pause();
                }
                bgPlayer.seekTo(0);
            } catch (IllegalStateException ignored) {
                // ignore
            }
        }

        void playTap() {
            playOneShot(tapPlayer);
        }

        void playDeath() {
            playOneShot(deathPlayer);
        }

        void playRunaway() {
            playOneShot(runawayPlayer);
        }

        void stopGameOverSfx() {
            stopPlayback(deathPlayer);
            stopPlayback(runawayPlayer);
        }

        private void stopPlayback(MediaPlayer player) {
            if (player == null) {
                return;
            }
            try {
                if (player.isPlaying()) {
                    player.pause();
                }
                player.seekTo(0);
            } catch (IllegalStateException ignored) {
                // ignore
            }
        }

        private void playOneShot(MediaPlayer player) {
            if (player == null) {
                return;
            }
            try {
                if (player.isPlaying()) {
                    player.pause();
                }
                player.seekTo(0);
                player.start();
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // ignore
            }
        }

        void release() {
            closePlayer(bgPlayer);
            closePlayer(tapPlayer);
            closePlayer(deathPlayer);
            closePlayer(runawayPlayer);
            bgPlayer = null;
            tapPlayer = null;
            deathPlayer = null;
            runawayPlayer = null;
        }

        private MediaPlayer openSound(AssetManager am, String path) {
            android.content.res.AssetFileDescriptor afd = null;
            try {
                afd = am.openFd(path);
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                mp.prepare();
                return mp;
            } catch (Exception e) {
                return null;
            } finally {
                if (afd != null) {
                    try {
                        afd.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
        }

        private void closePlayer(MediaPlayer p) {
            if (p != null) {
                p.release();
            }
        }
    }
}
