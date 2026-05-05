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
    private RadioButton directPlayRadio;
    private RadioButton doubleBufferRadio;
    private RadioButton ringBufferRadio;
    private ToggleGroup modeGroup;
    private Button loadButton;
    private Button playButton;
    private Button stopButton;
    private Label statusLabel;
    private Label fileLabel;

    // Data
    private File currentFile;
    private byte[] currentAudioData;
    private javax.sound.sampled.AudioFormat currentFormat;

    // Players
    private AudioPlayer directPlayer;
    private DoubleBufferPlayer doubleBufferPlayer;
    private RingBufferPlayer ringBufferPlayer;

    private volatile boolean isPlaying = false;
    private Thread playbackMonitor;

    public Scene getScene() {
        BorderPane root = new BorderPane();

        // Центральная панель с управлением
        root.setCenter(createControlPanel());

        // Нижняя панель - статус
        root.setBottom(createStatusBar());

        initPlayers();

        return new Scene(root, 800, 400);
    }

    private VBox createControlPanel() {
        VBox controlPanel = new VBox(15);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setAlignment(Pos.TOP_CENTER);
        controlPanel.setStyle("-fx-background-color: #f5f5f5;");

        // Заголовок
        Label titleLabel = new Label("Audio Buffer Tester");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        titleLabel.setAlignment(Pos.CENTER);

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
        bufferSizeCombo.setStyle("-fx-font-size: 12px;");
        bufferRow.getChildren().addAll(bufferLabel, bufferSizeCombo);

        // Строка выбора режима
        HBox modeRow = new HBox(10);
        modeRow.setAlignment(Pos.CENTER_LEFT);
        Label modeLabel = new Label("Режим:");
        modeLabel.setMinWidth(80);
        modeLabel.setStyle("-fx-font-weight: bold;");

        modeGroup = new ToggleGroup();
        directPlayRadio = new RadioButton("Прямое воспроизведение");
        doubleBufferRadio = new RadioButton("Double Buffer");
        ringBufferRadio = new RadioButton("RingBuffer");

        directPlayRadio.setToggleGroup(modeGroup);
        doubleBufferRadio.setToggleGroup(modeGroup);
        ringBufferRadio.setToggleGroup(modeGroup);
        directPlayRadio.setSelected(true);

        modeRow.getChildren().addAll(modeLabel, directPlayRadio, doubleBufferRadio, ringBufferRadio);

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

        // Добавляем все в главную панель
        controlPanel.getChildren().addAll(titleLabel, fileRow, bufferRow, modeRow, buttonRow);

        return controlPanel;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.setStyle("-fx-background-color: #34495e;");

        statusLabel = new Label("✅ Готов к работе");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer);

        return statusBar;
    }

    private void initPlayers() {
        directPlayer = new AudioPlayer();
        doubleBufferPlayer = new DoubleBufferPlayer(BUFFER_SIZES[0]);
        ringBufferPlayer = new RingBufferPlayer(BUFFER_SIZES[0]);

        System.out.println("[INIT] Плееры инициализированы");
    }

    private void loadWavFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите WAV файл");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("WAV файлы", "*.wav")
        );

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
            if (directPlayRadio.isSelected()) {
                System.out.println("[PLAY] Прямое воспроизведение, буфер=" + sizeName + " (" + bufferSize + " байт)");
                statusLabel.setText("🎵 Прямое воспроизведение... буфер=" + sizeName);
                directPlayer.play(currentAudioData, currentFormat);

            } else if (doubleBufferRadio.isSelected()) {
                System.out.println("[PLAY] DoubleBuffer воспроизведение, буфер=" + sizeName + " (" + bufferSize + " байт)");
                doubleBufferPlayer.setBufferSize(bufferSize);
                statusLabel.setText("🔄 DoubleBuffer... буфер=" + sizeName);
                doubleBufferPlayer.play(currentAudioData, currentFormat);

            } else if (ringBufferRadio.isSelected()) {
                System.out.println("[PLAY] RingBuffer воспроизведение, буфер=" + sizeName + " (" + bufferSize + " байт)");
                ringBufferPlayer.setBufferSize(bufferSize);
                statusLabel.setText("⭕ RingBuffer ... буфер=" + sizeName);
                ringBufferPlayer.play(currentAudioData, currentFormat);
            }

            playButton.setDisable(true);
            stopButton.setDisable(false);
            isPlaying = true;

            System.out.println("[PLAY] Воспроизведение запущено");

            // Мониторинг окончания воспроизведения
            playbackMonitor = new Thread(() -> {
                while (isPlaying && isPlaybackActive()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
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

    private boolean isPlaybackActive() {
        if (directPlayRadio.isSelected()) {
            return directPlayer.isPlaying();
        }
        return isPlaying;
    }

    private void stopPlayback() {
        System.out.println("[STOP] Остановка воспроизведения");
        isPlaying = false;

        directPlayer.stop();
        doubleBufferPlayer.stop();
        ringBufferPlayer.stop();

        if (playbackMonitor != null) {
            playbackMonitor.interrupt();
        }

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
    }
}