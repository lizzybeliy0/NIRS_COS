package player;

import javax.sound.sampled.*;

/**
 * Проигрыватель с использованием кольцевого буфера
 */
public class RingBufferPlayer {
    private SourceDataLine line;
    private RingBuffer ringBuffer;
    private volatile boolean playing = false;
    private Thread producerThread;
    private Thread consumerThread;
    private int bufferSize;

    // Минимальный аппаратный буфер для чистоты эксперимента
    private static final boolean FORCE_SMALL_HARDWARE_BUFFER = true;

    public RingBufferPlayer(int bufferSize) {
        this.bufferSize = bufferSize;
        this.ringBuffer = new RingBuffer(bufferSize);
    }

    public void play(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        stop();
        ringBuffer.clear();

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);

        // Используем минимальный аппаратный буфер для чистоты эксперимента
        int hardwareBufferSize;
        if (FORCE_SMALL_HARDWARE_BUFFER) {
            hardwareBufferSize = bufferSize * 2;
            System.out.println("[RingBuffer] Аппаратный буфер: " + hardwareBufferSize + " байт");
        } else {
            hardwareBufferSize = AudioSystem.NOT_SPECIFIED;
        }

        line.open(format, hardwareBufferSize);
        System.out.println("[RingBuffer] Реальный буфер звуковой карты: " + line.getBufferSize() + " байт");
        line.start();

        playing = true;
        final byte[] data = audioData;
        final int totalBytes = data.length;

        // Producer: читает из файла и пишет в кольцевой буфер
        producerThread = new Thread(() -> {
            int offset = 0;
            byte[] tempBuffer = new byte[bufferSize];
            int totalWritten = 0;
            long lastLog = System.currentTimeMillis();
            int starveCount = 0;

            System.out.println("[RingBuffer] Producer старт, буфер=" + bufferSize + " байт");

            while (playing && offset < totalBytes) {
                int chunkSize = Math.min(bufferSize, totalBytes - offset);
                System.arraycopy(data, offset, tempBuffer, 0, chunkSize);

                // Записываем в кольцевой буфер (блокирующая операция)
                int written = ringBuffer.write(tempBuffer, 0, chunkSize);
                offset += written;
                totalWritten += written;

                // Логируем проблемы с производительностью
                if (written < chunkSize) {
                    starveCount++;
                    if (System.currentTimeMillis() - lastLog > 1000) {
                        System.out.println("[RingBuffer] Producer не может записать данные! Голодание #" + starveCount);
                        lastLog = System.currentTimeMillis();
                    }
                }
            }

            System.out.println("[RingBuffer] Producer завершен. Записано байт: " + totalWritten);
        });

        // Consumer: читает из кольцевого буфера и отправляет в line
        consumerThread = new Thread(() -> {
            byte[] outputBuffer = new byte[bufferSize];
            int totalRead = 0;
            int underrunCount = 0;
            long lastLog = System.currentTimeMillis();

            System.out.println("[RingBuffer] Consumer старт");

            while (playing || ringBuffer.available() > 0) {
                try {
                    long waitStart = System.nanoTime();

                    // Читаем из кольцевого буфера с таймаутом
                    int bytesRead = ringBuffer.readBlocking(outputBuffer, 0, bufferSize, 10);

                    long waitTime = (System.nanoTime() - waitStart) / 1_000_000;

                    if (bytesRead > 0) {
                        // Логируем underrun (когда долго ждали данные)
                        if (waitTime > 5 && bufferSize <= 4096) {
                            underrunCount++;
                            if (System.currentTimeMillis() - lastLog > 1000) {
                                System.out.println("[RingBuffer] Underrun! Ждал " + waitTime + " мс (underrun #" + underrunCount + ")");
                                lastLog = System.currentTimeMillis();
                            }
                        }

                        // Проверяем состояние аппаратного буфера
                        if (bufferSize <= 4096) {
                            int available = line.available();
                            if (available < bufferSize / 2) {
                                System.out.println("[RingBuffer] Мало места в звуковой карте: " + available + " байт");
                            }
                        }

                        // Отправляем в звуковую карту
                        long writeStart = System.nanoTime();
                        line.write(outputBuffer, 0, bytesRead);
                        long writeTime = (System.nanoTime() - writeStart) / 1_000_000;

                        if (writeTime > 20 && bufferSize <= 4096) {
                            System.out.println("[RingBuffer] Медленная запись в line: " + writeTime + " мс");
                        }

                        totalRead += bytesRead;

                    } else if (!playing && ringBuffer.available() == 0) {
                        break;
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }

            System.out.println("[RingBuffer] Consumer завершен. Прочитано байт: " + totalRead);
            System.out.println("[RingBuffer] Всего underrun: " + underrunCount);

            line.drain();
            line.stop();
            line.close();
        });

        producerThread.start();
        consumerThread.start();
    }

    public void stop() {
        System.out.println("[RingBuffer] Остановка...");
        playing = false;

        if (producerThread != null) {
            producerThread.interrupt();
            try {
                producerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
        }
    }

    public void setBufferSize(int size) {
        this.bufferSize = size;
        this.ringBuffer = new RingBuffer(size);
    }

    public int getBufferSize() {
        return bufferSize;
    }
}