package player;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.atomic.AtomicBoolean;

public class DoubleBuffer {

    private byte[][] buffers;      // два буфера
    private int currentWrite;      // индекс буфера для записи (0 или 1)
    private int currentRead;       // индекс буфера для воспроизведения
    private int bufferSize;        // размер буфера в байтах
    private int bytesWritten;      // сколько байт записано в текущий буфер
    private boolean isPlaying;
    private AtomicBoolean isRunning;
    private SourceDataLine line;
    private AudioFormat format;
    private Thread playThread;
    private byte[] sourceData;     // исходные данные из WAV
    private int sourcePosition;    // позиция чтения из исходных данных

    // Конструктор
    public DoubleBuffer(int bufferSize, AudioFormat format) {
        this.bufferSize = bufferSize;
        this.format = format;
        this.buffers = new byte[2][bufferSize];
        this.currentWrite = 0;
        this.currentRead = 1;
        this.bytesWritten = 0;
        this.isPlaying = false;
        this.isRunning = new AtomicBoolean(false);
        this.sourcePosition = 0;
    }

    // Загрузить данные из WAV
    public void loadData(short[] samples) {
        // Конвертируем short[] в byte[]
        this.sourceData = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            sourceData[i * 2] = (byte) (samples[i] & 0xFF);
            sourceData[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        this.sourcePosition = 0;
    }

    // Запуск воспроизведения
    public void play() {
        if (sourceData == null) {
            System.err.println("No data loaded");
            return;
        }

        stop();
        isPlaying = true;
        isRunning.set(true);
        sourcePosition = 0;
        currentWrite = 0;
        currentRead = 1;
        bytesWritten = 0;

        // Заполняем первый буфер данными
        bytesWritten = fillBuffer(currentWrite);

        // Запускаем поток воспроизведения
        playThread = new Thread(this::playInternal);
        playThread.start();
    }

    // Остановка
    public void stop() {
        isPlaying = false;
        isRunning.set(false);
        if (playThread != null) {
            playThread.interrupt();
        }
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    // Заполнение буфера данными из sourceData
    private int fillBuffer(int bufferIndex) {
        int remaining = sourceData.length - sourcePosition;
        int toCopy = Math.min(bufferSize, remaining);

        if (toCopy > 0) {
            System.arraycopy(sourceData, sourcePosition, buffers[bufferIndex], 0, toCopy);
            sourcePosition += toCopy;
        }

        // Если данные кончились, заполняем тишиной (нолями)
        if (toCopy < bufferSize) {
            for (int i = toCopy; i < bufferSize; i++) {
                buffers[bufferIndex][i] = 0;
            }
        }

        return toCopy;
    }

    // Внутренний метод воспроизведения
    private void playInternal() {
        try {
            System.out.println("=== DoubleBuffer STARTED with size: " + bufferSize + " bytes ===");
            line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();

            int bufferSwitchCount = 0;

            while (isPlaying && (sourcePosition < sourceData.length || bytesWritten > 0)) {

                int bytesToWrite = (currentRead == currentWrite) ? bytesWritten : bufferSize;

                if (bytesToWrite > 0) {
                    line.write(buffers[currentRead], 0, bytesToWrite);
                }

                int oldRead = currentRead;
                currentRead = currentWrite;
                currentWrite = oldRead;

                bytesWritten = fillBuffer(currentWrite);

                bufferSwitchCount++;
                if (bufferSwitchCount % 100 == 0) {
                    System.out.println("Buffer switches: " + bufferSwitchCount + ", position: " + sourcePosition);
                }

                Thread.sleep(1);
            }

            System.out.println("=== DoubleBuffer FINISHED, total switches: " + bufferSwitchCount + " ===");
            line.drain();

        } catch (Exception e) {
            System.err.println("Playback error: " + e.getMessage());
        } finally {
            if (line != null) {
                line.close();
            }
        }
    }
    public boolean isPlaying() {
        return isPlaying;
    }

    public int getBufferSize() {
        return bufferSize;
    }
}