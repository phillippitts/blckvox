package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.SaveResult;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Tab;
import com.boombapcompile.blckvox.service.settings.PropertyMetadata.ValidationError;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JavaFX settings window accessible from the system tray.
 * Excluded from test coverage (JavaFX threading).
 *
 * <p>Provides a tabbed interface (Basic / Advanced) with collapsible
 * sections, real-time validation, and comment-preserving save.
 */
public class ConfigurationWindow {

    private static final Logger LOG = LogManager.getLogger(ConfigurationWindow.class);
    private static final String TITLE = "blckvox Settings";
    private static final String DIRTY_TITLE = "blckvox Settings *";

    private static Stage existingStage;

    private final ConfigurationService service;
    private final Map<String, Node> controls = new LinkedHashMap<>();
    private final Map<String, Label> errorLabels = new LinkedHashMap<>();
    private final Map<String, String> originalValues = new LinkedHashMap<>();
    private final Set<String> validationErrors = new LinkedHashSet<>();

    private Stage stage;
    private Button saveButton;
    private boolean dirty;

    public ConfigurationWindow(ConfigurationService service) {
        this.service = service;
    }

    public void show() {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            throw new IllegalStateException(
                    "ConfigurationWindow.show() must be called on the FX Application Thread");
        }
        // Singleton: bring existing window to front
        if (existingStage != null && existingStage.isShowing()) {
            existingStage.toFront();
            existingStage.requestFocus();
            return;
        }

        SaveResult loadResult = service.loadSnapshot();
        if (loadResult instanceof SaveResult.IoFailure failure) {
            showErrorAlert("Could not load settings: " + failure.message());
            return;
        }

        stage = createStage();
        existingStage = stage;

        Scene scene = new Scene(buildRootLayout());
        registerKeyboardShortcuts(scene);
        stage.setScene(scene);

        stage.setOnCloseRequest(event -> {
            if (dirty) {
                event.consume();
                handleDirtyClose();
            } else {
                existingStage = null;
            }
        });

