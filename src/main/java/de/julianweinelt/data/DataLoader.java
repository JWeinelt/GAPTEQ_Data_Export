package de.julianweinelt.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import de.julianweinelt.obj.GAPTEQDBConnection;
import de.julianweinelt.obj.GAPTEQPage;
import de.julianweinelt.obj.GAPTEQRepository;
import de.julianweinelt.obj.GAPTEQStatement;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.concurrent.ConcurrentLinkedDeque;

@Getter
@Setter
@Slf4j
public class DataLoader {
    private final Gson GSON = new Gson();

    private final ConcurrentLinkedDeque<GAPTEQPage> pages = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<GAPTEQStatement> statements = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<GAPTEQDBConnection> connections = new ConcurrentLinkedDeque<>();

    private boolean exportPages;
    private boolean exportStatements;
    private boolean exportConnections;

    private boolean useSmallBandwidth = false;
    private boolean enableMultiThreading = false;

    private int fileAmount = 0;

    private final JProgressBar progressBar;

    private final JFrame mainFrame;
    private final JLabel infoLabel;
    private final Taskbar taskbar;

    public DataLoader(JProgressBar progressBar, JFrame mainFrame, JLabel infoLabel, Taskbar taskbar) {
        this.progressBar = progressBar;
        this.mainFrame = mainFrame;
        this.infoLabel = infoLabel;
        this.taskbar = taskbar;
    }



    public GAPTEQRepository loadRepo(File folder) {
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

    public int getFileAmount(File file) {
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

    public void goThroughFiles(File file) throws FileNotFoundException {
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

    private void printStackTrace(Exception e) {
        for (StackTraceElement element : e.getStackTrace()) {
            log.debug(element.toString());
        }
    }
}
