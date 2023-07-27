package com.weirddev.testme.intellij.generator;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.IncorrectOperationException;
import com.weirddev.testme.intellij.template.FileTemplateContext;
import com.weirddev.testme.intellij.template.context.*;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.*;

/**
 * @author yb_zeng
 * @date 2023-07-25
 */
public class MySpockTestGenerator {
    private FileTemplateContext context;
    private final MockitoMockBuilder mockitoMockBuilder;
    private final Type testClass;
    private final GroovyPsiElementFactory factory;
    private final boolean hasMocks;
    private final Set<String> selectMethods;
    private final TestSubjectInspector testSubjectInspector;
    private final TestBuilder testBuilder;
    private final Project project;

    public MySpockTestGenerator(FileTemplateContext context, Map<String, Object> templateCtxtParams) {
        this.context = context;
        this.project = context.getProject();
        this.factory = GroovyPsiElementFactory.getInstance(context.getProject());
        this.mockitoMockBuilder = (MockitoMockBuilder) templateCtxtParams.get(TestMeTemplateParams.MockitoMockBuilder);
        this.testClass = (Type) templateCtxtParams.get(TestMeTemplateParams.TESTED_CLASS);
        this.selectMethods = (Set<String>) templateCtxtParams.get(TestMeTemplateParams.SELECT_METHODS);
        this.testSubjectInspector = (TestSubjectInspector) templateCtxtParams.get(TestMeTemplateParams.TestSubjectUtils);
        this.testBuilder = (TestBuilder) templateCtxtParams.get(TestMeTemplateParams.TestBuilder);
        this.hasMocks = mockitoMockBuilder.hasMockable(testClass.getFields());
    }

    public PsiElement createFromTemplate(PsiDirectory targetDirectory) {
        PsiClass srcClass = context.getSrcClass();

        GrTypeDefinition targetClass = generateClass(targetDirectory, project);
        if (targetClass == null) return null;

        addSuperClass(targetClass, project, "spock.lang.Specification");

        if (!containsField(targetClass, "testObj")) {
            generateField(targetClass, "def testObj = new %s()".formatted(srcClass.getQualifiedName()));
        }
        List<String> mockedFields = grRenderMockedFields(targetClass);
        generateSetupMethod(targetClass, mockedFields);

        generateTestMethods(targetClass);

        return targetClass;

    }

