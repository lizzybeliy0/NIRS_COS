package player;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;

public class WavFileReader {

    private AudioFormat format;
    private short[] samples;
    private File currentFile;

    public short[] readWavFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("File not found: " + filePath);
        }

        currentFile = file;
        AudioInputStream ais = AudioSystem.getAudioInputStream(file);
        this.format = ais.getFormat();

        System.out.println("=== Information about the file ===");
        System.out.println("Path: " + filePath);
        System.out.println("Frequency: " + format.getSampleRate() + " Hz");
        System.out.println("Bit depth: " + format.getSampleSizeInBits() + " bit");
        System.out.println("Каналы: " + format.getChannels());

        byte[] buffer = ais.readAllBytes();
        ais.close();

        int numSamples = buffer.length / 2;
        samples = new short[numSamples];

        for (int i = 0; i < numSamples; i++) {
            int low = buffer[i * 2] & 0xFF;
            int high = buffer[i * 2 + 1] & 0xFF;
            samples[i] = (short) ((high << 8) | low);
        }

        System.out.println("Samples: " + numSamples);
        return samples;
    }

    public void writeWavFile(String filePath, short[] samples) throws IOException {
        byte[] buffer = new byte[samples.length * 2];

        for (int i = 0; i < samples.length; i++) {
            buffer[i * 2] = (byte) (samples[i] & 0xFF);
            buffer[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        AudioInputStream ais = new AudioInputStream(bais, format, samples.length);

        File outputFile = new File(filePath);
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
        ais.close();

        System.out.println("Saved: " + outputFile.getAbsolutePath());
    }

    public AudioFormat getFormat() { return format; }
    public short[] getSamples() { return samples; }
    public File getCurrentFile() { return currentFile; }
}