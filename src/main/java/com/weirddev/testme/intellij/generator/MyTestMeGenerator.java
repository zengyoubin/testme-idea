package com.weirddev.testme.intellij.generator;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.createTest.JavaTestGenerator;
import com.intellij.util.IncorrectOperationException;
import com.weirddev.testme.intellij.action.before.CreateTestBeforeAction;
import com.weirddev.testme.intellij.action.before.CreateTestBeforeDialog;
import com.weirddev.testme.intellij.template.FileTemplateContext;
import com.weirddev.testme.intellij.template.context.TestMeTemplateParams;
import com.weirddev.testme.intellij.ui.template.TestMeTemplateManager;
import org.apache.velocity.app.Velocity;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Date: 10/19/2016
 *
 * @author Yaron Yamin
 * @see JavaTestGenerator
 */
public class MyTestMeGenerator {
    private final TestClassElementsLocator testClassElementsLocator;
    private final TestTemplateContextBuilder testTemplateContextBuilder;
    private final CodeRefactorUtil codeRefactorUtil;

    private final Set<String> selectMethods = new HashSet<>();
    private static final Logger LOG = Logger.getInstance(MyTestMeGenerator.class.getName());

    public MyTestMeGenerator() {
        this(new TestClassElementsLocator(), new TestTemplateContextBuilder(new MockBuilderFactory()), new CodeRefactorUtil());
    }

    MyTestMeGenerator(TestClassElementsLocator testClassElementsLocator, TestTemplateContextBuilder testTemplateContextBuilder, CodeRefactorUtil codeRefactorUtil) {
        this.testClassElementsLocator = testClassElementsLocator;
        this.testTemplateContextBuilder = testTemplateContextBuilder;
        this.codeRefactorUtil = codeRefactorUtil;
    }

    public PsiElement generateTest(final FileTemplateContext context) {
        final Project project = context.getProject();

        Set<String> existsTestMethods = findExistsTestMethods(context.getTargetDirectory(), context.getTargetClass());
        CreateTestBeforeDialog testDialog = new CreateTestBeforeAction().createTestDialog(context.getProject(), context.getSrcModule(), context.getSrcClass(), context.getTargetPackage(),existsTestMethods);
        boolean get = testDialog.showAndGet();
        if (!get) {
            return null;
        }
        Collection<MemberInfo> selectedMethods = testDialog.getSelectedMethods();
        selectedMethods.stream()
                .map(MemberInfoBase::getDisplayName)
                .map(s -> {
                    int index = s.indexOf("(");
                    return s.substring(0, index);
                })
                .forEach(selectMethods::add);

        // todo
        return PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable<PsiElement>() {
            public PsiElement compute() {
                return ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
                    public PsiElement compute() {
                        try {
                            final long start = new Date().getTime();
                            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
                            PsiFile targetClass = createTestClass(context);
                            if (targetClass == null) {
                                return null;
                            }
                            try {
                                PsiElement psiElement = resolveEmbeddedClass(targetClass);
                                if (psiElement instanceof PsiClass) {
                                    final PsiElement optimalCursorLocation = testClassElementsLocator.findOptimalCursorLocation((PsiClass) psiElement);
                                    if (optimalCursorLocation != null) {
                                        CodeInsightUtil.positionCursor(project, targetClass, optimalCursorLocation);
                                    }
                                } else {
                                    CodeInsightUtil.positionCursor(project, targetClass, psiElement);
                                }
                            } catch (Throwable e) {
                                LOG.warn("unable to locate optimal cursor location post test generation", e);
//                                new OpenFileDescriptor(project, targetClass.getContainingFile().getVirtualFile()).navigate(true);
                            }
                            LOG.debug("Done generating class " + context.getTargetClass() + " in " + (new Date().getTime() - start) + " millis");
                            return targetClass;
                        } catch (IncorrectOperationException e) {
                            showErrorLater(project, context.getTargetClass());
                            return null;
                        }
                    }
                });
            }
        });
    }

    @Nullable
    private PsiFile createTestClass(FileTemplateContext context) {
        final PsiDirectory targetDirectory = context.getTargetDirectory();


        final PsiFile classFromTemplate = createTestClassFromCodeTemplate(context, targetDirectory);
        if (classFromTemplate != null) {
            return classFromTemplate;
        }
        return JavaDirectoryService.getInstance().createClass(targetDirectory, context.getTargetClass()).getContainingFile();
    }

    private PsiFile createTestClassFromCodeTemplate(final FileTemplateContext context, final PsiDirectory targetDirectory) {
        FileTemplateManager fileTemplateManager = TestMeTemplateManager.getInstance(targetDirectory.getProject());
        Map<String, Object> templateCtxtParams = testTemplateContextBuilder.build(context, fileTemplateManager.getDefaultProperties());
        templateCtxtParams.put(TestMeTemplateParams.SELECT_METHODS, selectMethods);
        try {
            Velocity.setProperty(Velocity.VM_MAX_DEPTH, 200);
            final long startGeneration = new Date().getTime();
            final PsiElement psiElement = new MySpockTestGenerator(context, templateCtxtParams).createFromTemplate(targetDirectory);
            // LOG.debug("Done generating PsiElement from template "+codeTemplate.getName()+" in "+(new Date().getTime()-startGeneration)+" millis");
            final long startReformating = new Date().getTime();
            final PsiElement resolvedPsiElement = resolveEmbeddedClass(psiElement);
            final PsiFile psiFile = resolvedPsiElement instanceof PsiFile ? (PsiFile) resolvedPsiElement : resolvedPsiElement.getContainingFile();
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(targetDirectory.getProject());
            if (context.getFileTemplateConfig().isOptimizeImports()) {
                codeStyleManager.optimizeImports(psiFile);
            }
            if (context.getFileTemplateConfig().isReplaceFqn()) {
                codeStyleManager.shortenClassReferences(psiFile);
            }
            codeRefactorUtil.uncommentImports(psiFile, context.getProject());
            if (context.getFileTemplateConfig().isReformatCode()) {
                final PsiFile containingFile = psiFile;
                final TextRange textRange = containingFile.getTextRange();
                CodeStyleManager.getInstance(context.getProject()).reformatText(containingFile, textRange.getStartOffset(), textRange.getEndOffset());
            }
            LOG.debug("Done reformatting generated PsiClass in " + (new Date().getTime() - startReformating) + " millis");
            return psiFile;
        } catch (Exception e) {
            LOG.error("error generating test class", e);
            return null;
        }
    }

