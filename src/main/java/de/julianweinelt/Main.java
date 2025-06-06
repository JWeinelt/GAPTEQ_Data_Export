package de.julianweinelt;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import de.julianweinelt.data.ConfigManager;
import de.julianweinelt.data.Theme;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Main {
    @Getter
    private static Main instance;

    private final Gson GSON = new Gson();

    private File folder;
    private final ConcurrentLinkedDeque<GAPTEQPage> pages = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<GAPTEQStatement> statements = new ConcurrentLinkedDeque<>();

    private String pagesTableName;
    private String statementTableName;

    private boolean exportPages;
    private boolean exportStatements;
    private boolean exportConnections;

    private boolean useSmallBandwidth = false;
    private boolean enableMultiThreading = false;

    private int fileAmount = 0;

    private Exporter exporter = new SQLExporter("pages", "statements", false);

    private Taskbar taskbar;

    private final ConcurrentLinkedDeque<JCheckBox> optionButtons = new ConcurrentLinkedDeque<>();
    private boolean repoLoaded = false;

    private JLabel infoLabel;
    private JProgressBar progressBar;

    private JFrame mainFrame;

    @Getter
    private ConfigManager configManager;

    public static void main(String[] args) {
        instance = new Main();
        instance.start();
        instance.createGUI();
    }

    public void start() {
        configManager = new ConfigManager();
        configManager.loadConfig();
    }

    private void createGUI() {
        if (Taskbar.isTaskbarSupported()) taskbar = Taskbar.getTaskbar();
        switch (configManager.getConfiguration().getTheme()) {
            case LIGHT -> FlatLightLaf.setup();
            case DARK -> FlatDarkLaf.setup();
            case DARCULA -> FlatDarculaLaf.setup();
        }
        JFrame frame = new JFrame("GAPTEQ Data Exporter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(300, 200, 500, 350);

        frame.setLayout(new FlowLayout());

        frame.setJMenuBar(createMenuBar());

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(400, 30));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);


        JCheckBox smallBandWidth = new JCheckBox("Small bandwidth");
        smallBandWidth.setToolTipText("Will make a small delay between file queries (recommended for slow computers).");
        smallBandWidth.setEnabled(false);
        smallBandWidth.addActionListener(e -> useSmallBandwidth = smallBandWidth.isSelected());

        JCheckBox multiThreads = new JCheckBox("Use multiple threads");
        multiThreads.setEnabled(false);
        multiThreads.addActionListener(e -> enableMultiThreading = multiThreads.isSelected());

        JPanel options = new JPanel();
        JButton optionButton = new JButton("Select data");
        optionButton.addActionListener(e -> openSecondGUI());
        options.add(optionButton);

        JCheckBox createTablesBox = new JCheckBox("Attach CREATE TABLE script");
        createTablesBox.setEnabled(false);
        createTablesBox.addActionListener(e -> exporter.setCreateTables(createTablesBox.isSelected()));

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
            startButton.setEnabled(false);
            pages.clear();
            statements.clear();
            progressBar.setValue(0);
            progressBar.setMaximum(getFileAmount(folder));
            progressBar.setVisible(true);
            new Thread(() -> {
                try {
                    goThroughFiles(folder);
                    saveData();
                    taskbar.setWindowProgressState(mainFrame, Taskbar.State.OFF);
                    progressBar.setVisible(false);
                    startButton.setEnabled(true);
                    infoLabel.setText("All data has been exported. Pages: " + pages.size()
                            + " Statements: " + statements.size());
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
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                pagesTableName = pageTNameEnter.getText();
                exporter.setPagesTableName(pagesTableName);
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
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                statementTableName = statementsTNameEnter.getText();
                exporter.setStatementsTableName(statementTableName);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });


        JButton button = new JButton("Select folder");

        button.addActionListener(e -> {
            createRepoFileDialogue(frame, label, startButton, optionButton, exportOptions, pageTNameEnter,
                    statementsTNameEnter, smallBandWidth, createTablesBox);
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

        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(e->openHelpGUI());

        frame.add(button);
        frame.add(label);

        options.setPreferredSize(new Dimension(400, 40));
        frame.add(options);
        frame.add(createTablesBox);
        frame.add(smallBandWidth);
        frame.add(multiThreads);
        frame.add(export);

        frame.add(inputs);

        frame.add(startButton);

        JPanel infoPanel = new JPanel();
        infoPanel.add(infoLabel);


        frame.add(infoPanel);
        frame.add(progressBar);

        JPanel otherPanel = new JPanel();
        otherPanel.setPreferredSize(new Dimension(400, 30));
        otherPanel.add(helpButton);
        otherPanel.add(exitButton);

        frame.add(otherPanel);

        frame.setVisible(true);
        mainFrame = frame;
    }

    private void createRepoFileDialogue(JFrame frame, JLabel label, JButton startButton,
                                        JButton optionButton, JComboBox<String> exportOptions,
                                        JTextField pageTNameEnter, JTextField statementsTNameEnter,
                                        JCheckBox smallBandWidth, JCheckBox createTablesBox) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select a folder with a GAPTEQ repository");

        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            taskbar.setWindowProgressState(mainFrame, Taskbar.State.INDETERMINATE);
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
            optionButton.setEnabled(true);
            exportOptions.setEnabled(true);
            pageTNameEnter.setEnabled(true);
            statementsTNameEnter.setEnabled(true);
            smallBandWidth.setEnabled(true);
            createTablesBox.setEnabled(true);
            optionButtons.forEach(p->p.setEnabled(true));
            repoLoaded = true;
            //multiThreads.setEnabled(true); // A bit buggy
            taskbar.setWindowProgressState(mainFrame, Taskbar.State.OFF);
        }
    }

    private void openSecondGUI() {
        JFrame frame = new JFrame("Select data to be exported");

        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setLayout(new FlowLayout());
        frame.setBounds(500, 150, 300, 150);
        frame.setResizable(false);

        JCheckBox usePages = new JCheckBox("Export pages");
        usePages.setEnabled(repoLoaded);
        usePages.addActionListener(e -> exportPages = usePages.isSelected());
        JCheckBox useStatements = new JCheckBox("Export statements");
        useStatements.setEnabled(repoLoaded);
        useStatements.addActionListener(e -> exportStatements = useStatements.isSelected());
        JCheckBox usesConnections = new JCheckBox("Export connections");
        usesConnections.setEnabled(repoLoaded);
        usesConnections.addActionListener(e -> exportConnections = usesConnections.isSelected());

        JButton exitButton = new JButton("Apply");
        exitButton.addActionListener(e -> frame.setVisible(false));

        frame.add(usePages);
        frame.add(useStatements);
        frame.add(usesConnections);
        frame.add(exitButton);

        optionButtons.add(useStatements);
        optionButtons.add(usePages);
        optionButtons.add(usesConnections);

        frame.setVisible(true);
    }

    private void openHelpGUI() {
        JFrame frame = new JFrame("GAPTEQ Export guide");

        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setLayout(new FlowLayout());
        frame.setBounds(300, 100, 1000, 700);
        frame.setResizable(false);
        ImageIcon icon = new ImageIcon("src/main/resources/help1.png");

        JLabel label = new JLabel(icon);
        frame.add(label);
        frame.setVisible(true);
    }

    public void openSettings() {
        JFrame frame = new JFrame("GAPTEQ Export Settings");

        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setLayout(new FlowLayout());
        frame.setBounds(500, 150, 300, 150);
        frame.setResizable(false);

        String[] options = {"Light", "Dark", "Darcula"};
        JComboBox<String> dropdown = new JComboBox<>(options);dropdown.addActionListener(e -> {
            String selectedItem = (String) dropdown.getSelectedItem();
            if (selectedItem == null) return;
            Theme theme = Theme.valueOf(selectedItem.toUpperCase());
            configManager.getConfiguration().setTheme(theme);
            configManager.saveConfig();
            JOptionPane.showConfirmDialog(null, "After changing the theme GAPTEQ exporter has to be restarted.\nContinue?");
        });
        frame.add(dropdown);


        frame.setVisible(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem saveItm = new JMenuItem("Save");
        JMenuItem saveAsItm = new JMenuItem("Save as");
        JMenuItem openItm = new JMenuItem("Open");
        JMenuItem exitItm = new JMenuItem("Exit");
        fileMenu.add(saveItm);
        fileMenu.add(saveAsItm);
        fileMenu.add(openItm);
        fileMenu.add(exitItm);

        JMenu editMenu = new JMenu("Edit");
        JMenuItem loadRepoItm = new JMenuItem("Load repository");
        JMenuItem preferencesItm = new JMenuItem("Preferences");
        preferencesItm.addActionListener(e -> openSettings());
        JMenu exportMenu = new JMenu("Export...");
        JCheckBoxMenuItem exportPagesItm = new JCheckBoxMenuItem("Pages");
        JCheckBoxMenuItem exportStatementsItm = new JCheckBoxMenuItem("Statements");
        JCheckBoxMenuItem exportStatementTablesItm = new JCheckBoxMenuItem("Table References");
        JCheckBoxMenuItem exportConnectionsItm = new JCheckBoxMenuItem("Connections");
        exportMenu.add(exportPagesItm);
        exportMenu.add(exportStatementsItm);
        exportMenu.add(exportStatementTablesItm);
        exportMenu.add(exportConnectionsItm);
        editMenu.add(loadRepoItm);
        editMenu.add(preferencesItm);
        editMenu.add(exportMenu);

        bar.add(fileMenu);
        bar.add(editMenu);

        return bar;
    }

    private GAPTEQRepository loadRepo(File folder) {
        File toLoad = new File(folder, "Repository.json");
        if (!toLoad.exists()) {
            log.warn("Repo file does not exist.");
            return null;
        }

        try {
            JsonReader reader = new JsonReader(new FileReader(toLoad));
            reader.setStrictness(Strictness.LENIENT);
            JsonObject obj = ((JsonElement) GSON.fromJson(reader, JsonElement.class)).getAsJsonObject();

            JsonObject jsonObject =  obj.getAsJsonObject();
            try {Thread.sleep(1000);} catch (InterruptedException ignored) {}
            return new GAPTEQRepository(jsonObject.get("name").getAsString(), jsonObject.get("description").getAsString(),
                    folder);
        } catch (FileNotFoundException ignored) {}
        return null;
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
            if (useSmallBandwidth) try {Thread.sleep(10);} catch (InterruptedException ignored) {}
            infoLabel.setText("Loading " + f.getName() + "...");
            progressBar.setValue(progressBar.getValue() + 1);
            double progress = (progressBar.getValue() + 0.0) / progressBar.getMaximum();
            try {
                taskbar.setWindowProgressValue(mainFrame, (int) Math.ceil(progress * 100));
                taskbar.setWindowProgressState(mainFrame, Taskbar.State.NORMAL);
            } catch (UnsupportedOperationException ex) {
                log.error(ex.getMessage());
            }
            if (f.isDirectory()) {
                if (enableMultiThreading) {
                    new Thread(() -> {
                        try {
                            goThroughFiles(f);
                        } catch (FileNotFoundException e) {
                            printStackTrace(e);
                        }
                    }).start();
                } else
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
                    else if (jsonObject.get("dataType").getAsString()
                            .equals("GT.Formy.Repository.UserData.UserConnection") && exportConnections) {
                        log.debug("User Connection");
                    } else {
                        infoLabel.setText("Error: " + f.getName());
                        log.debug("File {} could not be identified as a page or statement.", f.getAbsolutePath());
                        log.debug("Detected type: {}", jsonObject.get("dataType").getAsString());
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
                log.debug("The file {} seems not to be a valid JSON page.", f.getName());
                log.debug(ie.getMessage());
            }
            log.debug("Page not loaded");
            log.debug(f.getAbsolutePath());
            log.debug(e.getMessage());
            printStackTrace(e);
            try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        }
        return null;
    }

    private GAPTEQStatement loadStatement(File f, JsonObject obj) {
        // obj is only the meta!
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
            String sqlStatement = o.get("sqlStatement").getAsString();

            Set<String> tables = extractTableNames(sqlStatement);
            stmt.setTables(tables);

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
                log.debug("The file {} seems not to be a valid JSON.", f.getName());
                log.debug(ie.getMessage());
            }
            log.debug("Statement not loaded");
            log.debug(f.getAbsolutePath());
            log.debug(e.getMessage());
            printStackTrace(e);
            try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        }
        return null;
    }

    private void saveData() {
        try (FileWriter writer = new FileWriter("./pages.sql")) {
            writer.write(exporter.createPageExport(pages));
        } catch (Exception e) {
            log.debug("Could not save pages file.");
            taskbar.setWindowProgressState(mainFrame, Taskbar.State.ERROR);
        }
        try (FileWriter writer = new FileWriter("./statements.sql")) {
            writer.write(exporter.createStatementExport(statements));
        } catch (Exception e) {
            log.debug("Could not save statement file.");
            taskbar.setWindowProgressState(mainFrame, Taskbar.State.ERROR);
        }
    }

    public java.util.List<String> parseSqlStatements(String content) {
        content = content.replaceAll("(?s)/\\*.*?\\*/", ""); // Blockkommentare
        content = content.replaceAll("(?m)--.*?$", "");      // Zeilenkommentare

        String[] rawStatements = content.split("(?<=;)|\\R{2,}");

        java.util.List<String> statements = new ArrayList<>();
        for (String stmt : rawStatements) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.endsWith(";")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
                }
                statements.add(trimmed);
            }
        }
        return statements;
    }

    public Set<String> extractTableNames(String sqlContent) {
        Set<String> tableNames = new HashSet<>();

        sqlContent = sqlContent.replaceAll("(?s)/\\*.*?\\*/", ""); // Blockkommentare
        sqlContent = sqlContent.replaceAll("(?m)--.*?$", "");      // Zeilenkommentare

        String content = sqlContent.toUpperCase();

        Pattern pattern = Pattern.compile(
                "\\b(FROM|JOIN|INTO|UPDATE|DELETE FROM|TRUNCATE TABLE|CREATE TABLE|ALTER TABLE|DROP TABLE)\\s+`?([a-zA-Z0-9_.]+)`?",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sqlContent);
        while (matcher.find()) {
            String table = matcher.group(2);
            tableNames.add(table);
        }

        return tableNames;
    }

    private void printStackTrace(Exception e) {
        for (StackTraceElement element : e.getStackTrace()) {
            log.debug(element.toString());
        }
    }
}