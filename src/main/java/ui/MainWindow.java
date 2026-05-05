package ui;

import player.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import javax.sound.sampled.LineUnavailableException;

public class MainWindow {
    private static final int[] BUFFER_SIZES = {256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
    private static final String[] SIZE_NAMES = {"256 байт", "512 байт", "1 КБ", "2 КБ", "4 КБ", "8 КБ", "16 КБ", "32 КБ"};

    // UI Components
    private ComboBox<String> bufferSizeCombo;
    private RadioButton doubleBufferRadio;
    private RadioButton ringBufferRadio;
    private RadioButton filteredRingBufferRadio;
    private ToggleGroup modeGroup;
    private Button loadButton;
    private Button playButton;
    private Button stopButton;
    private Label statusLabel;
    private Label fileLabel;
    private Label filterStatusLabel;

    // Filter controls
    private ComboBox<String> filterTypeCombo;
    private ComboBox<String> filterOrderCombo;
    private CheckBox filterEnabledCheck;
    private RadioButton firRadio;
    private RadioButton iirRadio;

    // Data
    private File currentFile;
    private byte[] currentAudioData;
    private javax.sound.sampled.AudioFormat currentFormat;

    // Players
    private DoubleBufferPlayer doubleBufferPlayer;
    private RingBufferPlayer ringBufferPlayer;
    private FilteredRingBufferPlayer filteredRingBufferPlayer;

    private volatile boolean isPlaying = false;
    private Thread playbackMonitor;

    public Scene getScene() {
        BorderPane root = new BorderPane();
        root.setCenter(createControlPanel());
        root.setBottom(createStatusBar());
        initPlayers();
        return new Scene(root, 950, 550);
    }

    private VBox createControlPanel() {
        VBox controlPanel = new VBox(15);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setAlignment(Pos.TOP_CENTER);
        controlPanel.setStyle("-fx-background-color: #f5f5f5;");

        // Заголовок
        Label titleLabel = new Label("Audio Buffer Tester");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Строка выбора файла
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        Label fileLabelText = new Label("WAV файл:");
        fileLabelText.setMinWidth(80);
        fileLabelText.setStyle("-fx-font-weight: bold;");
        fileLabel = new Label("Не выбран");
        fileLabel.setStyle("-fx-border-color: #999; -fx-border-radius: 3; -fx-padding: 5 10; -fx-background-color: white;");
        fileLabel.setMinWidth(300);
        loadButton = new Button("📂 Загрузить");
        loadButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        loadButton.setOnAction(e -> loadWavFile());
        fileRow.getChildren().addAll(fileLabelText, fileLabel, loadButton);

        // Строка выбора размера буфера
        HBox bufferRow = new HBox(10);
        bufferRow.setAlignment(Pos.CENTER_LEFT);
        Label bufferLabel = new Label("Размер буфера:");
        bufferLabel.setMinWidth(80);
        bufferLabel.setStyle("-fx-font-weight: bold;");
        bufferSizeCombo = new ComboBox<>(FXCollections.observableArrayList(SIZE_NAMES));
        bufferSizeCombo.getSelectionModel().selectFirst();
        bufferRow.getChildren().addAll(bufferLabel, bufferSizeCombo);

        // Строка выбора режима
        HBox modeRow = new HBox(10);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        Label modeLabel = new Label("Режим:");
        modeLabel.setMinWidth(80);
        modeLabel.setStyle("-fx-font-weight: bold;");

        modeGroup = new ToggleGroup();
        doubleBufferRadio = new RadioButton("Double Buffer");
        ringBufferRadio = new RadioButton("RingBuffer (без фильтра)");
        filteredRingBufferRadio = new RadioButton("RingBuffer + Фильтр");

        doubleBufferRadio.setToggleGroup(modeGroup);
        ringBufferRadio.setToggleGroup(modeGroup);
        filteredRingBufferRadio.setToggleGroup(modeGroup);
        doubleBufferRadio.setSelected(true);

        // ✅ ОБРАБОТЧИК - разблокирует панель фильтров при выборе
        filteredRingBufferRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean filterModeSelected = newVal;
            filterEnabledCheck.setDisable(!filterModeSelected);
            filterTypeCombo.setDisable(!filterModeSelected);
            filterOrderCombo.setDisable(!filterModeSelected);
        });

        modeRow.getChildren().addAll(modeLabel, doubleBufferRadio, ringBufferRadio, filteredRingBufferRadio);

        // Панель фильтров
        TitledPane filterPane = createFilterPanel();
        filterPane.setCollapsible(true);
        filterPane.setExpanded(false);

        // Строка кнопок управления
        HBox buttonRow = new HBox(15);
        buttonRow.setAlignment(Pos.CENTER);
        playButton = new Button("▶ Воспроизвести");
        stopButton = new Button("⏹ Остановить");
        playButton.setDisable(true);
        stopButton.setDisable(true);

        playButton.setStyle("-fx-font-size: 14px; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 8 20;");
        stopButton.setStyle("-fx-font-size: 14px; -fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 8 20;");

