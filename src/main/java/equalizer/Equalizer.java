package equalizer;

import filters.ButterworthIIR;
import filters.Filter;

import java.util.Arrays;

/**
 * 6-полосный эквалайзер
 * Полосы: 0-317, 317-951, 951-2219, 2219-4755, 4755-9827, 9827-19971 Гц
 */
public class Equalizer implements Filter {

    // 6 полосовых фильтров (БИХ)
    private final ButterworthIIR[] bands;

    // Коэффициенты усиления для каждой полосы (дБ: от -100 до 0)
    private double[] gains = new double[6];

    // Частотные диапазоны полос
    private static final double[][] BAND_RANGES = {
            {0, 317},       // Полоса 1: 0-317 Гц
            {317, 951},     // Полоса 2: 317-951 Гц
            {951, 2219},    // Полоса 3: 951-2219 Гц
            {2219, 4755},   // Полоса 4: 2219-4755 Гц
            {4755, 9827},   // Полоса 5: 4755-9827 Гц
            {9827, 19971}   // Полоса 6: 9827-19971 Гц
    };

    // Коэффициенты для каждой полосы (из MATLAB)
    private static final double[][][] BAND_COEFFS = {
            // Полоса 1 (0-317 Гц) - ФНЧ
            null, // ФНЧ обрабатывается отдельно
            // Полоса 2 (317-951 Гц) - полосовой
            {
                    {0.00008434219445279565, 0, -0.00025302658335838693, 0, 0.00025302658335838693, 0, -0.00008434219445279565},
                    {1.0, -5.8015923968917615, 14.043931530975909, -18.15670492251202, 13.222659700502504, -5.142961957108336, 0.8346682545152307}
            },
            // Полоса 3 (951-2219 Гц)
            {
                    {0.0006201191417057056, 0, -0.0018603574251171168, 0, 0.0018603574251171168, 0, -0.0006201191417057056},
                    {1.0, -5.518479852044019, 12.808381024306806, -16.002848942949413, 11.351570691141241, -4.334969220284759, 0.6964117317221159}
            },
            // Полоса 4 (2219-4755 Гц)
            {
                    {0.0042371174900582495, 0, -0.012711352470174749, 0, 0.012711352470174749, 0, -0.0042371174900582495},
                    {1.0, -4.71933389732127, 9.75618878692679, -11.261043630476365, 7.650721421751836, -2.9033396575545294, 0.4834983050462119}
            },
            // Полоса 5 (4755-9827 Гц)
            {
                    {0.025756762798832988, 0, -0.07727028839649897, 0, 0.07727028839649897, 0, -0.025756762798832988},
                    {1.0, -2.482980111800128, 3.68712096441109, -3.4186260006635485, 2.271711659213272, -0.9181875769329531, 0.22708620365172222}
            },
            // Полоса 6 (9827-19971 Гц)
            {
                    {0.108096007243511, 0, -0.324288021730533, 0, 0.324288021730533, 0, -0.108096007243511},
                    {1.0, 2.0374026279589414, 1.9766720334707677, 1.4142668794266597, 0.8791927528446892, 0.31251217122842376, 0.04629965317780651}
            }
    };

    // ФНЧ для полосы 1 (0-317 Гц)
    private ButterworthIIR lowpassFilter;

    // ФНЧ для выделения полос
    private ButterworthIIR highpassFilter;

    // Тип фильтрации ("IIR" или "FIR")
    private String filterType = "IIR";

    public Equalizer() {
        this.bands = new ButterworthIIR[6];
        initIIRFilters();
        // Инициализируем усиления нулями (0 дБ)
        Arrays.fill(gains, 0.0);
    }

    private void initIIRFilters() {
        // Полоса 1: ФНЧ 317 Гц (6 порядок)
        bands[0] = new ButterworthIIR(6, 317.0, "lowpass", 44100.0, 1.0);

        // Полосы 2-6: полосовые фильтры
        for (int i = 1; i <= 5; i++) {
            bands[i] = createBandpassFilter(i);
        }
    }

    private ButterworthIIR createBandpassFilter(int bandIndex) {
        double[][] coeffs = BAND_COEFFS[bandIndex];
        if (coeffs == null) return null;

        // Создаем фильтр с коэффициентами
        // В конструктор передаем уже готовые коэффициенты
        return new ButterworthIIR(coeffs[0], coeffs[1]);
    }

    /**
     * Установить усиление для полосы
     * @param band номер полосы (1-6)
     * @param gainDb усиление в дБ (от -100 до 0)
     */
    public void setGain(int band, double gainDb) {
        if (band < 1 || band > 6) return;
        gains[band - 1] = gainDb;
        System.out.println("[Equalizer] Полоса " + band + " (" + BAND_RANGES[band-1][0] + "-" + BAND_RANGES[band-1][1] + " Гц): " + gainDb + " дБ");
    }

    /**
     * Получить усиление для полосы
     */
    public double getGain(int band) {
        if (band < 1 || band > 6) return 0;
        return gains[band - 1];
    }

    /**
     * Установить тип фильтрации
     */
    public void setFilterType(String type) {
        this.filterType = type;
        System.out.println("[Equalizer] Тип фильтрации: " + type);
    }

    /**
     * Преобразование дБ в линейный коэффициент усиления
     */
    private double dbToLinear(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    @Override
    public double filter(double input) {
        if (filterType.equals("FIR")) {
            // FIR пока не реализован для эквалайзера, возвращаем исходный сигнал
            return input;
        }

        // Для IIR: пропускаем сигнал через все полосы с усилением
        double output = 0.0;

        // Проверяем, все ли фильтры инициализированы
        for (int i = 0; i < bands.length; i++) {
            if (bands[i] != null) {
                double bandOutput = bands[i].filter(input);
                double gainLinear = dbToLinear(gains[i]);
                output += bandOutput * gainLinear;
            }
        }

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
        for (ButterworthIIR band : bands) {
            if (band != null) band.reset();
        }
    }
}