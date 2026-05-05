package player;
/**
 * Кольцевой буфер
 * Использует битовую маску для определения размера (размер должен быть степенью 2)
 */
public class RingBuffer {
    private final byte[] buffer;
    private final int capacity;
    private final int mask;
    private int writePos = 0;
    private int readPos = 0;
    private int available = 0;  // количество доступных для чтения байт

    public RingBuffer(int capacity) {
        // Округляем до ближайшей степени двойки
        this.capacity = nextPowerOfTwo(capacity);
        this.buffer = new byte[this.capacity];
        this.mask = this.capacity - 1;
    }

    private int nextPowerOfTwo(int n) {
        int highestOneBit = Integer.highestOneBit(n);
        if (n == highestOneBit) {
            return n;
        }
        return highestOneBit << 1;
    }

    /**
     * Запись данных в буфер
     * @param data массив байт для записи
     * @param offset смещение
     * @param length количество байт
     * @return количество реально записанных байт
     */
    public synchronized int write(byte[] data, int offset, int length) {
        int freeSpace = capacity - available;
        int bytesToWrite = Math.min(length, freeSpace);

        for (int i = 0; i < bytesToWrite; i++) {
            int index = (writePos + i) & mask;
            buffer[index] = data[offset + i];
        }

        writePos = (writePos + bytesToWrite) & mask;
        available += bytesToWrite;
        notify();  // уведомляем читающих потоков

        return bytesToWrite;
    }

    /**
     * Чтение данных из буфера
     * @param data массив для чтения
     * @param offset смещение
     * @param length количество байт для чтения
     * @return количество реально прочитанных байт
     */
    public synchronized int read(byte[] data, int offset, int length) {
        int bytesToRead = Math.min(length, available);

        for (int i = 0; i < bytesToRead; i++) {
            int index = (readPos + i) & mask;
            data[offset + i] = buffer[index];
        }

        readPos = (readPos + bytesToRead) & mask;
        available -= bytesToRead;
        notify();

        return bytesToRead;
    }

    /**
     * Чтение с ожиданием
     */
    public synchronized int readBlocking(byte[] data, int offset, int length, int timeoutMs)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();

        while (available < length &&
                (timeoutMs == 0 || System.currentTimeMillis() - startTime < timeoutMs)) {
            wait(timeoutMs == 0 ? 100 : Math.max(1, timeoutMs -
                    (int)(System.currentTimeMillis() - startTime)));
        }

        return read(data, offset, length);
    }

    public synchronized int available() {
        return available;
    }

    public synchronized int capacity() {
        return capacity;
    }

    public synchronized void clear() {
        writePos = 0;
        readPos = 0;
        available = 0;
    }
}