package com.weirddev.testme.intellij.ui.template;

import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.DefaultTemplate;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.project.ProjectKt;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.MultiMap;
import com.weirddev.testme.intellij.utils.ResourceLoader;
import com.weirddev.testme.intellij.utils.UrlClassLoaderUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;

/**
 * Serves as a container for all existing template manager types and loads corresponding templates upon creation (at construction time).
 *
 * @author Rustam Vishnyakov
 */
class FileTemplatesLoader {
    private static final Logger LOG = Logger.getInstance(FileTemplatesLoader.class);

    static final String TEMPLATES_DIR = "fileTemplates";
    private static final String DEFAULT_TEMPLATES_ROOT = TEMPLATES_DIR;
    private static final String DESCRIPTION_FILE_EXTENSION = "html";
    private static final String DESCRIPTION_EXTENSION_SUFFIX = "." + DESCRIPTION_FILE_EXTENSION;
    private static final String DEFAULT_TEMPLATE_DESCRIPTION_FILENAME = "default.html";

    private final FTManager myTestTemplatesManager;
    private final FTManager myIncludesManager;// testMeIncludes

    private final FTManager[] myAllManagers;

    private static final String TESTS_DIR = "testMeTests";
    public static final String INCLUDES_DIR = "testMeIncludes";

    private final URL myDefaultTemplateDescription;
    private final URL myDefaultIncludeDescription;

    FileTemplatesLoader(@Nullable Project project) {
        Path configDir = Paths.get(project == null || project.isDefault()
                ? PathManager.getConfigPath()
                : UriUtil.trimTrailingSlashes(Objects.requireNonNull(Objects.requireNonNull(ProjectKt.getStateStore(project).getDirectoryStorePath()).toString())), TEMPLATES_DIR);

        myTestTemplatesManager = new FTManager(TestMeTemplateManager.TEST_TEMPLATES_CATEGORY, configDir.resolve(TESTS_DIR), true);
        myIncludesManager = new FTManager(FileTemplateManager.INCLUDES_TEMPLATES_CATEGORY, configDir.resolve(INCLUDES_DIR));
        myAllManagers = new FTManager[]{
                myTestTemplatesManager,
                myIncludesManager,
        };

        Map<FTManager, String> managerToPrefix = new LinkedHashMap<>();
        for (FTManager manager : myAllManagers) {
            Path managerRoot = manager.getConfigRoot();
            String relativePath = configDir.equals(managerRoot) ? "" : FileUtilRt.toSystemIndependentName(configDir.relativize(managerRoot).toString()) + "/";
            managerToPrefix.put(manager, relativePath);
        }

        FileTemplateLoadResult result = loadDefaultTemplates(new ArrayList<>(managerToPrefix.values()));
        myDefaultTemplateDescription = result.getDefaultTemplateDescription();
        myDefaultIncludeDescription = result.getDefaultIncludeDescription();
        for (FTManager manager : myAllManagers) {
            manager.setDefaultTemplates(result.getResult().get(managerToPrefix.get(manager)));
            manager.loadCustomizedContent();
        }
    }

    private Path resolveInternalTemplatesRoot() {
        try {
            return ResourceLoader.getPath("/" + TEMPLATES_DIR + "/" + TESTS_DIR);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    FTManager[] getAllManagers() {
        return myAllManagers;
    }

    @NotNull
    FTManager getInternalTestTemplatesManager() {
        return new FTManager(myTestTemplatesManager);
    }

    @NotNull
    FTManager getCustomTestTemplatesManager() {
        return new FTManager(myTestTemplatesManager);
    }

    @NotNull
    FTManager getPatternsManager() {
        return new FTManager(myIncludesManager);
    }

    URL getDefaultTemplateDescription() {
        return myDefaultTemplateDescription;
    }

    URL getDefaultIncludeDescription() {
        return myDefaultIncludeDescription;
    }

    @NotNull
    private static FileTemplateLoadResult loadDefaultTemplates(@NotNull List<String> prefixes) {
        FileTemplateLoadResult result = new FileTemplateLoadResult(new MultiMap<>());
        Set<URL> processedUrls = new THashSet<>();
        Set<ClassLoader> processedLoaders = new HashSet<>();
        IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
        for (PluginDescriptor plugin : plugins) {
            if (plugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl) plugin).isEnabled()) {
                final ClassLoader loader = plugin.getPluginClassLoader();
                if (loader instanceof PluginClassLoader && ((PluginClassLoader) loader).getUrls().isEmpty() ||
                        !processedLoaders.add(loader)) {
                    continue; // test or development mode, when IDEA_CORE's loader contains all the classpath
                }
                try {
                    final Enumeration<URL> systemResources = loader.getResources(DEFAULT_TEMPLATES_ROOT);
                    if (systemResources.hasMoreElements()) {
                        while (systemResources.hasMoreElements()) {
                            final URL url = systemResources.nextElement();
                            if (!processedUrls.add(url)) {
                                continue;
                            }
                            loadDefaultsFromRoot(url, prefixes, result);
                        }
                    }
                } catch (IOException e) {
                    LOG.error(e);
                }
            }
        }
        return result;
    }