//    private void flushOperations(FileTemplateContext context, PsiClass psiClass) {
//        final Document document = psiClass.getContainingFile().getViewProvider().getDocument();
//        if (document != null) {
//            PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(document);
//        }
//    }

    private PsiElement resolveEmbeddedClass(PsiElement psiElement) {
        // Important for Groovy support - expecting org.jetbrains.plugins.groovy.lang.psi.GroovyFile. see org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase.createClassByType
        final PsiElement resolveEmbeddedClass = resolveEmbeddedClassRecursive(psiElement, 2);
        if (resolveEmbeddedClass == null) {
            return psiElement;
        } else {
            return resolveEmbeddedClass;
        }
    }

    @Nullable
    private PsiElement resolveEmbeddedClassRecursive(PsiElement psiElement, int recursionLevel) {
        if (psiElement instanceof PsiClass || psiElement != null && psiElement.getClass().getCanonicalName().equals("org.jetbrains.kotlin.psi.KtClass")) {
            return psiElement;
        } else if (recursionLevel <= 0) {
            return null;
        } else {
            final PsiElement[] psiElementChildren = psiElement.getChildren();
            for (PsiElement psiElementChild : psiElementChildren) {
                final PsiElement resolvedPsiClass = resolveEmbeddedClassRecursive(psiElementChild, recursionLevel - 1);
                if (resolvedPsiClass != null) {
                    return resolvedPsiClass;
                }
            }
        }
        return null;
    }

    static void showErrorLater(final Project project, final String targetClassName) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                Messages.showErrorDialog(project,
                        CodeInsightBundle.message("intention.error.cannot.create.class.message", targetClassName),
                        CodeInsightBundle.message("intention.error.cannot.create.class.title"));
            }
        });
    }

    @Override
    public String toString() {
        return CodeInsightBundle.message("intention.create.test.dialog.java");
    }

    @Nullable
    private Set<String> findExistsTestMethods(PsiDirectory targetDirectory, String testClassName) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
        if (aPackage == null) {
            return Collections.emptySet();
        }

        final GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(targetDirectory, false);
        final PsiClass[] classes = aPackage.findClassByShortName(testClassName, scope);
        if (classes.length <= 0) {
            return Collections.emptySet();
        }
        if (!FileModificationService.getInstance().preparePsiElementForWrite(classes[0])) {
            return Collections.emptySet();
        }
       return  Arrays.stream(classes[0].getMethods())
                .map(PsiMethod::getName)
                .collect(Collectors.toSet());
    }
}
