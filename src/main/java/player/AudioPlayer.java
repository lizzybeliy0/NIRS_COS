package player;

import javax.sound.sampled.*;
import java.io.IOException;

public class AudioPlayer {
    private SourceDataLine line;
    private volatile boolean playing = false;
    private Thread playbackThread;

    public void play(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        stop();

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        playing = true;
        playbackThread = new Thread(() -> {
            int offset = 0;
            int bufferSize = line.getBufferSize();

            while (playing && offset < audioData.length) {
                int chunkSize = Math.min(bufferSize, audioData.length - offset);
                line.write(audioData, offset, chunkSize);
                offset += chunkSize;
            }

            line.drain();
            line.stop();
            line.close();
            playing = false;
        });
        playbackThread.start();
    }

    public void stop() {
        playing = false;
        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
        }
    }

    public boolean isPlaying() {
        return playing;
    }
}