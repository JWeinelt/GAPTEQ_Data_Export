package de.julianweinelt;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import de.julianweinelt.obj.GAPTEQPage;
import de.julianweinelt.obj.GAPTEQStatement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Gson GSON = new Gson();

    private static File folder = new File("C:\\Users\\weinelt\\Documents\\GAPTEQ_Repo\\Qconnect\\public");
    private static final List<GAPTEQPage> pages = new ArrayList<>();
    private static final List<GAPTEQStatement> statements = new ArrayList<>();

    private static String pagesTableName;
    private static String statementTableName;

    public static void main(String[] args) throws FileNotFoundException {
        System.out.println(folder.exists());
        goThroughFiles(folder);

        System.out.println("Found " + pages.size() + " pages in total.");
        StringBuilder statement = new StringBuilder();
        for (GAPTEQPage p : pages) {
            statement.append("INSERT INTO QGAPTEQ_Pages (creator, createDate, pageName, modifier, modifierDate, pageTemplate) VALUES('")
                    .append(p.getCreatedBy())
                    .append("', '").append(p.getCreatedAt())
                    .append("', '").append(p.getPageName())
                    .append("', '").append(p.getModifiedBy())
                    .append("', '").append(p.getModifiedAt())
                    .append("', '").append(p.getPageTemplate()).append("');").append("\n");
        }

        try (FileWriter writer = new FileWriter("./statement.sql")) {
            writer.write(statement.toString());
        } catch (Exception e) {
            System.err.println("Could not save statement file.");
        }
    }

    private static void goThroughFiles(File file) throws FileNotFoundException {
        File[] files = file.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                goThroughFiles(f);
            } else {
                if (f.getName().endsWith(".meta")) {

                    JsonReader reader = new JsonReader(new FileReader(f));
                    reader.setStrictness(Strictness.LENIENT);
                    JsonObject obj = ((JsonElement) GSON.fromJson(reader, JsonElement.class)).getAsJsonObject();

                    JsonObject jsonObject =  obj.getAsJsonObject();
                    if (jsonObject.get("dataType").getAsString().equals("GT.Formy.Repository.Template.UnityPage")) pages.add(loadPage(f, jsonObject));
                    else if (jsonObject.get("dataType").getAsString().equals("GT.Formy.Repository.UserData.UserStatementDefinition")) statements.add(loadStatement(f, jsonObject));
                    else {
                        System.err.println("File " + f.getAbsolutePath() + " could not be identified as a page or statement.");
                        System.err.println("Detected type: " + jsonObject.get("dataType").getAsString());
                    }
                }
            }
        }
    }

    private static GAPTEQPage loadPage(File f, JsonObject jsonObject) {
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
                System.err.println("The file " + f.getName() + " seems not to be a valid JSON.");
                System.err.println(ie.getMessage());
            }
            System.err.println("Page not loaded");
            System.err.println(f.getAbsolutePath());
            System.err.println(e.getMessage());
            e.printStackTrace();
            try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        }
        return null;
    }

    private static GAPTEQStatement loadStatement(File f, JsonObject obj) {
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
                System.err.println("The file " + f.getName() + " seems not to be a valid JSON.");
                System.err.println(ie.getMessage());
            }
            System.err.println("Statement not loaded");
            System.err.println(f.getAbsolutePath());
            System.err.println(e.getMessage());
            e.printStackTrace();
            try {Thread.sleep(100);} catch (InterruptedException ignored) {}
        }
        return null;
    }
}