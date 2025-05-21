package de.julianweinelt;

import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import de.julianweinelt.export.Exporter;
import de.julianweinelt.export.SQLExporter;
import de.julianweinelt.obj.GAPTEQPage;
import de.julianweinelt.obj.GAPTEQRepository;
import de.julianweinelt.obj.GAPTEQStatement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
public class Main {
    @Getter
    private static Main instance;

    private final Gson GSON = new Gson();

    private File folder = new File("C:\\Users\\weinelt\\Documents\\GAPTEQ_Repo\\Qconnect\\public");
    private final ConcurrentLinkedDeque<GAPTEQPage> pages = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<GAPTEQStatement> statements = new ConcurrentLinkedDeque<>();

    private String pagesTableName;
    private String statementTableName;

    private boolean exportPages;
    private boolean exportStatements;

    private int fileAmount = 0;

    private Exporter exporter = new SQLExporter("pages", "statements", false);

    private JLabel infoLabel;
    private JProgressBar progressBar;

    public static void main(String[] args) {
        instance = new Main();
        instance.createGUI();
    }

    private void createGUI() {
        FlatLightLaf.setup();
        JFrame frame = new JFrame("GAPTEQ Data Exporter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(300, 200, 500, 350);

        frame.setLayout(new FlowLayout());

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(400, 30));

        JLabel optionHeader = new JLabel("Options");
        JCheckBox usePages = new JCheckBox("Export pages");
        usePages.setEnabled(false);
        usePages.addActionListener(e -> {
            exportPages = usePages.isSelected();
        });
        JCheckBox useStatements = new JCheckBox("Export statements");
        useStatements.setEnabled(false);
        useStatements.addActionListener(e -> {
            exportStatements = useStatements.isSelected();
        });

        JPanel options = new JPanel();
        options.add(optionHeader);
        options.add(usePages);
        options.add(useStatements);

        JCheckBox createTablesBox = new JCheckBox("Attach CREATE TABLE script");
        createTablesBox.setEnabled(false);
        createTablesBox.addActionListener(e -> {
            exporter.setCreateTables(createTablesBox.isSelected());
        });
        options.add(createTablesBox);

        String[] formats = {"MSSQL", "MySQL", "CSV", "Excel"};
        JComboBox<String> exportOptions = new JComboBox<>(formats);
        exportOptions.setSelectedIndex(0);
        exportOptions.addActionListener(e -> {
            String selection = (String) exportOptions.getSelectedItem();
            if (selection == null) return;
            if (selection.equals("MSSQL")) {
                exporter = new SQLExporter(pagesTableName, statementTableName, false);
                createTablesBox.setEnabled(true);
            } else {
                createTablesBox.setEnabled(false);
            }
        });
        exportOptions.setEnabled(false);
        JLabel label = new JLabel("No folder selected");


        JPanel export = new JPanel();
        export.add(new JLabel("Select the format for export:"));
        export.add(exportOptions);


        JButton startButton = new JButton("Start export");
        startButton.setEnabled(false);
        startButton.addActionListener(e -> {
            pages.clear();
            statements.clear();
            progressBar.setValue(0);
            progressBar.setMaximum(getFileAmount(folder));
            new Thread(() -> {
                try {
                    goThroughFiles(folder);
                    saveData();
                    infoLabel.setText("All data has been exported. Pages: " + pages.size()
                            + " Statements: " + statements.size());
                    JOptionPane.showMessageDialog(null, "The selected data has been exported.\n" +
                            "You may now use it in your database or summaries.");
                    log.info("Finished!");
                } catch (FileNotFoundException ignored) {
                    label.setText("An internal error occurred. File not found.");
                    label.setForeground(Color.RED);
                }
            }).start();
        });

        JPanel inputs = new JPanel();

        JTextField pageTNameEnter = new JTextField("");
        pageTNameEnter.setEnabled(false);
        pageTNameEnter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                pagesTableName = pageTNameEnter.getText();
                exporter.setPagesTableName(pagesTableName);
                log.info("Set pages table name to: {}", pagesTableName);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                pagesTableName = pageTNameEnter.getText();
                exporter.setPagesTableName(pagesTableName);
                log.info("Set pages table name to: {}", pagesTableName);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });

        JTextField statementsTNameEnter = new JTextField("");
        statementsTNameEnter.setEnabled(false);
        statementsTNameEnter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                statementTableName = statementsTNameEnter.getText();
                exporter.setStatementsTableName(statementTableName);
                log.info("Set statements table name to: {}", statementTableName);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                statementTableName = statementsTNameEnter.getText();
                exporter.setStatementsTableName(statementTableName);
                log.info("Set statements table name to: {}", statementTableName);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });


        JButton button = new JButton("Select folder");

        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select a folder with a GAPTEQ repository");

            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                folder = chooser.getSelectedFile();
                GAPTEQRepository repo = loadRepo(folder);
                if (repo == null) {
                    label.setText("This folder seems not to be a GAPTEQ repository!");
                    label.setForeground(Color.RED);
                    return;
                }
                fileAmount = getFileAmount(folder);
                progressBar.setMaximum(fileAmount);
                label.setText("Selected repository: " + repo.name() + " (" + fileAmount + " files)");
                startButton.setEnabled(true);
                useStatements.setEnabled(true);
                usePages.setEnabled(true);
                exportOptions.setEnabled(true);
                pageTNameEnter.setEnabled(true);
                statementsTNameEnter.setEnabled(true);
            }
        });

        infoLabel = new JLabel("");
        infoLabel.setPreferredSize(new Dimension(400, 20));

        inputs.add(new JLabel("Table name for pages:"));
        inputs.add(pageTNameEnter);
        inputs.add(new JLabel("Table name for statements:"));
        inputs.add(statementsTNameEnter);

        JButton exitButton = new JButton("Exit");
        exitButton.setBounds(frame.getBounds().width - 80, frame.getBounds().height - 50, 40, 20);
        exitButton.addActionListener(e -> System.exit(0));

        frame.add(button);
        frame.add(label);

        frame.add(options);
        frame.add(export);

        frame.add(inputs);

        frame.add(startButton);

        JPanel infoPanel = new JPanel();
        infoPanel.add(infoLabel);


        frame.add(infoPanel);
        frame.add(progressBar);

        frame.add(exitButton);

        frame.setVisible(true);
    }

    private GAPTEQRepository loadRepo(File folder) {
        File toLoad = new File(folder, "Repository.json");
        for (File f : folder.listFiles()) log.info("Found file: {}", f.getName());
        if (!toLoad.exists()) {
            log.warn("Repo file does not exist.");
            return null;
        }

        try {
            JsonReader reader = new JsonReader(new FileReader(toLoad));
            reader.setStrictness(Strictness.LENIENT);
            JsonObject obj = ((JsonElement) GSON.fromJson(reader, JsonElement.class)).getAsJsonObject();

            JsonObject jsonObject =  obj.getAsJsonObject();
            return new GAPTEQRepository(jsonObject.get("name").getAsString(), jsonObject.get("description").getAsString(),
                    folder);
        } catch (FileNotFoundException ignored) {}
        return null;
    }

    private boolean isFolderRepo(File file) {
        return new File(file, "Public").isDirectory();
    }

    private int getFileAmount(File file) {
        int amount = 0;
        File[] files = file.listFiles();
        if (files == null) return amount;
        for (File f : files) {
            if (f.isDirectory()) {
                amount += getFileAmount(f);
                continue;
            }
            amount++;
        }
        return amount;
    }

    private void goThroughFiles(File file) throws FileNotFoundException {
        File[] files = file.listFiles();
        if (files == null) return;
        for (File f : files) {
            //try {Thread.sleep(10);} catch (InterruptedException ignored) {}
            infoLabel.setText("Loading " + f.getName() + "...");
            progressBar.setValue(progressBar.getValue() + 1);
            if (f.isDirectory()) {
                goThroughFiles(f);
            } else {
                if (f.getName().endsWith(".meta")) {

                    JsonReader reader = new JsonReader(new FileReader(f));
                    reader.setStrictness(Strictness.LENIENT);
                    JsonObject obj = ((JsonElement) GSON.fromJson(reader, JsonElement.class)).getAsJsonObject();

                    JsonObject jsonObject =  obj.getAsJsonObject();
                    if (jsonObject.get("dataType").getAsString().equals("GT.Formy.Repository.Template.UnityPage") && exportPages) {
                        GAPTEQPage p = loadPage(f, jsonObject);
                        if (p != null) pages.add(p);
                    }
                    else if (jsonObject.get("dataType").getAsString()
                            .equals("GT.Formy.Repository.UserData.UserStatementDefinition") && exportStatements) {
                        GAPTEQStatement stmt = loadStatement(f, jsonObject);
                        if (stmt != null) statements.add(stmt);
                    }
                    else {
                        infoLabel.setText("Error: " + f.getName());
                        log.error("File {} could not be identified as a page or statement.", f.getAbsolutePath());
                        log.error("Detected type: {}", jsonObject.get("dataType").getAsString());
                    }
                }
            }
        }
    }

    private GAPTEQPage loadPage(File f, JsonObject jsonObject) {
        String secondFileName = f.getName().replace(".meta", "");


        try {
            GAPTEQPage page = new GAPTEQPage();
            page.setCreatedBy(jsonObject.get("creator").getAsString().replace("\\", "\\\\"));
            page.setCreatedAt(jsonObject.get("createDate").getAsString());
            page.setModifiedBy(jsonObject.get("modifier").getAsString().replace("\\", "\\\\"));
            page.setModifiedAt(jsonObject.get("modifyDate").getAsString());
            JsonReader reader1 = new JsonReader(new FileReader(new File(f.getParent(), secondFileName)));

            JsonObject o = ((JsonElement) GSON.fromJson(reader1, JsonElement.class)).getAsJsonObject();
            page.setPageTemplate(o.get("pageDesign").getAsJsonObject()
                    .get("pageDefinition").getAsJsonObject().get("hiddenControls")
                    .getAsJsonArray().get(1).getAsJsonObject().get("pageTemplate").getAsString()
            );
            String name = f.getAbsolutePath().replace("\\", "/");
            StringBuilder b = new StringBuilder();
            boolean publicFound = false;
            for (String s : name.split("/")) {
                if (s.equalsIgnoreCase("public")) publicFound = true;
                if (!publicFound) continue;
                b.append(s).append("/");
            }
            if (b.toString().isEmpty()) {
                page.setPageName(name.replace(".meta", ""));
            } else
                page.setPageName(b.toString().replace(".meta", ""));

            // pageDesign.pageDefinition.hiddenControls[1].pageTemplate)
            return page;
        } catch (Exception e) {

            // Der Fehler soll im Detail ausgegeben werden
            if (e instanceof IllegalStateException ie) {
                log.error("The file {} seems not to be a valid JSON page.", f.getName());
                log.error(ie.getMessage());
            }
            log.error("Page not loaded");
            log.error(f.getAbsolutePath());
            log.error(e.getMessage());
            printStackTrace(e);
            try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        }
        return null;
    }

    private GAPTEQStatement loadStatement(File f, JsonObject obj) {
        String secondFileName = f.getName().replace(".meta", "");


        try {
            GAPTEQStatement stmt = new GAPTEQStatement();
            stmt.setCreatedBy(obj.get("creator").getAsString().replace("\\", "\\\\"));
            stmt.setCreatedAt(obj.get("createDate").getAsString());
            stmt.setModifiedBy(obj.get("modifier").getAsString().replace("\\", "\\\\"));
            stmt.setModifiedAt(obj.get("modifyDate").getAsString());
            JsonReader reader1 = new JsonReader(new FileReader(new File(f.getParent(), secondFileName)));

            JsonObject o = ((JsonElement) GSON.fromJson(reader1, JsonElement.class)).getAsJsonObject();
            stmt.setConnectionName(o.get("connectionName").getAsString());
            String name = f.getAbsolutePath().replace("\\", "/");
            StringBuilder b = new StringBuilder();
            boolean publicFound = false;
            for (String s : name.split("/")) {
                if (s.equalsIgnoreCase("public")) publicFound = true;
                if (!publicFound) continue;
                b.append(s).append("/");
            }
            if (b.toString().isEmpty()) {
                stmt.setStatementName(name.replace(".meta", ""));
            } else
                stmt.setStatementName(b.toString().replace(".meta", ""));

            return stmt;
        } catch (Exception e) {

            // Der Fehler soll im Detail ausgegeben werden
            if (e instanceof IllegalStateException ie) {
                log.error("The file {} seems not to be a valid JSON.", f.getName());
                log.error(ie.getMessage());
            }
            log.error("Statement not loaded");
            log.error(f.getAbsolutePath());
            log.error(e.getMessage());
            printStackTrace(e);
            try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        }
        return null;
    }

    private void saveData() {
        try (FileWriter writer = new FileWriter("./export.sql")) {
            writer.write(exporter.createPageExport(pages));
        } catch (Exception e) {
            log.error("Could not save statement file.");
        }
    }

    private void printStackTrace(Exception e) {
        for (StackTraceElement element : e.getStackTrace()) {
            log.error(element.toString());
        }
    }
}