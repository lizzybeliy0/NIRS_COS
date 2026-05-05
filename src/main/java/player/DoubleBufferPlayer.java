package player;

import javax.sound.sampled.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DoubleBufferPlayer {
    private SourceDataLine line;
    private volatile boolean playing = false;
    private Thread producerThread;
    private Thread consumerThread;
    private int bufferSize;

    private BlockingQueue<byte[]> freeBuffers;
    private BlockingQueue<byte[]> filledBuffers;

    private byte[] bufferA;
    private byte[] bufferB;

    // Флаг принудительного маленького буфера
    private static final boolean FORCE_SMALL_HARDWARE_BUFFER = true;

    public DoubleBufferPlayer(int bufferSize) {
        this.bufferSize = bufferSize;

        bufferA = new byte[bufferSize];
        bufferB = new byte[bufferSize];

        freeBuffers = new ArrayBlockingQueue<>(2);
        filledBuffers = new ArrayBlockingQueue<>(2);

        freeBuffers.offer(bufferA);
        freeBuffers.offer(bufferB);
    }

    public void play(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        stop();

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);

        // Открываем с МИНИМАЛЬНЫМ буфером!
        int hardwareBufferSize;
        if (FORCE_SMALL_HARDWARE_BUFFER) {
            // Принудительно устанавливаем маленький аппаратный буфер
            // Это заставит звуковую карту "голодать"
            // todo было hardwareBufferSize = bufferSize * 2; // Минимально возможный
            hardwareBufferSize = bufferSize; // Минимально возможный
            System.out.println("[DoubleBuffer] ПРИНУДИТЕЛЬНО маленький аппаратный буфер: " + hardwareBufferSize + " байт");
        } else {
            hardwareBufferSize = AudioSystem.NOT_SPECIFIED;
        }

        line.open(format, hardwareBufferSize);
        System.out.println("[DoubleBuffer] РЕАЛЬНЫЙ размер буфера звуковой карты: " + line.getBufferSize() + " байт");
        System.out.println("[DoubleBuffer] Вы запросили: " + hardwareBufferSize + " байт");
        line.start();

        playing = true;

        producerThread = new Thread(() -> {
            int offset = 0;
            int totalData = audioData.length;

            System.out.println("[DoubleBuffer] Producer старт, буфер=" + bufferSize + " байт");
            long lastLog = System.currentTimeMillis();
            int emptyCount = 0;

            while (playing && offset < totalData) {
                try {
                    byte[] buffer = freeBuffers.take();

                    int bytesToCopy = Math.min(bufferSize, totalData - offset);
                    System.arraycopy(audioData, offset, buffer, 0, bytesToCopy);

                    if (bytesToCopy < bufferSize) {
                        for (int i = bytesToCopy; i < bufferSize; i++) {
                            buffer[i] = 0;
                        }
                    }

                    offset += bytesToCopy;
                    filledBuffers.put(buffer);

                    // Проверяем, не пустует ли буфер
                    if (freeBuffers.isEmpty()) {
                        emptyCount++;
                        if (System.currentTimeMillis() - lastLog > 1000) {
                            System.out.println("[DoubleBuffer] Producer голодает! Буферы заполнены #" + emptyCount);
                            lastLog = System.currentTimeMillis();
                        }
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }
            System.out.println("[DoubleBuffer] Producer завершен. offset=" + offset);
        });

        consumerThread = new Thread(() -> {
            System.out.println("[DoubleBuffer] Consumer старт");
            int totalBuffers = 0;
            long totalUnderruns = 0;

            while (playing || !filledBuffers.isEmpty()) {
                try {
                    long waitStart = System.nanoTime();
                    byte[] buffer = filledBuffers.take();
                    long waitTime = (System.nanoTime() - waitStart) / 1_000_000;

                    if (waitTime > 5 && bufferSize <= 4096) {
                        System.out.println("[DoubleBuffer] Consumer ждал " + waitTime + " мс (underrun!)");
                        totalUnderruns++;
                    }

                    // 🟢 КРИТИЧЕСКИЙ МОМЕНТ: Проверяем, сколько данных в очереди звуковой карты
                    if (bufferSize <= 4096) {
                        int available = line.available(); // Сколько места в буфере звуковой карты
                        if (available < bufferSize) {
                            System.out.println("[DoubleBuffer] Опасно мало места в звуковой карте: " + available + " байт");
                        }
                    }

                    long writeStart = System.nanoTime();
                    line.write(buffer, 0, bufferSize);
                    long writeTime = (System.nanoTime() - writeStart) / 1_000_000;

                    if (writeTime > 50 && bufferSize <= 4096) {
                        System.out.println("[DoubleBuffer] Медленная запись: " + writeTime + " мс");
                    }

                    freeBuffers.put(buffer);
                    totalBuffers++;

                } catch (InterruptedException e) {
                    break;
                }
            }

            System.out.println("[DoubleBuffer] Consumer завершен. Всего буферов: " + totalBuffers);
            if (totalUnderruns > 0) {
                System.out.println("[DoubleBuffer] Всего underrun: " + totalUnderruns);
            }

            line.drain();
            line.stop();
            line.close();
        });

        producerThread.start();
        consumerThread.start();
    }

    public void stop() {
        playing = false;

        if (producerThread != null) {
            producerThread.interrupt();
            try { producerThread.join(500); } catch (InterruptedException e) {}
        }

        if (consumerThread != null) {
            consumerThread.interrupt();
            try { consumerThread.join(500); } catch (InterruptedException e) {}
        }

        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
        }

        if (freeBuffers != null) {
            freeBuffers.clear();
            filledBuffers.clear();
            freeBuffers.offer(bufferA);
            freeBuffers.offer(bufferB);
        }
    }

    public void setBufferSize(int size) {
        if (this.bufferSize != size) {
            this.bufferSize = size;
            bufferA = new byte[size];
            bufferB = new byte[size];
            freeBuffers.clear();
            filledBuffers.clear();
            freeBuffers.offer(bufferA);
            freeBuffers.offer(bufferB);
        }
    }

    public int getBufferSize() {
        return bufferSize;
    }
}