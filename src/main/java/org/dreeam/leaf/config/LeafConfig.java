package org.dreeam.leaf.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LeafConfig {
    public static final Logger logger = LogManager.getLogger("LeafConfig");
    private static final File baseConfigFolder = new File("leaf_config");
    private static final File baseConfigFile = new File(baseConfigFolder, "leaf_global_config.toml");
    private static final Set<IConfigModule> allInstanced = new HashSet<>();
    private static CommentedFileConfig configFileInstance;

    public static void loadConfig() throws IOException {
        baseConfigFolder.mkdirs();

        if (!baseConfigFile.exists()) {
            baseConfigFile.createNewFile();
        }

        configFileInstance = CommentedFileConfig.ofConcurrent(baseConfigFile);

        configFileInstance.load();
        configFileInstance.getComment("""
                Leaf Config
                Github Repo: https://github.com/Winds-Studio/Leaf
                Discord: dreeam___ | QQ: 2682173972""");

        try {
            instanceAllModule();
            loadAllModules();
        } catch (Exception e) {
            logger.error("Failed to load config modules!", e);
        }

        configFileInstance.save();
    }

    private static void loadAllModules() throws IllegalAccessException {
        for (IConfigModule instanced : allInstanced) {
            loadForSingle(instanced);
        }
    }

    private static void instanceAllModule() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> clazz : getClasses("org.dreeam.leaf.config.modules")) {
            if (IConfigModule.class.isAssignableFrom(clazz)) {
                allInstanced.add((IConfigModule) clazz.getConstructor().newInstance());
            }
        }
    }

    private static void loadForSingle(@NotNull IConfigModule singleConfigModule) throws IllegalAccessException {
        final EnumConfigCategory category = singleConfigModule.getCategory();

        Field[] fields = singleConfigModule.getClass().getDeclaredFields();

        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                boolean skipLoad = field.getAnnotation(DoNotLoad.class) != null;
                ConfigInfo configInfo = field.getAnnotation(ConfigInfo.class);

                if (skipLoad || configInfo == null) {
                    continue;
                }

                final String fullConfigKeyName = category.getBaseKeyName() + "." + singleConfigModule.getBaseName() + "." + configInfo.baseName();

                field.setAccessible(true);
                final Object currentValue = field.get(null);

                if (!configFileInstance.contains(fullConfigKeyName)) {
                    if (currentValue == null) {
                        throw new UnsupportedOperationException("Config " + singleConfigModule.getBaseName() + "tried to add an null default value!");
                    }

                    final String comments = configInfo.comments();

                    if (!comments.isBlank()) {
                        configFileInstance.setComment(fullConfigKeyName, comments);
                    }

                    configFileInstance.add(fullConfigKeyName, currentValue);
                    continue;
                }

                final Object actuallyValue = configFileInstance.get(fullConfigKeyName);
                field.set(null, actuallyValue);
            }
        }

        singleConfigModule.onLoaded(configFileInstance);
    }

    public static @NotNull Set<Class<?>> getClasses(String pack) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        String packageDirName = pack.replace('.', '/');
        Enumeration<URL> dirs;

        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
                    findClassesInPackageByFile(pack, filePath, classes);
                } else if ("jar".equals(protocol)) {
                    JarFile jar;
                    try {
                        jar = ((JarURLConnection) url.openConnection()).getJarFile();
                        Enumeration<JarEntry> entries = jar.entries();
                        findClassesInPackageByJar(pack, entries, packageDirName, classes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return classes;
    }

    private static void findClassesInPackageByFile(String packageName, String packagePath, Set<Class<?>> classes) {
        File dir = new File(packagePath);

        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] dirfiles = dir.listFiles((file) -> file.isDirectory() || file.getName().endsWith(".class"));
        if (dirfiles != null) {
            for (File file : dirfiles) {
                if (file.isDirectory()) {
                    findClassesInPackageByFile(packageName + "." + file.getName(), file.getAbsolutePath(), classes);
                } else {
                    String className = file.getName().substring(0, file.getName().length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private static void findClassesInPackageByJar(String packageName, Enumeration<JarEntry> entries, String packageDirName, Set<Class<?>> classes) {
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.charAt(0) == '/') {
                name = name.substring(1);
            }

            if (name.startsWith(packageDirName)) {
                int idx = name.lastIndexOf('/');

                if (idx != -1) {
                    packageName = name.substring(0, idx).replace('/', '.');
                }

                if (name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.substring(packageName.length() + 1, name.length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
