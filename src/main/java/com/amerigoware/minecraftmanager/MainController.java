package com.amerigoware.minecraftmanager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import java.util.prefs.Preferences;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class MainController {

    @FXML private TabPane categoryTabPane;
    @FXML private ComboBox<String> versionComboBox;

    // Tab-specific ListViews
    @FXML private ListView<String> modsDisabledListView, modsEnabledListView;
    @FXML private ListView<String> shadersDisabledListView, shadersEnabledListView;
    @FXML private ListView<String> resourcePacksDisabledListView, resourcePacksEnabledListView;
    @FXML private ListView<String> worldsDisabledListView, worldsEnabledListView;

    // Datapacks & Structures ListViews
    @FXML private ListView<String> availableDatapacksView, enabledDatapacksView;
    @FXML private ListView<String> availableStructuresView, enabledStructuresView;
    @FXML private ListView<String> worldSelectorListView;
    @FXML private Label statusLabel;
    @FXML private ProgressBar operationProgressBar;
    @FXML private ProgressIndicator operationSpinner;
    private Path mcDir;

    @FXML
    public void initialize() {
        mcDir = getDefaultMinecraftDirectory();

        // Configure event listeners and drag-and-drop across all tab list views
        configureAllTabListViews();

        // Single tab selection listener to auto-refresh lists
        if (categoryTabPane != null) {
            categoryTabPane.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldTab, newTab) -> {
                        if (newTab != null) refreshLists();
                    }
            );
        }
        loadMinecraftVersions();
        setupWorldScopedTab();
        setupExternalDrop(categoryTabPane);
        refreshLists();
    }

    private void configureAllTabListViews() {
        ListView<String>[] disabledViews = new ListView[]{
                modsDisabledListView, shadersDisabledListView, resourcePacksDisabledListView, worldsDisabledListView
        };
        ListView<String>[] enabledViews = new ListView[]{
                modsEnabledListView, shadersEnabledListView, resourcePacksEnabledListView, worldsEnabledListView
        };

        for (int i = 0; i < disabledViews.length; i++) {
            configureListView(disabledViews[i], true);
            configureListView(enabledViews[i], false);
        }
    }

    private void configureListView(ListView<String> listView, boolean isDisabledPane) {
        if (listView == null) return;

        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setOnMouseClicked(e -> handleDoubleClick(e, isDisabledPane));
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                if (isDisabledPane) handleDeleteDisabled(); else handleDeleteEnabled();
            }
        });

        setupDragSource(listView);
        setupDropTarget(listView, isDisabledPane);
    }

    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);

    private void loadMinecraftVersions() {
        Path versionsFolder = mcDir.resolve("versions");
        ObservableList<String> versions = FXCollections.observableArrayList("Default (.minecraft)");

        if (Files.exists(versionsFolder)) {
            try (Stream<Path> stream = Files.list(versionsFolder)) {
                stream.filter(Files::isDirectory)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(versions::add);
            } catch (IOException e) {
                System.err.println("Failed to read versions directory: " + e.getMessage());
            }
        }

        versionComboBox.setItems(versions);

        // Restore saved version setting or default to first item
        String savedVersion = prefs.get("selected_version", "Default (.minecraft)");
        if (versions.contains(savedVersion)) {
            versionComboBox.getSelectionModel().select(savedVersion);
        } else {
            versionComboBox.getSelectionModel().selectFirst();
        }

        // Save selection whenever changed
        versionComboBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        prefs.put("selected_version", newVal);
                        refreshLists();
                    }
                }
        );
    }

    private void handleDoubleClick(MouseEvent event, boolean isDisabledPane) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            ListView<String> sourceView = isDisabledPane ? getActiveDisabledListView() : getActiveEnabledListView();
            if (sourceView == null) return;

            List<String> selectedItems = new ArrayList<>(sourceView.getSelectionModel().getSelectedItems());

            if (!selectedItems.isEmpty()) {
                Path activeFolder = getCategoryFolder(getDirectoryNameForTab());
                Path disabledFolder = activeFolder.resolveSibling(activeFolder.getFileName() + "_disabled");

                for (String selected : selectedItems) {
                    Path source = (isDisabledPane ? disabledFolder : activeFolder).resolve(selected);
                    Path target = (isDisabledPane ? activeFolder : disabledFolder).resolve(selected);
                    moveFile(source, target);
                }
                refreshLists();
            }
        }
    }

    private void setupDragSource(ListView<String> listView) {
        listView.setOnDragDetected(event -> {
            List<String> selectedItems = listView.getSelectionModel().getSelectedItems();
            if (!selectedItems.isEmpty()) {
                Dragboard db = listView.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.join("\n", selectedItems));
                db.setContent(content);
                event.consume();
            }
        });
    }

    private void setupDropTarget(ListView<String> targetView, boolean isTargetDisabledPane) {
        targetView.setOnDragOver(event -> {
            if (event.getGestureSource() != targetView && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        targetView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasString()) {
                String[] filenames = db.getString().split("\n");
                Path activeFolder = getCategoryFolder(getDirectoryNameForTab());
                Path disabledFolder = activeFolder.resolveSibling(activeFolder.getFileName() + "_disabled");

                for (String fileName : filenames) {
                    if (!fileName.trim().isEmpty()) {
                        Path source = (isTargetDisabledPane ? activeFolder : disabledFolder).resolve(fileName);
                        Path target = (isTargetDisabledPane ? disabledFolder : activeFolder).resolve(fileName);
                        moveFile(source, target);
                    }
                }
                refreshLists();
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    private void handleRefresh() {
        refreshLists();
    }

    @FXML
    private void handleChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select .minecraft Directory");
        if (mcDir != null && Files.exists(mcDir)) {
            chooser.setInitialDirectory(mcDir.toFile());
        }
        File selected = chooser.showDialog(categoryTabPane.getScene().getWindow());
        if (selected != null) {
            mcDir = selected.toPath();
            refreshLists();
        }
    }

    @FXML
    private void handleLaunch() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                launchWindows();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-a", "Minecraft").start();
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("minecraft-launcher").start();
            }
        } catch (IOException e) {
            System.err.println("Failed to launch Minecraft Launcher: " + e.getMessage());
        }
    }

    private void launchWindows() throws IOException {
        try {
            new ProcessBuilder("cmd", "/c", "start", "minecraft:").start();
            return;
        } catch (IOException ignored) {}

        File standardExe = new File("C:\\Program Files (x86)\\Minecraft Launcher\\MinecraftLauncher.exe");
        if (standardExe.exists()) {
            new ProcessBuilder(standardExe.getAbsolutePath()).start();
            return;
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            File localExe = new File(localAppData, "Programs\\Minecraft Launcher\\MinecraftLauncher.exe");
            if (localExe.exists()) {
                new ProcessBuilder(localExe.getAbsolutePath()).start();
            }
        }
    }

    private ListView<String> getActiveDisabledListView() {
        Tab selected = categoryTabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return modsDisabledListView;
        String text = selected.getText();
        if (text.contains("Shader")) return shadersDisabledListView;
        if (text.contains("Resource")) return resourcePacksDisabledListView;
        if (text.contains("World")) return worldsDisabledListView;
        return modsDisabledListView;
    }

    private ListView<String> getActiveEnabledListView() {
        Tab selected = categoryTabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return modsEnabledListView;
        String text = selected.getText();
        if (text.contains("Shader")) return shadersEnabledListView;
        if (text.contains("Resource")) return resourcePacksEnabledListView;
        if (text.contains("World")) return worldsEnabledListView;
        return modsEnabledListView;
    }

    private void refreshLists() {
        if (mcDir == null || !Files.exists(mcDir)) return;

        Tab selectedTab = categoryTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return;

        if (selectedTab.getText().contains("Datapack")) {
            loadWorldList();
            if (worldSelectorListView != null) {
                String selectedWorld = worldSelectorListView.getSelectionModel().getSelectedItem();
                if (selectedWorld != null) {
                    refreshWorldScopedLists(selectedWorld);
                }
            }
        } else {
            String dirName = getDirectoryNameForTab();
            Path enabledPath = getCategoryFolder(dirName);
            Path disabledPath = enabledPath.resolveSibling(dirName + "_disabled");

            ensureDirectoryExists(enabledPath);
            ensureDirectoryExists(disabledPath);

            ListView<String> disabledView = getActiveDisabledListView();
            ListView<String> enabledView = getActiveEnabledListView();

            if (disabledView != null) disabledView.setItems(loadDirectoryContents(disabledPath));
            if (enabledView != null) enabledView.setItems(loadDirectoryContents(enabledPath));
        }
    }

    private Path getCategoryFolder(String subDirName) {
        String selectedVersion = versionComboBox.getSelectionModel().getSelectedItem();

        if (selectedVersion == null || selectedVersion.startsWith("Default")) {
            return mcDir.resolve(subDirName);
        }

        return mcDir.resolve("versions").resolve(selectedVersion).resolve(subDirName);
    }

    private String getDirectoryNameForTab() {
        Tab selectedTab = categoryTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) return "mods";

        String tabText = selectedTab.getText().toLowerCase();
        if (tabText.contains("shader")) return "shaderpacks";
        if (tabText.contains("resource")) return "resourcepacks";
        if (tabText.contains("datapack")) return "datapacks";
        if (tabText.contains("world")) return "saves";
        return "mods";
    }

    private ObservableList<String> loadDirectoryContents(Path folder) {
        ObservableList<String> items = FXCollections.observableArrayList();
        if (!Files.exists(folder)) return items;

        try (Stream<Path> stream = Files.list(folder)) {
            stream.map(Path::getFileName)
                    .map(Path::toString)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(items::add);
        } catch (IOException e) {
            System.err.println("Failed to read folder: " + folder + " | " + e.getMessage());
        }
        return items;
    }

    private void moveFile(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error moving item: " + e.getMessage());
        }
    }

    private void ensureDirectoryExists(Path folder) {
        if (!Files.exists(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                System.err.println("Could not create directory: " + folder);
            }
        }
    }

    private Path getDefaultMinecraftDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Paths.get(appData != null ? appData : userHome, ".minecraft");
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", "minecraft");
        } else {
            return Paths.get(userHome, ".minecraft");
        }
    }

    @FXML
    private void handleDeleteDisabled() {
        deleteSelectedItem(true);
    }

    @FXML
    private void handleDeleteEnabled() {
        deleteSelectedItem(false);
    }

    private void deleteSelectedItem(boolean isDisabledPane) {
        ListView<String> targetView = isDisabledPane ? getActiveDisabledListView() : getActiveEnabledListView();
        if (targetView == null) return;

        List<String> selectedItems = new ArrayList<>(targetView.getSelectionModel().getSelectedItems());
        if (selectedItems.isEmpty()) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Confirmation");
        if (selectedItems.size() == 1) {
            alert.setHeaderText("Delete '" + selectedItems.get(0) + "'?");
        } else {
            alert.setHeaderText("Delete " + selectedItems.size() + " selected items?");
        }
        alert.setContentText("Are you sure you want to permanently delete these item(s) from disk?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Path activeFolder = getCategoryFolder(getDirectoryNameForTab());
                Path disabledFolder = activeFolder.resolveSibling(activeFolder.getFileName() + "_disabled");

                for (String selected : selectedItems) {
                    Path targetPath = (isDisabledPane ? disabledFolder : activeFolder).resolve(selected);
                    try {
                        deletePathRecursively(targetPath);
                    } catch (IOException e) {
                        System.err.println("Failed to delete " + selected + ": " + e.getMessage());
                    }
                }
                refreshLists();
            }
        });
    }

    private void deletePathRecursively(Path pathToDelete) throws IOException {
        if (!Files.exists(pathToDelete)) return;

        if (Files.isDirectory(pathToDelete)) {
            try (Stream<Path> stream = Files.walk(pathToDelete)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } else {
            Files.delete(pathToDelete);
        }
    }

    public void setupExternalDrop(javafx.scene.Node rootNode) {
        rootNode.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        rootNode.setOnDragDropped(event -> {
            var db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    processIngestedFile(file.toPath());
                }
                refreshLists();
                success = true;
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void processIngestedFile(Path sourcePath) {
        FileInspector.FileAnalysis analysis = FileInspector.inspect(sourcePath);

        if (analysis.detectedVersion() != null && versionComboBox.getItems().contains(analysis.detectedVersion())) {
            versionComboBox.getSelectionModel().select(analysis.detectedVersion());
        }

        String targetSubDir = switch (analysis.type()) {
            case MOD -> "mods";
            case SHADER -> "shaderpacks";
            case RESOURCE_PACK -> "resourcepacks";
            case DATAPACK -> "datapacks";
            case WORLD -> "saves";
            case UNKNOWN -> getDirectoryNameForTab();
        };

        Path destinationFolder = getCategoryFolder(targetSubDir);
        ensureDirectoryExists(destinationFolder);

        try {
            if (Files.isDirectory(sourcePath)) {
                copyDirectoryRecursively(sourcePath, destinationFolder.resolve(sourcePath.getFileName()));
            } else {
                Files.copy(sourcePath, destinationFolder.resolve(sourcePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("Failed to import external file: " + e.getMessage());
        }
    }

    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    ensureDirectoryExists(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                System.err.println("Failed copying subfile: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleMigrateDefaultContent() {
        String selectedVersion = versionComboBox.getSelectionModel().getSelectedItem();
        if (selectedVersion == null || selectedVersion.startsWith("Default")) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Select a specific target version profile first.");
            alert.show();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Migrate Legacy Content");
        confirm.setHeaderText("Migrate content from root .minecraft into profile '" + selectedVersion + "'?");
        confirm.setContentText("This will scan root mods, shaders, resource packs, datapacks, and worlds, moving compatible files into the selected version folder.");

        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            String[] categories = {"mods", "shaderpacks", "resourcepacks", "datapacks", "saves"};

            for (String category : categories) {
                Path rootCategoryPath = mcDir.resolve(category);
                if (!Files.exists(rootCategoryPath)) continue;

                try (Stream<Path> stream = Files.list(rootCategoryPath)) {
                    stream.forEach(file -> {
                        FileInspector.FileAnalysis analysis = FileInspector.inspect(file);

                        if (analysis.detectedVersion() == null || analysis.detectedVersion().equals(selectedVersion)) {
                            Path destFolder = mcDir.resolve("versions").resolve(selectedVersion).resolve(category);
                            ensureDirectoryExists(destFolder);
                            moveFile(file, destFolder.resolve(file.getFileName()));
                        }
                    });
                } catch (IOException e) {
                    System.err.println("Migration failed for " + category + ": " + e.getMessage());
                }
            }
            refreshLists();
        });
    }

    @FXML
    private void handleExit() {
        javafx.application.Platform.exit();
    }

    private void setupWorldScopedTab() {
        if (worldSelectorListView == null) return;

        loadWorldList();

        worldSelectorListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldWorld, selectedWorld) -> {
                    if (selectedWorld != null) {
                        refreshWorldScopedLists(selectedWorld);
                    }
                }
        );
    }

    private void loadWorldList() {
        if (worldSelectorListView == null) return;

        Path savesDir = getCategoryFolder("saves");
        ObservableList<String> worlds = FXCollections.observableArrayList();

        if (Files.exists(savesDir)) {
            try (Stream<Path> stream = Files.list(savesDir)) {
                stream.filter(Files::isDirectory)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(worlds::add);
            } catch (IOException e) {
                System.err.println("Failed scanning worlds: " + e.getMessage());
            }
        }
        worldSelectorListView.setItems(worlds);
        if (worldSelectorListView.getSelectionModel().getSelectedItem() == null && !worlds.isEmpty()) {
            worldSelectorListView.getSelectionModel().selectFirst();
        }
    }

    private void refreshWorldScopedLists(String worldName) {
        if (availableDatapacksView == null || enabledDatapacksView == null ||
                availableStructuresView == null || enabledStructuresView == null) return;

        Path savesDir = getCategoryFolder("saves").resolve(worldName);

        Path worldDatapacks = savesDir.resolve("datapacks");
        Path worldStructures = savesDir.resolve("generated");

        Path repoDatapacks = mcDir.resolve("datapacks_storage");
        Path repoStructures = mcDir.resolve("structures_storage");

        ensureDirectoryExists(worldDatapacks);
        ensureDirectoryExists(worldStructures);
        ensureDirectoryExists(repoDatapacks);
        ensureDirectoryExists(repoStructures);

        availableDatapacksView.setItems(loadDirectoryContents(repoDatapacks));
        enabledDatapacksView.setItems(loadDirectoryContents(worldDatapacks));

        availableStructuresView.setItems(loadDirectoryContents(repoStructures));
        enabledStructuresView.setItems(loadDirectoryContents(worldStructures));
    }

    @FXML
    private void handleBackupWorld() {
        ListView<String> activeView = getActiveEnabledListView();
        String selectedWorld = activeView != null ? activeView.getSelectionModel().getSelectedItem() : null;

        if (selectedWorld == null) {
            new Alert(Alert.AlertType.WARNING, "Please select an enabled world to back up.").show();
            return;
        }

        Path worldFolder = getCategoryFolder("saves").resolve(selectedWorld);
        Path backupFolder = mcDir.resolve("backups");
        ensureDirectoryExists(backupFolder);

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path targetZip = backupFolder.resolve(timestamp + "_" + selectedWorld + ".zip");

        runAsyncTask(
                "Compressing world backup...",
                () -> zipDirectory(worldFolder, targetZip),
                () -> new Alert(Alert.AlertType.INFORMATION, "Backup created successfully!").show()
        );
    }

    @FXML
    private void handleRestoreWorld() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select World Backup Archive");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Zip Files", "*.zip"));

        Path backupFolder = mcDir.resolve("backups");
        if (Files.exists(backupFolder)) {
            chooser.setInitialDirectory(backupFolder.toFile());
        }

        File selectedFile = chooser.showOpenDialog(categoryTabPane.getScene().getWindow());
        if (selectedFile != null) {
            Path targetSaves = getCategoryFolder("saves");
            try {
                unzipArchive(selectedFile.toPath(), targetSaves);
                refreshLists();
                new Alert(Alert.AlertType.INFORMATION, "World restored successfully to saves!").show();
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, "Failed to restore backup: " + e.getMessage()).show();
            }
        }
    }

    // Zip Utility Method
    private void zipDirectory(Path sourceDir, Path targetZip) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(targetZip));
             Stream<Path> stream = Files.walk(sourceDir)) {
            stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(sourceDir.relativize(path).toString());
                try {
                    zos.putNextEntry(zipEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    System.err.println("Failed zipping path: " + path);
                }
            });
        }
    }

    // Unzip Utility Method
    private void unzipArchive(Path zipFile, Path destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    ensureDirectoryExists(newPath);
                } else {
                    ensureDirectoryExists(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    // Helper to run heavy file operations off the UI thread
    private void runAsyncTask(String statusMessage, RunnableBackgroundWork work, Runnable onComplete) {
        categoryTabPane.getScene().setCursor(javafx.scene.Cursor.WAIT);
        categoryTabPane.setDisable(true);

        if (statusLabel != null) statusLabel.setText(statusMessage);
        if (operationProgressBar != null) {
            operationProgressBar.setVisible(true);
            operationProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        }
        if (operationSpinner != null) operationSpinner.setVisible(true);

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() throws Exception {
                work.run();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            resetUIState("Ready");
            if (onComplete != null) onComplete.run();
        });

        task.setOnFailed(e -> {
            resetUIState("Error during operation.");
            Throwable ex = task.getException();
            new Alert(Alert.AlertType.ERROR, "Operation failed: " + ex.getMessage()).show();
        });

        new Thread(task).start();
    }

    private void resetUIState(String message) {
        if (categoryTabPane.getScene() != null) {
            categoryTabPane.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
        }
        categoryTabPane.setDisable(false);
        if (statusLabel != null) statusLabel.setText(message);
        if (operationProgressBar != null) operationProgressBar.setVisible(false);
        if (operationSpinner != null) operationSpinner.setVisible(false);
    }

    @FunctionalInterface
    private interface RunnableBackgroundWork {
        void run() throws Exception;
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Minecraft Manager");
        alert.setHeaderText("Minecraft Manager v2.0\nCreated by Emmery Chrisco (Amerigoware)");

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);

        Label bioLabel = new Label(
                "A lightweight desktop utility for toggling mods, shaders, resource packs, " +
                        "and managing world backups without launcher overhead."
        );
        bioLabel.setWrapText(true);

        Label authorNote = new Label(
                "When I'm not programming utilities or building massive castles in Minecraft, " +
                        "I author hard science fiction novels."
        );
        authorNote.setWrapText(true);

        Hyperlink mainWikiLink = new Hyperlink("• Explore the 'One Mind, Two Stars' Worldbuilding Wiki");
        mainWikiLink.setOnAction(e -> openWebpage("https://amerigoware1.github.io/One-Mind-Two-Stars-Wiki/index.html"));

        Hyperlink anthologyLink = new Hyperlink("• Read the Preview Anthology (Password: BRIDGE)");
        anthologyLink.setOnAction(e -> openWebpage("https://amerigoware1.github.io/One-Mind-Two-Stars-Wiki/anthology.html"));

        content.getChildren().addAll(bioLabel, authorNote, mainWikiLink, anthologyLink);

        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    private void openWebpage(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            System.err.println("Failed to open browser URL: " + e.getMessage());
        }
    }
}