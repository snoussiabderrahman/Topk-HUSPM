package ui;

import algorithms.Algorithm;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import model.Sequence;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Small JavaFX application to drive the benchmark.
 * Add JavaFX VM args when running (see instructions).
 */
public class BenchmarkApp extends Application {

    private final Map<String, Class<? extends Algorithm>> availableAlgorithms = AlgorithmFactory.getAvailableAlgorithms();

    private final ObservableList<BenchmarkResult> results = FXCollections.observableArrayList();

    private final File dataFolder = new File("data");
    private File outputFolder = new File("output");

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Benchmark UI - Top-K HUSPM");

        // Left: controls
        VBox left = new VBox(8);
        left.setPadding(new Insets(10));

        // Algorithms (checkboxes)
        TitledPane algPane = new TitledPane();
        algPane.setText("Algorithms");
        VBox algBox = new VBox(4);
        Map<String, CheckBox> algCheckBoxes = new LinkedHashMap<>();
        for (String name : availableAlgorithms.keySet()) {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(true); // default selected
            algBox.getChildren().add(cb);
            algCheckBoxes.put(name, cb);
        }
        algPane.setContent(new ScrollPane(algBox));
        algPane.setExpanded(true);

        // Datasets (scan folder)
        TitledPane dsPane = new TitledPane();
        dsPane.setText("Datasets (folder: " + dataFolder.getPath() + ")");
        VBox dsBox = new VBox(4);
        VBox dsSection = getVBox(dsBox, dsPane);
        dsPane.setContent(dsSection);
        dsPane.setExpanded(true);