    private static void loadDefaultsFromRoot(@NotNull URL root, @NotNull List<String> prefixes, @NotNull FileTemplateLoadResult result) throws IOException {
        final List<String> children = UrlUtil.getChildrenRelativePaths(root);
        if (children.isEmpty()) {
            return;
        }

        final Set<String> descriptionPaths = new HashSet<>();
        for (String path : children) {
            if (path.equals(FileTemplatesLoader.TESTS_DIR + "/" + DEFAULT_TEMPLATE_DESCRIPTION_FILENAME)) {
                result.setDefaultTemplateDescription(toFullPath(root, path));
            } else if (path.equals(FileTemplatesLoader.INCLUDES_DIR + "/" + DEFAULT_TEMPLATE_DESCRIPTION_FILENAME)) {
                result.setDefaultIncludeDescription(toFullPath(root, path));
            } else if (path.endsWith(DESCRIPTION_EXTENSION_SUFFIX)) {
                descriptionPaths.add(path);
            }
        }

        for (final String path : children) {
            if (!path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
                continue;
            }

            for (String prefix : prefixes) {
                if (!matchesPrefix(path, prefix)) {
                    continue;
                }

                String filename = path.substring(prefix.length(), path.length() - FTManager.TEMPLATE_EXTENSION_SUFFIX.length());
                String extension = FileUtilRt.getExtension(filename);
                String templateName = filename.substring(0, filename.length() - extension.length() - 1);
                URL templateUrl = toFullPath(root, path);
                String descriptionPath = getDescriptionPath(prefix, templateName, extension, descriptionPaths);
                URL descriptionUrl = descriptionPath == null ? null : toFullPath(root, descriptionPath);
                assert templateUrl != null;
                result.getResult().putValue(prefix, new DefaultTemplate(templateName, extension, templateUrl, descriptionUrl));
                // FTManagers loop
                break;
            }
        }
    }

    private static URL toFullPath(@NotNull URL root, String path) throws MalformedURLException {
        return UrlClassLoaderUtils.internProtocol(new URL(UriUtil.trimTrailingSlashes(root.toExternalForm()) + "/" + path));
    }

    private static boolean matchesPrefix(@NotNull String path, @NotNull String prefix) {
        if (prefix.isEmpty()) {
            return path.indexOf('/') == -1;
        }
        return FileUtil.startsWith(path, prefix) && path.indexOf('/', prefix.length()) == -1;
    }

    // Example: templateName="NewClass"   templateExtension="java"
    @Nullable
    private static String getDescriptionPath(@NotNull String pathPrefix,
                                             @NotNull String templateName,
                                             @NotNull String templateExtension,
                                             @NotNull Set<String> descriptionPaths) {
        final Locale locale = Locale.getDefault();

        String descName = MessageFormat.format("{0}.{1}_{2}_{3}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension,
                locale.getLanguage(), locale.getCountry());
        String descPath = pathPrefix.isEmpty() ? descName : pathPrefix + descName;
        if (descriptionPaths.contains(descPath)) {
            return descPath;
        }

        descName = MessageFormat.format("{0}.{1}_{2}" + DESCRIPTION_EXTENSION_SUFFIX, templateName, templateExtension, locale.getLanguage());
        descPath = pathPrefix.isEmpty() ? descName : pathPrefix + descName;
        if (descriptionPaths.contains(descPath)) {
            return descPath;
        }

        descName = templateName + "." + templateExtension + DESCRIPTION_EXTENSION_SUFFIX;
        descPath = pathPrefix.isEmpty() ? descName : pathPrefix + descName;
        if (descriptionPaths.contains(descPath)) {
            return descPath;
        }
        return null;
    }
}
