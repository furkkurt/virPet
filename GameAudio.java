import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Plays WAV from {@code assets/} using the JDK ({@link Clip}) — Windows and Linux, no external player. */
public final class GameAudio {

    private static Clip bgClip;
    private static final List<Clip> sfxClips = new ArrayList<>();

    private GameAudio() {}

    public static void startBgMusic(Path assetsDir) {
        stopBgMusic();
        Path file = assetsDir.resolve("bg.wav");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Clip clip = AudioSystem.getClip();
            try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
                clip.open(in);
            }
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
            bgClip = clip;
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
            // no audio
        }
    }

    public static void stopBgMusic() {
        if (bgClip != null) {
            bgClip.stop();
            bgClip.close();
            bgClip = null;
        }
    }

    public static void playTap(Path assetsDir) {
        playSfx(assetsDir.resolve("tap.wav"));
    }

    public static void playDeath(Path assetsDir) {
        playSfx(assetsDir.resolve("death.wav"));
    }

    public static void playRunaway(Path assetsDir) {
        playSfx(assetsDir.resolve("runaway.wav"));
    }

    private static void playSfx(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Clip clip = null;
        try {
            clip = AudioSystem.getClip();
            final Clip playing = clip;
            try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
                clip.open(in);
            }
            clip.addLineListener(new LineListener() {
                @Override
                public void update(LineEvent event) {
                    if (event.getType() == LineEvent.Type.STOP) {
                        synchronized (sfxClips) {
                            sfxClips.remove(playing);
                        }
                        playing.close();
                    }
                }
            });
            synchronized (sfxClips) {
                sfxClips.add(clip);
            }
            clip.start();
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
            if (clip != null) {
                clip.close();
            }
        }
    }

    /** Stops any in-progress tap / death / runaway sounds. */
    public static void stopSfx() {
        synchronized (sfxClips) {
            for (Clip clip : sfxClips) {
                clip.stop();
            }
            sfxClips.clear();
        }
    }

    public static void shutdown() {
        stopBgMusic();
        stopSfx();
    }
}
