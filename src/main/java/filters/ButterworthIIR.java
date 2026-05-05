package filters;

import java.util.Arrays;

public class ButterworthIIR implements Filter {
    private final double[] b;
    private final double[] a;
    private final double[] xHistory;
    private final double[] yHistory;
    private int pos = 0;

    // Конструктор для ФНЧ и ФВЧ
    public ButterworthIIR(int order, double cutoff, String btype, double fs, double rp) {
        double Wc = 2 * Math.PI * cutoff / fs;

        double[] tempB = null;
        double[] tempA = null;

        if (btype.equals("lowpass")) {
            if (order == 2) {
                tempB = new double[]{0.0000515, 0.0001030, 0.0000515};
                tempA = new double[]{1.0, -1.9745, 0.9757};
            } else if (order == 4) {
                tempB = new double[]{0.0000022, 0.0000088, 0.0000132, 0.0000088, 0.0000022};
                tempA = new double[]{1.0, -3.8975, 5.6998, -3.7089, 0.9066};
            } else if (order == 6) {
                tempB = new double[]{0.0000000089, 0.0000000534, 0.0000001335, 0.0000001780, 0.0000001335, 0.0000000534, 0.0000000089};
                tempA = new double[]{1.0, -5.4758, 12.7649, -15.9679, 11.2316, -4.2017, 0.6540};
            }
        } else if (btype.equals("highpass")) {
            if (order == 2) {
                tempB = new double[]{0.9759, -1.9518, 0.9759};
                tempA = new double[]{1.0, -1.9745, 0.9757};
            } else if (order == 4) {
                tempB = new double[]{0.9066, -3.6264, 5.4396, -3.6264, 0.9066};
                tempA = new double[]{1.0, -3.8975, 5.6998, -3.7089, 0.9066};
            } else if (order == 6) {
                tempB = new double[]{0.6540, -3.9240, 9.8100, -13.0800, 9.8100, -3.9240, 0.6540};
                tempA = new double[]{1.0, -5.4758, 12.7649, -15.9679, 11.2316, -4.2017, 0.6540};
            }
        }

        if (tempB == null) {
            tempB = new double[]{1.0};
            tempA = new double[]{1.0};
        }

        this.b = tempB;
        this.a = tempA;
        this.xHistory = new double[b.length];
        this.yHistory = new double[b.length];
    }

    // Конструктор для полосового фильтра (order 2, 4, 6)
    public ButterworthIIR(int order, double[] cutoff, String btype, double fs, double rp) {
        double[] tempB = null;
        double[] tempA = null;

        if (btype.equals("bandpass")) {
            if (order == 2) {
                // Полосовой 2-го порядка (прототип 1-го порядка)
                tempB = new double[]{0.00023, 0, -0.00046, 0, 0.00023};
                tempA = new double[]{1.0, -3.8637, 5.7033, -3.8230, 0.9826};
            } else if (order == 4) {
                // Полосовой 4-го порядка (прототип 2-го порядка)
                tempB = new double[]{0.0000021, 0, -0.0000084, 0, 0.0000126, 0, -0.0000084, 0, 0.0000021};
                tempA = new double[]{1.0, -7.3287, 24.2322, -46.8243, 57.9599, -47.2071, 25.0732, -8.0207, 1.2146};
            } else if (order == 6) {
                // Полосовой 6-го порядка (прототип 3-го порядка)
                tempB = new double[]{0.00000091, 0, -0.00000546, 0, 0.00001365, 0, -0.00001820, 0, 0.00001365, 0, -0.00000546, 0, 0.00000091};
                tempA = new double[]{1.0, -9.5835, 44.0754, -125.9823, 249.4666, -359.9698, 388.3266, -316.1203, 193.4572, -87.4751, 28.1631, -5.9737, 0.6521};
            }
        }

        if (tempB == null) {
            tempB = new double[]{1.0};
            tempA = new double[]{1.0};
        }

        this.b = tempB;
        this.a = tempA;
        this.xHistory = new double[b.length];
        this.yHistory = new double[b.length];
    }

    @Override
    public double filter(double input) {
        int m = b.length;
        double output = b[0] * input;

        for (int i = 1; i < m; i++) {
            int idx = (pos - i + m) % m;
            output += b[i] * xHistory[idx] - a[i] * yHistory[idx];
        }

        xHistory[pos] = input;
        yHistory[pos] = output;
        pos = (pos + 1) % m;

        return output;
    }

    @Override
    public byte[] filter(byte[] input) {
        int sampleCount = input.length / 2;
        double[] samples = new double[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int idx = i * 2;
            samples[i] = ((input[idx + 1] & 0xFF) << 8) | (input[idx] & 0xFF);
            if (samples[i] > 32767) samples[i] -= 65536;
            samples[i] = samples[i] / 32768.0;
        }

        double[] filtered = new double[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            filtered[i] = filter(samples[i]);
        }

        byte[] output = new byte[input.length];
        for (int i = 0; i < sampleCount; i++) {
            short val = (short) Math.max(-32768, Math.min(32767, Math.round(filtered[i] * 32768)));
            int idx = i * 2;
            output[idx] = (byte) (val & 0xFF);
            output[idx + 1] = (byte) ((val >> 8) & 0xFF);
        }

        return output;
    }

    @Override
    public void reset() {
        Arrays.fill(xHistory, 0.0);
        Arrays.fill(yHistory, 0.0);
        pos = 0;
    }
}