package equalizer;

import javax.sound.sampled.*;

public class EqualizerPlayer {
    private SourceDataLine line;
    private volatile boolean playing = false;
    private Thread playbackThread;
    private int bufferSize;
    private Equalizer equalizer;

    public EqualizerPlayer(int bufferSize) {
        this.bufferSize = bufferSize;
        this.equalizer = new Equalizer();
    }

    public void play(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        stop();

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format, bufferSize * 4);
        line.start();

        playing = true;

        playbackThread = new Thread(() -> {
            int offset = 0;
            int totalData = audioData.length;
            byte[] tempBuffer = new byte[bufferSize];

            while (playing && offset < totalData) {
                int bytesToCopy = Math.min(bufferSize, totalData - offset);
                System.arraycopy(audioData, offset, tempBuffer, 0, bytesToCopy);

                // Применяем эквалайзер
                byte[] filteredData = equalizer.filter(tempBuffer);

                line.write(filteredData, 0, bytesToCopy);
                offset += bytesToCopy;
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
            try { playbackThread.join(500); } catch (InterruptedException e) {}
        }
        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
        }
        equalizer.reset();
    }

    public void setBufferSize(int size) {
        this.bufferSize = size;
    }

    public Equalizer getEqualizer() {
        return equalizer;
    }
}