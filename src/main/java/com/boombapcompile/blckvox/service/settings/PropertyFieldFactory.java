package com.boombapcompile.blckvox.service.settings;

import com.boombapcompile.blckvox.service.settings.PropertyMetadata.Constraints;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

/**
 * Creates JavaFX controls for property metadata types.
 * Excluded from test coverage (JavaFX threading).
 */
class PropertyFieldFactory {

    private PropertyFieldFactory() {
    }

    static Node createControl(PropertyMetadata meta, String currentValue) {
        return switch (meta.type()) {
            case BOOLEAN -> createCheckBox(currentValue);
            case INT -> createIntSpinner(currentValue, meta.constraints());
            case LONG -> createLongSpinner(currentValue, meta.constraints());
            case DOUBLE -> createDoubleSpinner(currentValue, meta.constraints());
            case STRING -> createTextField(currentValue);
            case ENUM -> createComboBox(currentValue, meta.constraints());
        };
    }

    static String extractValue(Node control) {
        if (control instanceof CheckBox cb) {
            return String.valueOf(cb.isSelected());
        } else if (control instanceof Spinner<?> spinner) {
            return String.valueOf(spinner.getValue());
        } else if (control instanceof TextField tf) {
            return tf.getText();
        } else if (control instanceof ComboBox<?> combo) {
            Object selected = combo.getValue();
            return selected != null ? selected.toString() : "";
        }
        return "";
    }

    private static CheckBox createCheckBox(String value) {
        CheckBox cb = new CheckBox();
        cb.setSelected("true".equalsIgnoreCase(value));
        return cb;
    }

    private static Spinner<Integer> createIntSpinner(String value, Constraints constraints) {
        int min = constraints.min() != null ? constraints.min() : 0;
        int max = constraints.max() != null ? constraints.max() : Integer.MAX_VALUE;
        int current;
        try {
            current = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            current = min;
        }
        Spinner<Integer> spinner = new Spinner<>(min, max, current);
        spinner.setEditable(true);
        spinner.setPrefWidth(150);
        return spinner;
    }

    private static Spinner<Double> createDoubleSpinner(String value, Constraints constraints) {
        double min = constraints.minDouble() != null ? constraints.minDouble() : 0.0;
        double max = constraints.maxDouble() != null ? constraints.maxDouble() : Double.MAX_VALUE;
        double current;
        try {
            current = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            current = min;
        }
        Spinner<Double> spinner = new Spinner<>(min, max, current, 0.01);
        spinner.setEditable(true);
        spinner.setPrefWidth(150);
        return spinner;
    }

    private static Spinner<Long> createLongSpinner(String value, Constraints constraints) {
        long min = constraints.minLong() != null ? constraints.minLong() : 0L;
        long max = constraints.maxLong() != null ? constraints.maxLong() : Long.MAX_VALUE;
        long parsedVal;
        try {
            parsedVal = Long.parseLong(value);
        } catch (NumberFormatException e) {
            parsedVal = min;
        }
        final long currentVal = parsedVal;

        SpinnerValueFactory<Long> factory = new SpinnerValueFactory<>() {
            {
                setValue(currentVal);
            }

            @Override
            public void decrement(int steps) {
                long val = getValue() == null ? min : getValue();
                setValue(Math.max(min, val - steps));
            }

            @Override
            public void increment(int steps) {
                long val = getValue() == null ? min : getValue();
                setValue(Math.min(max, val + steps));
            }
        };
        Spinner<Long> spinner = new Spinner<>(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(150);
        return spinner;
    }

    private static TextField createTextField(String value) {
        TextField tf = new TextField(value != null ? value : "");
        tf.setPrefWidth(250);
        return tf;
    }

    private static ComboBox<String> createComboBox(String value, Constraints constraints) {
        ComboBox<String> combo = new ComboBox<>();
        if (constraints.enumValues() != null) {
            combo.getItems().addAll(constraints.enumValues());
        }
        if (value != null) {
            // Case-insensitive selection
            for (String item : combo.getItems()) {
                if (item.equalsIgnoreCase(value)) {
                    combo.setValue(item);
                    break;
                }
            }
        }
        combo.setPrefWidth(200);
        return combo;
    }
}