        playButton.setOnAction(e -> startPlayback());
        stopButton.setOnAction(e -> stopPlayback());

        buttonRow.getChildren().addAll(playButton, stopButton);

        controlPanel.getChildren().addAll(titleLabel, fileRow, bufferRow, modeRow, filterPane, buttonRow);

        return controlPanel;
    }

    private TitledPane createFilterPanel() {
        VBox filterContent = new VBox(10);
        filterContent.setPadding(new Insets(10));

        // Включение фильтра
        filterEnabledCheck = new CheckBox("Включить фильтр");
        filterEnabledCheck.setSelected(true);
        // НЕ ДИСЕЙБЛИМ ЗДЕСЬ - обработчик в createControlPanel() будет управлять

        // Тип фильтра
        HBox typeRow = new HBox(10);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label("Тип фильтра:");
        filterTypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "ФНЧ (Low-pass) - срез 317 Гц",
                "ФВЧ (High-pass) - срез 9827 Гц",
                "Полосовой (Band-pass) - полоса 2219-4755 Гц"
        ));
        filterTypeCombo.getSelectionModel().selectFirst();
        // НЕ ДИСЕЙБЛИМ ЗДЕСЬ
        typeRow.getChildren().addAll(typeLabel, filterTypeCombo);

        // Выбор FIR/IIR
        HBox algorithmRow = new HBox(10);
        algorithmRow.setAlignment(Pos.CENTER_LEFT);
        Label algorithmLabel = new Label("Тип фильтрации:");
        ToggleGroup algGroup = new ToggleGroup();
        firRadio = new RadioButton("КИХ (Rectangular window)");
        iirRadio = new RadioButton("БИХ (Butterworth)");
        firRadio.setToggleGroup(algGroup);
        iirRadio.setToggleGroup(algGroup);
        firRadio.setSelected(true);
        // ДЕЛАЕМ ТОЛЬКО ДЛЯ ЧТЕНИЯ - нельзя кликать
        firRadio.setDisable(true);
        iirRadio.setDisable(true);
        algorithmRow.getChildren().addAll(algorithmLabel, firRadio, iirRadio);

        // Порядок фильтра
        HBox orderRow = new HBox(10);
        orderRow.setAlignment(Pos.CENTER_LEFT);
        Label orderLabel = new Label("Порядок фильтра:");
        filterOrderCombo = new ComboBox<>(FXCollections.observableArrayList(
                "2 (IIR)", "4 (IIR)", "6 (IIR)",
                "10 (FIR)", "100 (FIR)", "200 (FIR)", "500 (FIR)", "750 (FIR)", "1000 (FIR)"
        ));
        filterOrderCombo.getSelectionModel().select(8);
        // НЕ ДИСЕЙБЛИМ ЗДЕСЬ

        // Автовыбор FIR/IIR при смене порядка
        filterOrderCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.contains("IIR")) {
                iirRadio.setSelected(true);
            } else {
                firRadio.setSelected(true);
            }
        });

        orderRow.getChildren().addAll(orderLabel, filterOrderCombo);

        Label infoLabel = new Label("📌 Частоты среза фиксированы по заданию");
        infoLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

        filterContent.getChildren().addAll(filterEnabledCheck, typeRow, algorithmRow, orderRow, infoLabel);

        TitledPane pane = new TitledPane("Настройки фильтра", filterContent);
        pane.setStyle("-fx-font-weight: bold;");
        return pane;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.setStyle("-fx-background-color: #34495e;");

        statusLabel = new Label("✅ Готов к работе");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        filterStatusLabel = new Label("");
        filterStatusLabel.setStyle("-fx-text-fill: #3498db; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer, filterStatusLabel);
        return statusBar;
    }

    private void initPlayers() {
        doubleBufferPlayer = new DoubleBufferPlayer(BUFFER_SIZES[0]);
        ringBufferPlayer = new RingBufferPlayer(BUFFER_SIZES[0]);
        filteredRingBufferPlayer = new FilteredRingBufferPlayer(BUFFER_SIZES[0]);
        System.out.println("[INIT] Плееры инициализированы");
    }

    private void loadWavFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите WAV файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV файлы", "*.wav"));

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            currentFile = file;
            try {
                System.out.println("[LOAD] Загрузка файла: " + file.getAbsolutePath());
                WavReader reader = new WavReader();
                reader.loadWav(currentFile);
                currentAudioData = reader.getAudioData();
                currentFormat = reader.getFormat();

                fileLabel.setText(currentFile.getName());
                playButton.setDisable(false);
                statusLabel.setText("📁 Файл загружен: " + currentFile.getName());

                System.out.println("[LOAD] Файл успешно загружен");
                System.out.println(String.format("  Формат: %.1f кГц, %d бит, %s",
                        currentFormat.getSampleRate() / 1000.0,
                        currentFormat.getSampleSizeInBits(),
                        currentFormat.getChannels() == 1 ? "моно" : "стерео"));
                System.out.println("  Размер данных: " + currentAudioData.length + " байт");

            } catch (Exception e) {
                System.err.println("[ERROR] Ошибка загрузки WAV: " + e.getMessage());
                showAlert("Ошибка", "Не удалось загрузить WAV файл:\n" + e.getMessage());
                statusLabel.setText("❌ Ошибка загрузки");
                e.printStackTrace();
            }
        }
    }

    private void startPlayback() {
        if (currentAudioData == null) {
            showAlert("Предупреждение", "Сначала загрузите WAV файл");
            return;
        }

        int bufferSizeIndex = bufferSizeCombo.getSelectionModel().getSelectedIndex();
        int bufferSize = BUFFER_SIZES[bufferSizeIndex];
        String sizeName = SIZE_NAMES[bufferSizeIndex];

        stopPlayback();

        try {
            if (doubleBufferRadio.isSelected()) {
                System.out.println("[PLAY] DoubleBuffer, буфер=" + sizeName);
                statusLabel.setText("🔄 DoubleBuffer... буфер=" + sizeName);
                filterStatusLabel.setText("");
                doubleBufferPlayer.setBufferSize(bufferSize);
                doubleBufferPlayer.play(currentAudioData, currentFormat);

            } else if (ringBufferRadio.isSelected()) {
                System.out.println("[PLAY] RingBuffer (без фильтра), буфер=" + sizeName);
                statusLabel.setText("⭕ RingBuffer... буфер=" + sizeName);
                filterStatusLabel.setText("");
                ringBufferPlayer.setBufferSize(bufferSize);
                ringBufferPlayer.play(currentAudioData, currentFormat);

            } else if (filteredRingBufferRadio.isSelected()) {
                System.out.println("[PLAY] RingBuffer + Фильтр, буфер=" + sizeName);

                if (filterEnabledCheck.isSelected()) {
                    setupFilter();
                } else {
                    filteredRingBufferPlayer.disableFilter();
                    filterStatusLabel.setText("🔘 Фильтр выключен");
                }

                filteredRingBufferPlayer.setBufferSize(bufferSize);
                filteredRingBufferPlayer.play(currentAudioData, currentFormat);

                if (filterEnabledCheck.isSelected()) {
                    String filterType = filterTypeCombo.getValue();
                    String orderStr = filterOrderCombo.getValue();
                    String firIir = orderStr.contains("IIR") ? "IIR" : "FIR";
                    filterStatusLabel.setText("🎛️ " + filterType + ", " + firIir + " порядок=" + orderStr.replaceAll("[^0-9]", ""));
                }
            }

            playButton.setDisable(true);
            stopButton.setDisable(false);
            isPlaying = true;
            System.out.println("[PLAY] Воспроизведение запущено");

            playbackMonitor = new Thread(() -> {
                while (isPlaying && isPlaybackActive()) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                }
                Platform.runLater(() -> {
                    if (!stopButton.isDisable()) {
                        stopPlayback();
                        System.out.println("[PLAY] Воспроизведение завершено");
                    }
                });
            });
            playbackMonitor.setDaemon(true);
            playbackMonitor.start();

        } catch (LineUnavailableException e) {
            System.err.println("[ERROR] Ошибка воспроизведения: " + e.getMessage());
            showAlert("Ошибка", "Не удалось воспроизвести:\n" + e.getMessage());
            statusLabel.setText("❌ Ошибка воспроизведения");
            playButton.setDisable(false);
        }
    }

    private void setupFilter() {
        String filterType = filterTypeCombo.getValue();
        String orderStr = filterOrderCombo.getValue();
        int order = Integer.parseInt(orderStr.replaceAll("[^0-9]", ""));
        boolean useFIR = orderStr.contains("FIR");

        if (filterType.startsWith("ФНЧ")) {
            filteredRingBufferPlayer.setLowPassFilter(order, useFIR);
        } else if (filterType.startsWith("ФВЧ")) {
            filteredRingBufferPlayer.setHighPassFilter(order, useFIR);
        } else if (filterType.startsWith("Полосовой")) {
            filteredRingBufferPlayer.setBandPassFilter(order, useFIR);
        }
        filteredRingBufferPlayer.setFilterEnabled(true);
    }

    private boolean isPlaybackActive() {
        return isPlaying;
    }

    private void stopPlayback() {
        System.out.println("[STOP] Остановка воспроизведения");
        isPlaying = false;

        if (doubleBufferPlayer != null) doubleBufferPlayer.stop();
        if (ringBufferPlayer != null) ringBufferPlayer.stop();
        if (filteredRingBufferPlayer != null) filteredRingBufferPlayer.stop();

        if (playbackMonitor != null) playbackMonitor.interrupt();

        playButton.setDisable(false);
        stopButton.setDisable(true);
        statusLabel.setText("⏹ Остановлено");
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public void shutdown() {
        System.out.println("[SHUTDOWN] Завершение работы приложения");
        stopPlayback();
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        System.out.println("[SHUTDOWN] Завершено");
    }
}