    @Nullable
    private GrTypeDefinition generateClass(PsiDirectory targetDirectory, Project project) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
        if (aPackage != null) {
            final GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(targetDirectory, false);
            final PsiClass[] classes = aPackage.findClassByShortName(context.getTargetClass(), scope);
            if (classes.length > 0) {
                if (!FileModificationService.getInstance().preparePsiElementForWrite(classes[0])) {
                    return null;
                }
                return (GrTypeDefinition) classes[0];
            }
        }
        return CreateClassActionBase.createClassByType(
                targetDirectory,
                context.getTargetClass(),
                PsiManager.getInstance(project),
                null,
                GroovyTemplates.GROOVY_CLASS, true);
    }

    private void generateTestMethods(GrTypeDefinition targetClass) {
        List<Method> methods = testClass.getMethods();
        for (Method method : methods) {
            if (!selectMethods.contains(method.getName())) {
                continue;
            }
            if (method.isInherited()) {
                continue;
            }
            if (!testSubjectInspector.shouldBeTested(method)) {
                return;
            }
            try {
                generateTestMethod(targetClass, method);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

    }

    private void generateTestMethod(GrTypeDefinition targetClass, Method method) throws Exception {
        ParameterizedTestComponents paraTestComponents = testBuilder.buildPrameterizedTestComponents(method, CommonConstant.grReplacementTypesStatic, CommonConstant.grReplacementTypesStatic, CommonConstant.grDefaultTypeValues);
        StringBuilder sb = new StringBuilder();
        sb.append("@spock.lang.Unroll\n");
        sb.append("def \"").append(method.getName()).append("Test\"(){\n");
        boolean shouldSpy = testSubjectInspector.shouldSpy(method);
        if (mockitoMockBuilder.shouldStub(method, testClass.getFields()) || shouldSpy) {
            sb.append("given: \"设定相关方法入参\"\n");
            sb.append("and: \"Mock相关接口返回\"\n");
            for (Field field : testClass.getFields()) {
                if (!mockitoMockBuilder.isMockable(field)) {
                    continue;
                }
                if (field.getType().getAnnotationQualifiedNames().contains("org.mapstruct.Mapper")) {
                    continue;
                }
                for (Method fieldMethod : field.getType().getMethods()) {
                    if (method.getReturnType() != null && !fieldMethod.getReturnType().getName().equals("void") && testSubjectInspector.isMethodCalledIgnore2P(fieldMethod, method)) {
                        sb.append(field.getName())
                                .append(".")
                                .append(fieldMethod.getName())
                                .append("(")
                                .append(mockitoMockBuilder.buildMockArgsMatchers(fieldMethod.getMethodParams(), "Groovy"))
                                .append(") >>")
                                .append(testBuilder.renderReturnParam(method, fieldMethod.getReturnType(), fieldMethod.getName() + "Response", CommonConstant.grReplacementTypesStatic, CommonConstant.grDefaultTypeValues))
                                .append("\n");
                    }
                }
            }
            sb.append("\n");
            if (shouldSpy) {
                sb.append("and: \"Spy相关接口\"\n");
                sb.append("def spy = Spy( testObj )\n");
                for (Method spyMethod : method.getSpyMethods()) {
                    if (!testSubjectInspector.isSpyMethod(spyMethod, method)) {
                        continue;
                    }

                    sb.append("spy.")
                            .append(spyMethod.getName())
                            .append("(")
                            .append(mockitoMockBuilder.buildMockArgsMatchers(spyMethod.getMethodParams(), "Groovy"))
                            .append(") >> ");
                    if (Objects.equals(spyMethod.getReturnType().getName(), "void")) {
                        sb.append(" {}");
                    } else {
                        sb.append(testBuilder.renderReturnParam(method, spyMethod.getReturnType(), spyMethod.getName() + "Response", CommonConstant.grReplacementTypesStatic, CommonConstant.grDefaultTypeValues));
                    }
                    sb.append("\n");
                }

            }
        }
        sb.append("when:\n");
        sb.append("def result = ");
        if (method.isStatic()) {
            sb.append(testClass.getName());
        } else {
            sb.append(shouldSpy ? "spy" : "testObj");
        }
        sb.append(".")
                .append(method.getName())
                .append("(")
                .append(paraTestComponents.getMethodClassParamsStr())
                .append(")\n");
        sb.append("\n");
        sb.append("""
                then: "验证返回结果里属性值是否符合预期"
                                result == expectedResult
                                where: "表格方式验证多种分支调用场景"
                """);
        sb.append(testSubjectInspector.formatSpockDataParameters(paraTestComponents.getParamsMap(), "         "));
        sb.append("\n}\n");
        GrMethod generateMethod = factory.createMethodFromText(sb);
        targetClass.add(generateMethod);
    }

    private void generateSetupMethod(GrTypeDefinition targetClass, List<String> mockedFields) {
        if (hasMocks && CollectionUtils.isNotEmpty(mockedFields)) {

            Optional<PsiMethod> methodOptional = Arrays.stream(targetClass.getMethods())
                    .filter(psiMethod -> psiMethod.getName().equals("setup"))
                    .findFirst();
            boolean newMethod = false;
            PsiMethod method;
            if (methodOptional.isPresent()) {
                method = methodOptional.get();
            } else {
                newMethod = true;
                method = factory.createMethodFromText("def setup(){\n\n}\n");
            }
            PsiElement body = method.getLastChild();

            if (body != null) {
                for (String fieldName : mockedFields) {
                    GrStatement statement = factory.createStatementFromText("testObj.%s = %s \n".formatted(fieldName, fieldName));
                    body.addBefore(statement, body.getLastChild());
                }
            }
            if (newMethod) {
                targetClass.add(method);
            } else {
                method.replace(method.copy());
            }

        }

    }

    private List<String> grRenderMockedFields(GrTypeDefinition targetClass) {
        List<String> mockFieldNames = new ArrayList<>();
        if (hasMocks) {
            List<Field> fields = testClass.getFields();
            for (Field field : fields) {
                if (field.isInherited()) {
                    continue;
                }
                if (!mockitoMockBuilder.isMockable(field)) {
                    continue;
                }
                if (containsField(targetClass, field.getName())) {
                    continue;
                }
                if (field.getType().getAnnotationQualifiedNames().contains("org.mapstruct.Mapper")) {
                    generateField(targetClass, "def %s = org.mapstruct.factory.Mappers.getMapper(%s)".formatted(field.getName(), field.getType().getCanonicalName()));
                } else {
                    generateField(targetClass, "def %s = Mock(%s)".formatted(field.getName(), field.getType().getCanonicalName()));
                }
                mockFieldNames.add(field.getName());
            }
        }
        return mockFieldNames;
    }

    private void generateField(GrTypeDefinition targetClass, String text) {
        GrVariableDeclaration field = factory.createFieldDeclarationFromText(text);
        targetClass.add(field);
    }


    private void addSuperClass(@NotNull GrTypeDefinition targetClass, @NotNull Project project, @Nullable String superClassName)
            throws IncorrectOperationException {
        if (superClassName == null) return;
        PsiClass[] supers = targetClass.getSupers();
        if (supers.length > 0) {
            if (Arrays.stream(supers).anyMatch(superClass -> Objects.equals(superClass.getQualifiedName(), superClassName))) {
                return;
            }
        }

        PsiClass superClass = findClass(project, superClassName);
        GrCodeReferenceElement superClassRef;
        if (superClass != null) {
            superClassRef = factory.createCodeReferenceElementFromClass(superClass);
        } else {
            superClassRef = factory.createCodeReference(superClassName);
        }
        GrExtendsClause extendsClause = targetClass.getExtendsClause();
        if (extendsClause == null) {
            extendsClause = (GrExtendsClause) targetClass.addAfter(factory.createExtendsClause(), targetClass.getNameIdentifierGroovy());
        }

        extendsClause.add(superClassRef);
    }

    private PsiClass findClass(Project project, String fqName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
    }

    private boolean containsField(GrTypeDefinition targetClass, String fieldName) {
        return Arrays.stream(targetClass.getFields()).anyMatch(field -> Objects.equals(fieldName, field.getName()));
    }

    private boolean containsMethod(GrTypeDefinition targetClass, String methodName) {
        String testMethodName = methodName + "Test";
        return Arrays.stream(targetClass.getMethods()).anyMatch(method -> Objects.equals(testMethodName, method.getName()));
    }
}
