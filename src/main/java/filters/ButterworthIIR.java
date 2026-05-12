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
        double[] tempB = null;
        double[] tempA = null;

        if (btype.equals("lowpass")) {
            if (order == 2) {
                tempB = new double[]{0.0004941046199407910, 0.0009882092398815819, 0.0004941046199407910};
                tempA = new double[]{1.0, -1.9361479664887258, 0.9381243849684888};
            } else if (order == 4) {
                tempB = new double[]{0.000000245327897303946, 0.000000981311589215784, 0.00000147196738382368, 0.000000981311589215784, 0.000000245327897303946};
                tempA = new double[]{1.0, -3.881983431648751, 5.652864415059423, -3.659543618275146, 0.8886665601108299};
            } else if (order == 6) {
                tempB = new double[]{0.000000000121665998172276, 0.000000000729995989033658, 0.000000001824989972584144, 0.000000002433319963445526, 0.000000001824989972584144, 0.000000000729995989033658, 0.000000000121665998172276};
                tempA = new double[]{1.0, -5.825499339969697, 14.142656809604885, -18.314802954671496, 13.343491582793842, -5.185704150776340, 0.8398580608057002};
            }
        } else if (btype.equals("highpass")) {
            if (order == 2) {
                tempB = new double[]{0.43364825283318914, 0, -0.43364825283318914};
                tempA = new double[]{1.0, 0.6622940419715801, 0.13270349433362175};
            } else if (order == 4) {
                tempB = new double[]{0.21965156181180262, 0, -0.43930312362360524, 0, 0.21965156181180262};
                tempA = new double[]{1.0, 1.3506657796940738, 0.8223499427178917, 0.4018267596903342, 0.18861320998834924};
            } else if (order == 6) {
                tempB = new double[]{0.108096007243511, 0, -0.324288021730533, 0, 0.324288021730533, 0, -0.108096007243511};
                tempA = new double[]{1.0, 2.0374026279589414, 1.9766720334707677, 1.4142668794266597, 0.8791927528446892, 0.31251217122842376, 0.04629965317780651};
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

        System.out.println("[IIR] " + btype + ", order=" + order + ", cutoff=" + cutoff + " Hz");
        System.out.println("[IIR] b length=" + b.length + ", a length=" + a.length);
    }

    // Конструктор для полосового фильтра
    public ButterworthIIR(int order, double[] cutoff, String btype, double fs, double rp) {
        double[] tempB = null;
        double[] tempA = null;

        if (btype.equals("bandpass")) {
            if (order == 2) {
                tempB = new double[]{0.1544418920664769, 0, -0.1544418920664769};
                tempA = new double[]{1.0, -1.5112654082467516, 0.6911162158670462};
            } else if (order == 4) {
                tempB = new double[]{0.0258280848680252, 0, -0.0516561697360504, 0, 0.0258280848680252};
                tempA = new double[]{1.0, -3.1248502739371475, 3.9698387549845995, -2.4100037222751975, 0.600040936298999};
            } else if (order == 6) {
                tempB = new double[]{0.0042371174900582495, 0, -0.012711352470174749, 0, 0.012711352470174749, 0, -0.0042371174900582495};
                tempA = new double[]{1.0, -4.71933389732127, 9.75618878692679, -11.261043630476365, 7.650721421751836, -2.9033396575545294, 0.4834983050462119};
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

        System.out.println("[IIR] bandpass, order=" + order + ", cutoff=" + cutoff[0] + "-" + cutoff[1] + " Hz");
        System.out.println("[IIR] b length=" + b.length + ", a length=" + a.length);
    }

    public ButterworthIIR(double[] b, double[] a) {
        this.b = b.clone();
        this.a = a.clone();
        this.xHistory = new double[b.length];
        this.yHistory = new double[b.length];
        System.out.println("[IIR] Создан фильтр с готовыми коэффициентами, порядок=" + (b.length-1));
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
        Arrays.fill(xHistory, 0.0);
        Arrays.fill(yHistory, 0.0);
        pos = 0;
    }
}