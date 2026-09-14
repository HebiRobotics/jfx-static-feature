package us.hebi.graalvm.jfx.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * Plays a generated wav through MediaPlayer, enough to exercise the GStreamer platform lookup,
 * the shared library loading and the jfxmedia callbacks into Java.
 *
 * @author Florian Enner
 * @since 14 Sep 2026
 */
public class MediaCheck {

    static void run() throws Exception {
        Path wav = writeSineWav(Files.createTempFile("media-check", ".wav"));
        MediaPlayer player = new MediaPlayer(new Media(wav.toUri().toString()));
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        player.setOnReady(ready::countDown);
        player.setOnEndOfMedia(ended::countDown);
        player.setOnError(() -> {
            player.getError().printStackTrace();
            System.exit(1);
        });
        player.play();

        if (!ready.await(10, TimeUnit.SECONDS)) {
            System.err.println("media check failed: player never became ready");
            System.exit(1);
        }
        System.out.println("ready, duration " + player.getMedia().getDuration());
        if (!ended.await(10, TimeUnit.SECONDS)) {
            System.err.println("media check failed: playback never finished");
            System.exit(1);
        }
        System.out.println("media check passed");
        System.exit(0);
    }

    // 500ms of a 440Hz sine as 16-bit mono PCM at 8kHz
    private static Path writeSineWav(Path file) throws IOException {
        int sampleRate = 8000;
        int numSamples = sampleRate / 2;
        ByteBuffer data = ByteBuffer.allocate(2 * numSamples).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numSamples; i++) {
            data.putShort((short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000));
        }

        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + data.capacity());
        header.put("WAVEfmt ".getBytes());
        header.putInt(16); // PCM header size
        header.putShort((short) 1); // PCM
        header.putShort((short) 1); // mono
        header.putInt(sampleRate);
        header.putInt(sampleRate * 2); // byte rate
        header.putShort((short) 2); // block align
        header.putShort((short) 16); // bits per sample
        header.put("data".getBytes());
        header.putInt(data.capacity());
        wav.write(header.array());
        wav.write(data.array());
        return Files.write(file, wav.toByteArray());
    }

}
