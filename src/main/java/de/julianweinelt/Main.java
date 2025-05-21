package de.julianweinelt;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Gson GSON = new Gson();

    private static File folder = new File("C:\\Users\\weinelt\\Documents\\GAPTEQ_Repo\\Qconnect\\public");
    private static final List<GAPTEQPage> pages = new ArrayList<>();

    public static void main(String[] args) {
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

    private static void goThroughFiles(File file) {
        File[] files = file.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                goThroughFiles(f);
            } else {
                if (f.getName().endsWith(".meta")) {
                    String secondFileName = f.getName().replace(".meta", "");


                    try {
                        JsonReader reader = new JsonReader(new FileReader(f));
                        reader.setStrictness(Strictness.LENIENT);
                        //JsonElement obj = parser.parse(new FileReader(f));
                        JsonObject obj = ((JsonElement) GSON.fromJson(reader, JsonElement.class)).getAsJsonObject();

                        JsonObject jsonObject =  obj.getAsJsonObject();
                        if (!jsonObject.get("dataType").getAsString().equals("GT.Formy.Repository.Template.UnityPage")) continue;

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
                        pages.add(page);
                    } catch (Exception e) {
                        System.err.println("Page not loaded");
                        System.err.println(f.getAbsolutePath());
                        System.err.println(e.getMessage());
                        e.printStackTrace();
                        try {Thread.sleep(100);} catch (InterruptedException ignored) {}
                    }
                }
            }
        }
    }
}