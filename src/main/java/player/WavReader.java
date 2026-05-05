package player;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class WavReader {
    private AudioInputStream audioStream;
    private AudioFormat format;
    private byte[] audioData;

    public void loadWav(File file) throws UnsupportedAudioFileException, IOException {
        audioStream = AudioSystem.getAudioInputStream(file);
        format = audioStream.getFormat();

        // Читаем весь файл
        audioData = audioStream.readAllBytes();
        audioStream.close();
    }

    public AudioFormat getFormat() {
        return format;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public int getDataLength() {
        return audioData != null ? audioData.length : 0;
    }
}