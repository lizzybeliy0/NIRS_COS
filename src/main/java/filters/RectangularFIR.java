package filters;

import java.util.Arrays;

public class RectangularFIR implements Filter {
    private final double[] coefficients;
    private final double[] history;
    private int pos = 0;
    private final int numtaps;

    public RectangularFIR(int numtaps, double cutoff, String btype, double fs) {
        this.numtaps = numtaps;
        this.coefficients = designFIR(numtaps, cutoff, btype, fs);
        this.history = new double[numtaps];

        double sum = 0;
        for (double c : coefficients) sum += c;
        System.out.println("[FIR] " + btype + ", taps=" + numtaps + ", cutoff=" + cutoff + " Hz, sum=" + sum);

        // Проверка: для ФВЧ центральный коэффициент должен быть около 1
        int m = (numtaps - 1) / 2;
        System.out.println("[FIR] Центральный коэффициент: " + coefficients[m]);
    }

    public RectangularFIR(int numtaps, double[] cutoff, String btype, double fs) {
        this.numtaps = numtaps;
        this.coefficients = designFIRBandpass(numtaps, cutoff, btype, fs);
        this.history = new double[numtaps];
    }

    private double[] designFIR(int numtaps, double cutoff, String btype, double fs) {
        double[] coeff = new double[numtaps];
        int m = (numtaps - 1) / 2;

        if (btype.equals("lowpass")) {
            // ФНЧ
            double fc = cutoff / fs;
            for (int i = 0; i < numtaps; i++) {
                int n = i - m;
                if (n == 0) {
                    coeff[i] = 2.0 * fc;
                } else {
                    coeff[i] = Math.sin(2.0 * Math.PI * fc * n) / (Math.PI * n);
                }
            }
        }
        else if (btype.equals("highpass")) {
            // Используем формулу: h_hp[n] = h_lp[n] * (-1)^n, но с fc_lp = 0.5 - fc
            double fc = cutoff / fs;
            double fc_lp = 0.5 - fc;  // Частота для ФНЧ прототипа

            for (int i = 0; i < numtaps; i++) {
                int n = i - m;
                if (n == 0) {
                    coeff[i] = 2.0 * fc_lp;
                } else {
                    coeff[i] = Math.sin(2.0 * Math.PI * fc_lp * n) / (Math.PI * n);
                }
                // Применяем модуляцию (-1)^n для преобразования в ФВЧ
                coeff[i] = coeff[i] * ((n % 2 == 0) ? 1 : -1);
            }
        }

        // Применяем прямоугольное окно (уже применено, sinc уже окна)
        return coeff;
    }

    private double[] designFIRBandpass(int numtaps, double[] cutoff, String btype, double fs) {
        double[] coeff = new double[numtaps];
        int m = (numtaps - 1) / 2;
        double f1 = cutoff[0] / fs;
        double f2 = cutoff[1] / fs;

        for (int i = 0; i < numtaps; i++) {
            int n = i - m;
            if (n == 0) {
                coeff[i] = 2.0 * (f2 - f1);
            } else {
                coeff[i] = (Math.sin(2.0 * Math.PI * f2 * n) - Math.sin(2.0 * Math.PI * f1 * n)) / (Math.PI * n);
            }
        }

        return coeff;
    }

    @Override
    public double filter(double input) {
        history[pos] = input;
        double output = 0;
        int idx = pos;

        for (int i = 0; i < numtaps; i++) {
            output += coefficients[i] * history[idx];
            idx = (idx - 1 + numtaps) % numtaps;
        }

        pos = (pos + 1) % numtaps;
        return output;
    }

    @Override
    public byte[] filter(byte[] input) {
        int sampleCount = input.length / 2;
        double[] samples = new double[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            int idx = i * 2;
            int sample = ((input[idx + 1] & 0xFF) << 8) | (input[idx] & 0xFF);
            if (sample > 32767) sample -= 65536;
            samples[i] = sample / 32768.0;
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
        Arrays.fill(history, 0.0);
        pos = 0;
    }
}