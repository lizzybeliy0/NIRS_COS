package player;

import javax.sound.sampled.*;
import filters.*;

/**
 * Проигрыватель с кольцевым буфером и фильтрацией
 */
public class FilteredRingBufferPlayer extends RingBufferPlayer {

    private Filter currentFilter;
    private boolean filterEnabled = true;

    private String filterName = "Нет";
    private int filterOrder = 0;
    private String filterType = "FIR";

    private static final double LOWPASS_CUTOFF = 317.0;
    private static final double[] BANDPASS_CUTOFF = {2219.0, 4755.0};
    private static final double HIGHPASS_CUTOFF = 9827.0;

    // Поля для управления потоками
    private Thread producerThread;
    private Thread consumerThread;
    private SourceDataLine currentLine;
    private volatile boolean isPlaying = false;
    private RingBuffer localRingBuffer;
    private volatile boolean isStopped = true;

    public FilteredRingBufferPlayer(int bufferSize) {
        super(bufferSize);
    }

    public void setLowPassFilter(int order, boolean useFIR) {
        if (useFIR) {
            currentFilter = new RectangularFIR(order, LOWPASS_CUTOFF, "lowpass", 44100.0);
            filterName = "ФНЧ (FIR, прямоуг. окно)";
            filterOrder = order;
            filterType = "FIR";
        } else {
            currentFilter = new ButterworthIIR(order, LOWPASS_CUTOFF, "lowpass", 44100.0, 1.0);
            filterName = "ФНЧ (IIR, Баттерворт)";
            filterOrder = order;
            filterType = "IIR";
        }
        filterEnabled = true;
        System.out.println("[Filter] Установлен фильтр: " + filterName + ", порядок=" + filterOrder);
    }

    public void setHighPassFilter(int order, boolean useFIR) {
        if (useFIR) {
            currentFilter = new RectangularFIR(order, HIGHPASS_CUTOFF, "highpass", 44100.0);
            filterName = "ФВЧ (FIR, прямоуг. окно)";
            filterOrder = order;
            filterType = "FIR";
        } else {
            currentFilter = new ButterworthIIR(order, HIGHPASS_CUTOFF, "highpass", 44100.0, 1.0);
            filterName = "ФВЧ (IIR, Баттерворт)";
            filterOrder = order;
            filterType = "IIR";
        }
        filterEnabled = true;
        System.out.println("[Filter] Установлен фильтр: " + filterName + ", порядок=" + filterOrder);
    }

    public void setBandPassFilter(int order, boolean useFIR) {
        if (useFIR) {
            currentFilter = new RectangularFIR(order, BANDPASS_CUTOFF, "bandpass", 44100.0);
            filterName = "Полосовой (FIR, прямоуг. окно)";
            filterOrder = order;
            filterType = "FIR";
        } else {
            currentFilter = new ButterworthIIR(order, BANDPASS_CUTOFF, "bandpass", 44100.0, 1.0);
            filterName = "Полосовой (IIR, Баттерворт)";
            filterOrder = order;
            filterType = "IIR";
        }
        filterEnabled = true;
        System.out.println("[Filter] Установлен фильтр: " + filterName + ", порядок=" + filterOrder);
    }

    public void setFilterEnabled(boolean enabled) {
        this.filterEnabled = enabled;
    }

    public void disableFilter() {
        this.filterEnabled = false;
        this.currentFilter = null;
        this.filterName = "Нет";
        this.filterOrder = 0;
    }

    public String getFilterName() { return filterName; }
    public int getFilterOrder() { return filterOrder; }
    public boolean isFilterEnabled() { return filterEnabled; }
    public String getFilterType() { return filterType; }

    @Override
    public void play(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        // Останавливаем предыдущее воспроизведение
        stop();

        isStopped = false;
        isPlaying = true;

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        currentLine = (SourceDataLine) AudioSystem.getLine(info);

        int hardwareBufferSize = getBufferSize() * 4;
        currentLine.open(format, hardwareBufferSize);
        System.out.println("[FilteredPlayer] Аппаратный буфер: " + currentLine.getBufferSize() + " байт");
        currentLine.start();

        localRingBuffer = new RingBuffer(getBufferSize());

        final byte[] data = audioData;
        final int totalBytes = data.length;

        // Producer поток
        producerThread = new Thread(() -> {
            int offset = 0;
            byte[] tempBuffer = new byte[getBufferSize()];

            System.out.println("[FilteredPlayer] Producer старт, буфер=" + getBufferSize() + " байт");

            while (isPlaying && offset < totalBytes && !isStopped) {
                int chunkSize = Math.min(getBufferSize(), totalBytes - offset);
                System.arraycopy(data, offset, tempBuffer, 0, chunkSize);

                int written = localRingBuffer.write(tempBuffer, 0, chunkSize);
                offset += written;

                // Небольшая задержка для предотвращения busy-wait
                if (written == 0 && isPlaying) {
                    try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                }
            }

            System.out.println("[FilteredPlayer] Producer завершен");
        });

        // Consumer поток с фильтрацией
        consumerThread = new Thread(() -> {
            byte[] outputBuffer = new byte[getBufferSize()];
            int totalRead = 0;

            System.out.println("[FilteredPlayer] Consumer старт с фильтром: " + filterName);

            while ((isPlaying || localRingBuffer.available() > 0) && !isStopped) {
                try {
                    int bytesRead = localRingBuffer.readBlocking(outputBuffer, 0, getBufferSize(), 10);

                    if (bytesRead > 0 && currentLine != null && currentLine.isOpen()) {
                        byte[] finalBuffer;

                        if (filterEnabled && currentFilter != null) {
                            finalBuffer = currentFilter.filter(outputBuffer);
                        } else {
                            finalBuffer = outputBuffer;
                        }

                        currentLine.write(finalBuffer, 0, bytesRead);
                        totalRead += bytesRead;
                    } else if (!isPlaying && localRingBuffer.available() == 0) {
                        break;
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }

            System.out.println("[FilteredPlayer] Consumer завершен. Прочитано байт: " + totalRead);

            if (currentLine != null) {
                try {
                    currentLine.drain();
                    currentLine.stop();
                    currentLine.close();
                } catch (Exception e) {}
            }
            currentLine = null;
        });

        producerThread.start();
        consumerThread.start();
    }

    @Override
    public void stop() {
        System.out.println("[FilteredPlayer] Остановка...");
        isPlaying = false;
        isStopped = true;

        // Останавливаем потоки
        if (producerThread != null) {
            producerThread.interrupt();
            try {
                producerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            producerThread = null;
        }

        if (consumerThread != null) {
            consumerThread.interrupt();
            try {
                consumerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consumerThread = null;
        }

        // Закрываем линию
        if (currentLine != null && currentLine.isOpen()) {
            try {
                currentLine.stop();
                currentLine.close();
            } catch (Exception e) {}
        }
        currentLine = null;

        // Очищаем буфер
        if (localRingBuffer != null) {
            localRingBuffer.clear();
            localRingBuffer = null;
        }

        // Сбрасываем фильтр
        if (currentFilter != null) {
            currentFilter.reset();
        }

        System.out.println("[FilteredPlayer] Остановка завершена");
    }

    @Override
    public void setBufferSize(int size) {
        super.setBufferSize(size);
    }
}