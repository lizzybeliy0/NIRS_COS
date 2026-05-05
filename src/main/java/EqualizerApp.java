import ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class EqualizerApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainWindow mainWindow = new MainWindow();
        Scene scene = mainWindow.getScene();

        primaryStage.setTitle("EqualizerApp");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(400);
        primaryStage.setResizable(false);
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            mainWindow.shutdown();
        });
    }

    public static void main(String[] args) {
        System.out.println("[START] Запуск приложения Audio Buffer Tester");
        launch(args);
    }
}