        stage.show();
    }

    private Stage createStage() {
        Stage s = new Stage();
        s.setTitle(TITLE);
        s.setMinWidth(650);
        s.setMinHeight(550);
        s.setMaxWidth(900);
        s.setMaxHeight(800);
        s.setWidth(750);
        s.setHeight(700);
        return s;
    }

    private VBox buildRootLayout() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().add(createTab("Basic", Tab.BASIC, true));
        tabPane.getTabs().add(createTab("Advanced", Tab.ADVANCED, false));

        VBox root = new VBox(tabPane, createButtonBar());
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        root.setPadding(new Insets(0));
        return root;
    }

    private void registerKeyboardShortcuts(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                () -> {
                    if (!saveButton.isDisabled()) {
                        handleSave();
                    }
                });
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.ESCAPE),
                () -> {
                    if (dirty) {
                        handleDirtyClose();
                    } else {
                        closeWindow();
                    }
                });
    }

    private javafx.scene.control.Tab createTab(String name, Tab tab,
                                                boolean expandSections) {
        javafx.scene.control.Tab fxTab = new javafx.scene.control.Tab(name);
        fxTab.setClosable(false);

        List<PropertyMetadata> properties = service.getAllByTab(tab);
        Map<String, String> snapshot = service.getSnapshot();

        // Group by section
        Map<String, List<PropertyMetadata>> sections = new LinkedHashMap<>();
        for (PropertyMetadata meta : properties) {
            sections.computeIfAbsent(meta.section(), k -> new java.util.ArrayList<>())
                    .add(meta);
        }

        VBox content = new VBox(5);
        content.setPadding(new Insets(10));

        for (var entry : sections.entrySet()) {
            TitledPane sectionPane = createSection(
                    entry.getKey(), entry.getValue(), snapshot, expandSections);
            content.getChildren().add(sectionPane);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        fxTab.setContent(scrollPane);
        return fxTab;
    }

    private TitledPane createSection(String sectionName,
                                     List<PropertyMetadata> properties,
                                     Map<String, String> snapshot,
                                     boolean expanded) {
        VBox sectionContent = new VBox(8);
        sectionContent.setPadding(new Insets(10));

        for (PropertyMetadata meta : properties) {
            String value = snapshot.getOrDefault(meta.key(), meta.defaultValue());
            originalValues.put(meta.key(), value);
            Node control = PropertyFieldFactory.createControl(meta, value);
            controls.put(meta.key(), control);

            // Spinner focus-loss commit
            if (control instanceof Spinner<?> spinner) {
                spinner.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) {
                        spinner.increment(0);
                    }
                });
            }

            // Validation error label (hidden by default)
            Label errorLabel = new Label();
            errorLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-size: 11;");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            errorLabels.put(meta.key(), errorLabel);

            // Description label
            Label descLabel = new Label(meta.description());
            descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11;");
            descLabel.setWrapText(true);

            // Label + control row
            Label nameLabel = new Label(meta.displayName());
            nameLabel.setPrefWidth(160);
            nameLabel.setMinWidth(Region.USE_PREF_SIZE);
            nameLabel.setLabelFor(control);

            // Tooltip with description
            Tooltip tooltip = new Tooltip(meta.description());
            Tooltip.install(control, tooltip);
            nameLabel.setTooltip(tooltip);

            // Accessible text
            control.setAccessibleText(meta.displayName() + ": " + meta.description());

            HBox row = new HBox(10, nameLabel, control);
            row.setAlignment(Pos.CENTER_LEFT);

            sectionContent.getChildren().addAll(row, descLabel, errorLabel);

            // Attach change listener for dirty tracking and real-time validation
            attachChangeListener(meta.key(), control);
        }

        TitledPane pane = new TitledPane(sectionName, sectionContent);
        pane.setExpanded(expanded);
        return pane;
    }

    private void attachChangeListener(String key, Node control) {
        Runnable onChange = () -> {
            String current = PropertyFieldFactory.extractValue(control);
            PropertyValidator validator = service.getValidator();

            applyFieldValidation(key, current, control, validator);
            applyCrossPropertyValidation(validator);
            updateDirtyState();

            if (saveButton != null) {
                saveButton.setDisable(!validationErrors.isEmpty());
            }
        };

        if (control instanceof javafx.scene.control.CheckBox cb) {
            cb.selectedProperty().addListener((obs, o, n) -> onChange.run());
        } else if (control instanceof javafx.scene.control.Spinner<?> spinner) {
            spinner.valueProperty().addListener((obs, o, n) -> onChange.run());
        } else if (control instanceof javafx.scene.control.TextField tf) {
            tf.textProperty().addListener((obs, o, n) -> onChange.run());
        } else if (control instanceof javafx.scene.control.ComboBox<?> combo) {
            combo.valueProperty().addListener((obs, o, n) -> onChange.run());
        }
    }

    private void applyFieldValidation(String key, String value,
                                       Node control, PropertyValidator validator) {
        Optional<ValidationError> error = validator.validateField(key, value);
        Label errorLabel = errorLabels.get(key);
        if (error.isPresent()) {
            String displayName = resolveDisplayName(key);
            errorLabel.setText(displayName + ": " + error.get().message());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            control.setStyle("-fx-border-color: #d32f2f;");
            validationErrors.add(key);
        } else {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            control.setStyle("");
            validationErrors.remove(key);
        }
    }

    private void applyCrossPropertyValidation(PropertyValidator validator) {
        Map<String, String> allCurrent = collectCurrentValues();
        List<ValidationError> crossErrors = validator.validateCrossProperty(allCurrent);

        // Collect keys that currently have cross-property errors
        Set<String> currentCrossErrorKeys = new LinkedHashSet<>();
        for (ValidationError crossErr : crossErrors) {
            currentCrossErrorKeys.add(crossErr.key());
        }

        // Clear resolved cross-property errors (not already flagged by field validation)
        for (String key : Set.copyOf(validationErrors)) {
            if (!currentCrossErrorKeys.contains(key)) {
                // Only clear if this was a cross-property error, not a field error
                Optional<ValidationError> fieldErr = validator.validateField(
                        key, allCurrent.getOrDefault(key, ""));
                if (fieldErr.isEmpty()) {
                    Label label = errorLabels.get(key);
                    if (label != null) {
                        label.setVisible(false);
                        label.setManaged(false);
                    }
                    Node ctrl = controls.get(key);
                    if (ctrl != null) {
                        ctrl.setStyle("");
                    }
                    validationErrors.remove(key);
                }
            }
        }

        // Add new cross-property errors
        for (ValidationError crossErr : crossErrors) {
            Label crossLabel = errorLabels.get(crossErr.key());
            if (crossLabel != null && !validationErrors.contains(crossErr.key())) {
                String name = resolveDisplayName(crossErr.key());
                crossLabel.setText(name + ": " + crossErr.message());
                crossLabel.setVisible(true);
                crossLabel.setManaged(true);
                Node crossControl = controls.get(crossErr.key());
                if (crossControl != null) {
                    crossControl.setStyle("-fx-border-color: #d32f2f;");
                }
                validationErrors.add(crossErr.key());
            }
        }
    }

    private String resolveDisplayName(String key) {
        return service.getRegistry().findByKey(key)
                .map(PropertyMetadata::displayName)
                .orElse(key);
    }

    private Map<String, String> collectCurrentValues() {
        Map<String, String> values = new LinkedHashMap<>(service.getSnapshot());
        for (var entry : controls.entrySet()) {
            values.put(entry.getKey(),
                    PropertyFieldFactory.extractValue(entry.getValue()));
        }
        return values;
    }

    private void updateDirtyState() {
        boolean anyChanged = false;
        for (var entry : controls.entrySet()) {
            String current = PropertyFieldFactory.extractValue(entry.getValue());
            String original = originalValues.getOrDefault(entry.getKey(), "");
            if (!current.equals(original)) {
                anyChanged = true;
                break;
            }
        }
        dirty = anyChanged;
        stage.setTitle(dirty ? DIRTY_TITLE : TITLE);
    }

    private HBox createButtonBar() {
        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> {
            if (dirty) {
                handleDirtyClose();
            } else {
                closeWindow();
            }
        });

        saveButton = new Button("Save");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> handleSave());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, spacer, cancelButton, saveButton);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setAlignment(Pos.CENTER_RIGHT);
        return bar;
    }

    /**
     * Validates and saves changes. Returns true on success, false on failure.
     */
    private boolean handleSave() {
        Map<String, String> snapshot = service.getSnapshot();
        Map<String, String> changes = new LinkedHashMap<>();

        for (var entry : controls.entrySet()) {
            String key = entry.getKey();
            String newValue = PropertyFieldFactory.extractValue(entry.getValue());
            String oldValue = snapshot.getOrDefault(key, "");
            if (!newValue.equals(oldValue)) {
                changes.put(key, newValue);
            }
        }

        if (changes.isEmpty()) {
            dirty = false;
            stage.setTitle(TITLE);
            return true;
        }

        SaveResult result = service.save(changes);

        if (result instanceof SaveResult.Success) {
            dirty = false;
            stage.setTitle(TITLE);
            // Update original values to match new saved state
            for (var entry : changes.entrySet()) {
                originalValues.put(entry.getKey(), entry.getValue());
            }
            showInfoAlert("Settings saved. Restart blckvox to apply changes.");
            return true;
        } else if (result instanceof SaveResult.ValidationFailure failure) {
            StringBuilder msg = new StringBuilder("Validation errors:\n");
            for (var err : failure.errors()) {
                PropertyMetadata meta = service.getRegistry()
                        .findByKey(err.key()).orElse(null);
                String displayName = meta != null ? meta.displayName() : err.key();
                msg.append("  ").append(displayName).append(": ")
                        .append(err.message()).append("\n");
            }
            showErrorAlert(msg.toString());
        } else if (result instanceof SaveResult.IoFailure failure) {
            showErrorAlert("Could not save: " + failure.message()
                    + "\nEdit application.properties manually.");
        }
        return false;
    }

    private void handleDirtyClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("You have unsaved changes.");
        alert.setContentText("What would you like to do?");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discardBtn = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        ButtonType cancelBtn = new ButtonType("Cancel",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveBtn, discardBtn, cancelBtn);

        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent()) {
            if (choice.get() == saveBtn) {
                if (handleSave()) {
                    closeWindow();
                }
                // If save failed, keep window open
            } else if (choice.get() == discardBtn) {
                closeWindow();
            }
            // Cancel: do nothing, keep window open
        }
    }

    private void closeWindow() {
        existingStage = null;
        if (stage != null) {
            stage.close();
        }
    }

    private void showInfoAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("Settings");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (stage != null) {
            alert.initOwner(stage);
        }
        alert.setTitle("Settings Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