        // K-values input
        Label kLabel = new Label("K values (e.g. 10,20 or 5-50:5):");
        TextField kField = new TextField("40"); // default sample
        TextField rhoField = new TextField("0.2");
        // Algorithm options
        Label optsLabel = new Label("Algorithm options:");
        SpinnerValueFactory.IntegerSpinnerValueFactory sampleFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000000, 2000, 100);
        Spinner<Integer> sampleSizeSpinner = new Spinner<>(sampleFactory);
        makeSpinnerEditable(sampleSizeSpinner);

        SpinnerValueFactory.IntegerSpinnerValueFactory maxIterFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10000, 100, 10);
        Spinner<Integer> maxIterSpinner = new Spinner<>(maxIterFactory);
        makeSpinnerEditable(maxIterSpinner);

        SpinnerValueFactory.IntegerSpinnerValueFactory maxSeqLenFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 10, 1);
        Spinner<Integer> maxSeqLenSpinner = new Spinner<>(maxSeqLenFactory);
        makeSpinnerEditable(maxSeqLenSpinner);


        GridPane optsGrid = new GridPane();
        optsGrid.setHgap(6);
        optsGrid.setVgap(6);
        optsGrid.add(new Label("Sample N:"), 0, 0);
        optsGrid.add(sampleSizeSpinner, 1, 0);
        optsGrid.add(new Label("rho:"), 0, 1);
        optsGrid.add(rhoField, 1, 1);
        optsGrid.add(new Label("maxIter:"), 0, 2);
        optsGrid.add(maxIterSpinner, 1, 2);
        optsGrid.add(new Label("maxSeqLen:"), 0, 3);
        optsGrid.add(maxSeqLenSpinner, 1, 3);

        // Output chooser
        Label outputLabel = new Label("Output folder:");
        TextField outputField = new TextField(outputFolder.getAbsolutePath());
        Button chooseOutput = new Button("Choose...");
        chooseOutput.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setInitialDirectory(outputFolder.exists() ? outputFolder : new File("."));
            File chosen = dc.showDialog(primaryStage);
            if (chosen != null) {
                outputFolder = chosen;
                outputField.setText(chosen.getAbsolutePath());
            }
        });

        // Start / Cancel
        Button startBtn = new Button("Start Benchmark");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setDisable(true);

        left.getChildren().addAll(algPane, dsPane, kLabel, kField, optsLabel, optsGrid,
                outputLabel, new HBox(6, outputField, chooseOutput), new HBox(6, startBtn, cancelBtn));

        // Center: results table
        TableView<BenchmarkResult> table = new TableView<>(results);
        TableColumn<BenchmarkResult, String> colAlgo = new TableColumn<>("Algorithm");
        colAlgo.setCellValueFactory(c -> c.getValue().algorithmProperty());
        TableColumn<BenchmarkResult, String> colDs = new TableColumn<>("Dataset");
        colDs.setCellValueFactory(c -> c.getValue().datasetProperty());
        TableColumn<BenchmarkResult, Number> colK = new TableColumn<>("k");
        colK.setCellValueFactory(c -> c.getValue().kProperty());
        TableColumn<BenchmarkResult, Number> colTime = new TableColumn<>("Runtime (ms)");
        colTime.setCellValueFactory(c -> c.getValue().runtimeMsProperty());
        TableColumn<BenchmarkResult, Number> colMem = new TableColumn<>("Memory (MB)");
        colMem.setCellValueFactory(c -> c.getValue().memoryMbProperty());
        TableColumn<BenchmarkResult, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());

        table.getColumns().addAll(colAlgo, colDs, colK, colTime, colMem, colStatus);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Bottom: progress
        ProgressBar progressBar = new ProgressBar(0);
        Label statusLabel = new Label("Ready");

        BorderPane root = new BorderPane();
        root.setLeft(left);
        root.setCenter(table);
        VBox bottom = new VBox(6, progressBar, statusLabel);
        bottom.setPadding(new Insets(6));
        root.setBottom(bottom);

        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Action: Start
        final javafx.concurrent.Task<Void>[] runningTask = new javafx.concurrent.Task[1];

        startBtn.setOnAction(ev -> {
            // collect selected algorithms
            List<Class<? extends Algorithm>> selectedAlgos = algCheckBoxes.entrySet().stream()
                    .filter(e -> e.getValue().isSelected())
                    .map(e -> availableAlgorithms.get(e.getKey()))
                    .collect(Collectors.toList());
            if (selectedAlgos.isEmpty()) {
                showAlert("Please select at least one algorithm.");
                return;
            }

            // selected datasets
            List<File> selectedDatasets = dsBox.getChildren().stream()
                    .filter(n -> n instanceof CheckBox)
                    .map(n -> (CheckBox) n)
                    .filter(CheckBox::isSelected)
                    .map(cb -> (File) cb.getUserData())
                    .collect(Collectors.toList());
            if (selectedDatasets.isEmpty()) {
                showAlert("Please select at least one dataset.");
                return;
            }

            // parse ks
            List<Integer> ks;
            try {
                ks = parseKField(kField.getText());
                if (ks.isEmpty()) {
                    showAlert("No valid k values parsed.");
                    return;
                }
            } catch (Exception ex) {
                showAlert("Failed to parse k values: " + ex.getMessage());
                return;
            }

            // output folder
            File out = new File(outputField.getText().trim());
            if (!out.exists()) {
                boolean created = out.mkdirs();
                if (!created) {
                    showAlert("Failed to create output folder: " + out.getAbsolutePath());
                    return;
                }
            }

            int sampleSize = sampleSizeSpinner.getValue();
            double rho;
            try { rho = Double.parseDouble(rhoField.getText().trim()); } catch (Exception ex) { showAlert("Invalid rho"); return; }
            int maxIter = maxIterSpinner.getValue();
            int maxSeqLen = maxSeqLenSpinner.getValue();

            // clear previous results
            results.clear();

            // create and start runner
            BenchmarkRunner runner = new BenchmarkRunner(selectedAlgos, selectedDatasets, ks, out, sampleSize, rho, maxIter, maxSeqLen, 42L);

            progressBar.progressProperty().unbind();
            progressBar.progressProperty().bind(runner.progressProperty());
            statusLabel.textProperty().unbind();
            statusLabel.textProperty().bind(runner.messageProperty());
            startBtn.setDisable(true);
            cancelBtn.setDisable(false);

            Thread t = new Thread(runner, "benchmark-runner");
            t.setDaemon(true);
            t.start();
            runningTask[0] = runner;

            // A simple polling loop to read summary.csv and update the table when each result file exists
            // (or better: refactor BenchmarkRunner to publish per-run results via a listener)
            // For brevity this sample does not implement per-row UI update; you can extend runner to publish results.
            runner.setOnSucceeded(ev2 -> {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Completed");
                progressBar.progressProperty().unbind();
                progressBar.setProgress(1.0);
                startBtn.setDisable(false);
                cancelBtn.setDisable(true);
                // Optionally: scan output folder and populate results table
                Platform.runLater(() -> {
                    populateResultsTableFromOutput(out, results);
                });
            });
            runner.setOnFailed(ev2 -> {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Failed: " + runner.getException());
                progressBar.progressProperty().unbind();
                startBtn.setDisable(false);
                cancelBtn.setDisable(true);
            });
            runner.setOnCancelled(ev2 -> {
                statusLabel.textProperty().unbind();
                statusLabel.setText("Cancelled");
                progressBar.progressProperty().unbind();
                startBtn.setDisable(false);
                cancelBtn.setDisable(true);
            });
        });

        cancelBtn.setOnAction(ev -> {
            if (runningTask[0] != null && !runningTask[0].isDone()) {
                runningTask[0].cancel(true);
            }
        });
    }

    private void makeSpinnerEditable(Spinner<Integer> spinner) {
        spinner.setEditable(true);
        // Commit when Enter is pressed in the editor
        spinner.getEditor().setOnAction(e -> commitEditorText(spinner));
        // Commit when focus is lost
        spinner.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) commitEditorText(spinner);
        });
    }

    private static void commitEditorText(Spinner<Integer> spinner) {
        if (!spinner.isEditable()) return;
        SpinnerValueFactory<Integer> vf = spinner.getValueFactory();
        if (vf == null) return;
        String text = spinner.getEditor().getText();
        try {
            int value = Integer.parseInt(text.trim());
            if (vf instanceof SpinnerValueFactory.IntegerSpinnerValueFactory) {
                SpinnerValueFactory.IntegerSpinnerValueFactory intVf =
                        (SpinnerValueFactory.IntegerSpinnerValueFactory) vf;
                int min = intVf.getMin();
                int max = intVf.getMax();
                if (value < min) value = min;
                if (value > max) value = max;
                intVf.setValue(value);
            } else {
                vf.setValue(value);
            }
        } catch (NumberFormatException ex) {
            // revert to previous valid value
            spinner.getEditor().setText(String.valueOf(vf.getValue()));
        }
    }

    private VBox getVBox(VBox dsBox, TitledPane dsPane) {
        List<CheckBox> dsCheckboxes = new ArrayList<>();
        Button refreshDatasets = new Button("Refresh datasets");
        refreshDatasets.setOnAction(e -> {
            dsBox.getChildren().clear();
            File[] files = dataFolder.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".txt"));
            if (files != null) {
                for (File f : files) {
                    CheckBox cb = new CheckBox(f.getName());
                    cb.setSelected(true);
                    cb.setUserData(f);
                    dsBox.getChildren().add(cb);
                    dsCheckboxes.add(cb);
                }
            }
            dsPane.setText("Datasets (folder: " + dataFolder.getPath() + ")");
        });
        // default load
        refreshDatasets.fire();
        return new VBox(6, refreshDatasets, new ScrollPane(dsBox));
    }

    private void populateResultsTableFromOutput(File out, ObservableList<BenchmarkResult> results) {
        // read summary.csv or walk output tree and parse k_<k>.txt files to populate the table
        // quick implementation: find summary.csv
        File summary = new File(out, "summary.csv");
        if (summary.exists()) {
            try (Scanner sc = new Scanner(summary)) {
                if (sc.hasNextLine()) sc.nextLine(); // skip header
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(",", 6);
                    if (parts.length >= 6) {
                        String alg = parts[0].replaceAll("^\"|\"$", "");
                        String ds = parts[1].replaceAll("^\"|\"$", "");
                        int k = Integer.parseInt(parts[2]);
                        long rt = Long.parseLong(parts[3]);
                        double mem = Double.parseDouble(parts[4]);
                        String status = parts[5];
                        results.add(new BenchmarkResult(alg, ds, k, rt, mem, status));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private List<Integer> parseKField(String s) {
        s = s.trim();
        if (s.isEmpty()) return Collections.emptyList();
        List<Integer> ks = new ArrayList<>();
        String[] tokens = s.split("[,;\\s]+");
        for (String t : tokens) {
            if (t.contains("-")) {
                // format: start-end or start-end:step
                String[] part = t.split(":");
                String range = part[0];
                int step = 1;
                if (part.length > 1) step = Integer.parseInt(part[1]);
                String[] se = range.split("-");
                int start = Integer.parseInt(se[0]);
                int end = Integer.parseInt(se[1]);
                if (step <= 0) step = 1;
                if (start <= end) {
                    for (int x = start; x <= end; x += step) ks.add(x);
                } else {
                    for (int x = start; x >= end; x -= step) ks.add(x);
                }
            } else {
                ks.add(Integer.parseInt(t));
            }
        }
        // remove duplicates and sort
        return ks.stream().distinct().sorted().collect(Collectors.toList());
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}