package player;

import javax.sound.sampled.*;
import filters.*;

/**
 * Проигрыватель с кольцевым буфером и фильтрацией
 */
public class FilteredRingBufferPlayer extends RingBufferPlayer {

    private Filter currentFilter;
    private boolean filterEnabled = true;

    // Параметры фильтра
    private String filterName = "Нет";
    private int filterOrder = 0;
    private String filterType = "FIR"; // "FIR" или "IIR"

    // Предустановленные частоты для разных типов фильтров
    private static final double LOWPASS_CUTOFF = 317.0;      // ФНЧ: 0-317 Гц
    private static final double[] BANDPASS_CUTOFF = {2219.0, 4755.0};  // Полосовой: 2219-4755 Гц
    private static final double HIGHPASS_CUTOFF = 9827.0;    // ФВЧ: 9827-19971 Гц

    public FilteredRingBufferPlayer(int bufferSize) {
        super(bufferSize);
    }

    /**
     * Установка ФНЧ (Low-pass) с заданными параметрами
     * @param order порядок фильтра
     * @param useFIR true - использовать FIR, false - использовать IIR
     */
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
        System.out.println("[Filter] Установлен фильтр: " + filterName + ", порядок=" + filterOrder + ", срез=" + LOWPASS_CUTOFF + " Гц");
    }

    /**
     * Установка ФВЧ (High-pass) с заданными параметрами
     */
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
        System.out.println("[Filter] Установлен фильтр: " + filterName + ", порядок=" + filterOrder + ", срез=" + HIGHPASS_CUTOFF + " Гц");
    }

    /**
     * Установка полосового фильтра (Band-pass) с заданными параметрами
     */
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
        System.out.println("[Filter] Установлен фильтр: " + filterName + ", порядок=" + filterOrder +
                ", полоса=" + BANDPASS_CUTOFF[0] + "-" + BANDPASS_CUTOFF[1] + " Гц");
    }

    /**
     * Включить/выключить фильтр
     */
    public void setFilterEnabled(boolean enabled) {
        this.filterEnabled = enabled;
        System.out.println("[Filter] Фильтр " + (enabled ? "ВКЛЮЧЕН" : "ВЫКЛЮЧЕН"));
    }

    /**
     * Отключить фильтр
     */
    public void disableFilter() {
        this.filterEnabled = false;
        this.currentFilter = null;
        this.filterName = "Нет";
        this.filterOrder = 0;
        System.out.println("[Filter] Фильтр отключен");
    }

    public String getFilterName() {
        return filterName;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public boolean isFilterEnabled() {
        return filterEnabled;
    }

    public String getFilterType() {
        return filterType;
    }

    /**
     * Воспроизведение с фильтрацией
     */
    public void playFiltered(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        super.stop();

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);

        int hardwareBufferSize = getBufferSize() * 4;
        line.open(format, hardwareBufferSize);
        System.out.println("[FilteredPlayer] Аппаратный буфер: " + line.getBufferSize() + " байт");
        line.start();

        RingBuffer localRingBuffer = new RingBuffer(getBufferSize());

        final boolean[] playing = {true};

        // Producer поток
        Thread producerThread = new Thread(() -> {
            int offset = 0;
            int totalBytes = audioData.length;
            byte[] tempBuffer = new byte[getBufferSize()];

            System.out.println("[FilteredPlayer] Producer старт, буфер=" + getBufferSize() + " байт");

            while (playing[0] && offset < totalBytes) {
                int chunkSize = Math.min(getBufferSize(), totalBytes - offset);
                System.arraycopy(audioData, offset, tempBuffer, 0, chunkSize);

                int written = localRingBuffer.write(tempBuffer, 0, chunkSize);
                offset += written;
            }

            System.out.println("[FilteredPlayer] Producer завершен");
        });

        // Consumer поток с фильтрацией
        Thread consumerThread = new Thread(() -> {
            byte[] outputBuffer = new byte[getBufferSize()];
            int totalRead = 0;

            System.out.println("[FilteredPlayer] Consumer старт с фильтром: " + filterName);

            while (playing[0] || localRingBuffer.available() > 0) {
                try {
                    int bytesRead = localRingBuffer.readBlocking(outputBuffer, 0, getBufferSize(), 10);

                    if (bytesRead > 0) {
                        byte[] finalBuffer;

                        if (filterEnabled && currentFilter != null) {
                            finalBuffer = currentFilter.filter(outputBuffer);
                        } else {
                            finalBuffer = outputBuffer;
                        }

                        line.write(finalBuffer, 0, bytesRead);
                        totalRead += bytesRead;
                    } else if (!playing[0] && localRingBuffer.available() == 0) {
                        break;
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }

            System.out.println("[FilteredPlayer] Consumer завершен. Прочитано байт: " + totalRead);

            line.drain();
            line.stop();
            line.close();
        });

        producerThread.start();
        consumerThread.start();

        this.runningThreads = new Thread[] {producerThread, consumerThread};
        this.currentLine = line;
    }

    private Thread[] runningThreads;
    private SourceDataLine currentLine;

    @Override
    public void play(byte[] audioData, AudioFormat format) throws LineUnavailableException {
        playFiltered(audioData, format);
    }

    @Override
    public void stop() {
        if (runningThreads != null) {
            for (Thread t : runningThreads) {
                if (t != null) t.interrupt();
            }
        }
        if (currentLine != null && currentLine.isOpen()) {
            currentLine.stop();
            currentLine.close();
        }
        super.stop();
    }
}