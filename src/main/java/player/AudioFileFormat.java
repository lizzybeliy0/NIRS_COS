package player;

public class AudioFileFormat {
    private final boolean bigEndian;
    private final boolean signed;
    private final int bits;
    private final int channels;
    private final double sampleRate;

    public AudioFileFormat() {
        this.bigEndian = false;      // WAV использует little-endian
        this.signed = true;           // PCM signed
        this.bits = 16;               // 16 бит на семпл
        this.channels = 2;            // Стерео
        this.sampleRate = 44100.0;    // 44.1 кГц
    }

    public boolean isBigEndian() { return bigEndian; }
    public boolean isSigned() { return signed; }
    public int getBits() { return bits; }
    public int getChannels() { return channels; }
    public double getSampleRate() { return sampleRate; }

    public int getBytesPerSample() { return bits / 8; }
    public int getFrameSize() { return getBytesPerSample() * channels; }
}