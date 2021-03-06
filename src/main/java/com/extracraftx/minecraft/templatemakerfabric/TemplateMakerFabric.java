package com.extracraftx.minecraft.templatemakerfabric;

import static com.extracraftx.minecraft.templatemakerfabric.util.Util.contains;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.function.Consumer;

import com.extracraftx.minecraft.templatemakerfabric.data.DataProvider;
import com.extracraftx.minecraft.templatemakerfabric.fabric.FabricMod;
import com.extracraftx.minecraft.templatemakerfabric.schema.FabricModJsonSchema;
import com.extracraftx.minecraft.templatemakerfabric.schema.MixinsJsonSchema;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateNotFoundException;

public class TemplateMakerFabric {

    private Configuration cfg;
    private Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public TemplateMakerFabric() {
        cfg = new Configuration(Configuration.VERSION_2_3_29);
        cfg.setClassForTemplateLoading(getClass(), "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
    }

    public void outputMod(FabricMod mod, Path dir) throws FileNotFoundException, IOException, Exception {
        outputMod(mod, dir, null, null);
    }

    public void outputMod(FabricMod mod, Path dir, Consumer<String> fileStartCallback, Consumer<String> fileEndCallback)
            throws FileNotFoundException, IOException, Exception {
        copyFile(mod, dir, fileStartCallback, fileEndCallback, ".gitignore.template", ".gitignore");
        copyFile(mod, dir, fileStartCallback, fileEndCallback, "settings.gradle", "settings.gradle");

        String gradleBase = "gradle" + mod.getLoomVersion().gradle + "/";
        copyFileExecutable(mod, dir, fileStartCallback, fileEndCallback, gradleBase + "gradlew", "gradlew");
        copyFile(mod, dir, fileStartCallback, fileEndCallback, gradleBase + "gradlew.bat", "gradlew.bat");
        copyFile(mod, dir, fileStartCallback, fileEndCallback, gradleBase + "gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.jar");
        copyFile(mod, dir, fileStartCallback, fileEndCallback, gradleBase + "gradle-wrapper.properties",
                "gradle/wrapper/gradle-wrapper.properties");

        outputFile(mod, dir, fileStartCallback, fileEndCallback, "gradle.properties", "gradle.properties");
        outputFile(mod, dir, fileStartCallback, fileEndCallback, "build.gradle", "build.gradle");

        outputFile(mod, dir, fileStartCallback, fileEndCallback, "init.java.template",
                "src/main/java/" + String.join("/", mod.getMainPackage()) + "/" + mod.getMainClass() + ".java");
        copyFile(mod, dir, fileStartCallback, fileEndCallback, "cobblestone.png",
                "src/main/resources/assets/" + mod.getModId() + "/icon.png");

        outputFabricModJson(mod, dir, fileStartCallback, fileEndCallback);
        if(mod.isMixin())
            outputMixinsJson(mod, dir, fileStartCallback, fileEndCallback);

        if(contains(DataProvider.LICENSES, mod.getLicense()))
            outputFile(mod, dir, fileStartCallback, fileEndCallback, "licenses/"+mod.getLicense().value+".txt", "LICENSE");
    }

    private void outputFabricModJson(FabricMod mod, Path dir, Consumer<String> fileStartCallback,
            Consumer<String> fileEndCallback) throws IOException {
        FabricModJsonSchema obj = new FabricModJsonSchema();
        obj.id = mod.getModId();
        obj.version = "${version}";
        obj.name = mod.getModName();
        obj.description = mod.getModDescription();
        obj.authors.add(mod.getAuthor());
        if (mod.getSources() != null) {
            obj.contact.sources = mod.getSources().toString();
        }
        if (mod.getHomepage() != null) {
            obj.contact.homepage = mod.getHomepage().toString();
        }
        obj.license = mod.getLicense().value;
        obj.icon = "assets/" + mod.getModId() + "/icon.png";
        obj.environment = "*";
        obj.entrypoints.main.add(String.join(".", mod.getMainPackage()) + "." + mod.getMainClass());
        if (mod.isMixin()) {
            obj.mixins.add(mod.getModId() + ".mixins.json");
        }
        obj.depends.put("fabricloader", ">=0.4.0");
        if (mod.isFabricApi()) {
            obj.depends.put("fabric", "*");
        }

        outputJson(obj, dir, fileStartCallback, fileEndCallback, "src/main/resources/fabric.mod.json");
    }

    private void outputMixinsJson(FabricMod mod, Path dir, Consumer<String> fileStartCallback,
            Consumer<String> fileEndCallback) throws IOException {
        MixinsJsonSchema obj = new MixinsJsonSchema();
        obj.pkg = String.join(".", mod.getMainPackage()) + ".mixin";

        outputJson(obj, dir, fileStartCallback, fileEndCallback, "src/main/resources/"+mod.getModId()+".mixins.json");
    }

    private void copyFile(FabricMod mod, Path dir, Consumer<String> fileStartCallback, Consumer<String> fileEndCallback,
            String filePath, String outputPath) throws IOException {
        if (fileStartCallback != null)
            fileStartCallback.accept(outputPath);

        Path output = dir.resolve(outputPath);
        if(output.toFile().getParentFile() != null)
            output.toFile().getParentFile().mkdirs();

        Files.copy(getClass().getResourceAsStream("/" + filePath), output);

        if (fileEndCallback != null)
            fileEndCallback.accept(outputPath);
    }

    private void copyFileExecutable(FabricMod mod, Path dir, Consumer<String> fileStartCallback,
            Consumer<String> fileEndCallback, String filePath, String outputPath) throws IOException {
        copyFile(mod, dir, fileStartCallback, fileEndCallback, filePath, outputPath);
        try{
            Files.setPosixFilePermissions(dir.resolve(outputPath), PosixFilePermissions.fromString("rwxr-xr-x"));
        }catch(UnsupportedOperationException e){}
    }

    private void outputFile(FabricMod mod, Path dir, Consumer<String> fileStartCallback,
            Consumer<String> fileEndCallback, String templatePath, String outputPath) throws TemplateNotFoundException,
            MalformedTemplateNameException, ParseException, IOException, TemplateException {
        if (fileStartCallback != null)
            fileStartCallback.accept(outputPath);

        File output = dir.resolve(outputPath).toFile();
        if(output.getParentFile() != null)
            output.getParentFile().mkdirs();

        Template t = cfg.getTemplate(templatePath);
        t.process(mod, new FileWriter(output));

        if (fileEndCallback != null)
            fileEndCallback.accept(outputPath);
    }

    private void outputJson(Object obj, Path dir, Consumer<String> fileStartCallback, Consumer<String> fileEndCallback,
            String outputPath) throws IOException {
        if (fileStartCallback != null)
            fileStartCallback.accept(outputPath);

        File output = dir.resolve(outputPath).toFile();
        if(output.getParentFile() != null)
            	output.getParentFile().mkdirs();

        FileWriter fw = new FileWriter(output);
        fw.append(gson.toJson(obj));
        fw.close();

        if (fileEndCallback != null)
            fileEndCallback.accept(outputPath);
    }

}