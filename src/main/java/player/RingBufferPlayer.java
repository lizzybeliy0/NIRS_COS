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

    public RingBufferPlayer(int bufferSize) {
        this.bufferSize = bufferSize;
        this.ringBuffer = new RingBuffer(bufferSize);
    }

    public void play(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        stop();
        ringBuffer.clear();

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        playing = true;
        final byte[] data = audioData;
        final int totalBytes = data.length;

        // Producer: читает из файла и пишет в кольцевой буфер
        producerThread = new Thread(() -> {
            int offset = 0;
            byte[] tempBuffer = new byte[bufferSize];

            while (playing && offset < totalBytes) {
                int chunkSize = Math.min(bufferSize, totalBytes - offset);
                System.arraycopy(data, offset, tempBuffer, 0, chunkSize);

                // Записываем в кольцевой буфер
                int written = ringBuffer.write(tempBuffer, 0, chunkSize);
                offset += written;

                // Если буфер полон, немного ждём
                if (written < chunkSize) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });

        // Consumer: читает из кольцевого буфера и отправляет в line
        consumerThread = new Thread(() -> {
            byte[] outputBuffer = new byte[bufferSize];

            while (playing || ringBuffer.available() > 0) {
                try {
                    int bytesRead = ringBuffer.readBlocking(outputBuffer, 0, bufferSize, 100);
                    if (bytesRead > 0) {
                        line.write(outputBuffer, 0, bytesRead);
                    } else if (!playing && ringBuffer.available() == 0) {
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
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