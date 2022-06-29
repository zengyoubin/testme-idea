package com.weirddev.testme.intellij.template.context;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.weirddev.testme.intellij.common.utils.LanguageUtils;
import com.weirddev.testme.intellij.common.utils.PsiMethodUtils;
import com.weirddev.testme.intellij.configuration.TestMeConfig;
import com.weirddev.testme.intellij.configuration.TestMeConfigPersistent;
import com.weirddev.testme.intellij.groovy.resolvers.GroovyPsiTreeUtils;
import com.weirddev.testme.intellij.resolvers.to.MethodCallArg;
import com.weirddev.testme.intellij.resolvers.to.ResolvedMethodCall;
import com.weirddev.testme.intellij.resolvers.to.ResolvedReference;
import com.weirddev.testme.intellij.scala.resolvers.ScalaPsiTreeUtils;
import com.weirddev.testme.intellij.template.TypeDictionary;
import com.weirddev.testme.intellij.utils.ClassNameUtils;
import com.weirddev.testme.intellij.utils.JavaPsiTreeUtils;
import com.weirddev.testme.intellij.utils.PropertyUtils;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class Method.
 * Date: 24/10/2016
 *
 * @author Yaron Yamin
 */
public class Method {
    private static final Logger LOG = Logger.getInstance(Method.class.getName());
    /**
     * Method's return type
     */
    @Getter
    private final Type returnType;
    /**
     * method name
     */
    @Getter
    private final String name;
    /**
     * method owner type cannonical name
     */
    @Getter
    private final String ownerClassCanonicalType;
    /**
     * method arguments
     */
    @Getter
    private final List<Param> methodParams;
    /**
     * true - if method has private modifier
     */
    @Getter
    private final boolean isPrivate;
    /**
     * true - if method has protected modifier
     */
    @Getter
    private final boolean isProtected;
    /**
     * true - if method has default (package-private access modifier)
     */
    @Getter
    private final boolean isDefault;
    /**
     * true - if method has public modifier
     */
    @Getter
    private final boolean isPublic;
    /**
     * true - if method is abstract
     */
    @Getter
    private final boolean isAbstract;
    /**
     * true - if method defined as native
     */
    @Getter
    private final boolean isNative;
    /**
     * true - if this is a static method
     */
    @Getter
    private final boolean isStatic;
    /**
     * true - if method is a setter
     */
    @Getter
    private final boolean isSetter;
    /**
     * true - if method is a getter
     */
    @Getter
    private final boolean isGetter;
    /**
     * true - if method is a constructor
     */
    @Getter
    private final boolean constructor;
    /**
     * true - if method is overridden in child class
     */
    @Getter
    private final boolean overridden;
    /**
     * true - if method is inherited from parent class
     */
    @Getter
    private final boolean inherited;
    /**
     * true - if owner type is an interface
     */
    @Getter
    private final boolean isInInterface;
    /**
     * true -  if method is synthetically generated. common for scala methods
     */
    @Getter
    private final boolean isSynthetic;
    /**
     * the underlying field property name. relevant for getter/setter
     */
    @Getter
    private final String propertyName;
    /**
     * true - when accessible from class under test
     */
    @Getter
    private final boolean accessible;
    /**
     * true - is Primary Constructor (relevant for Scala)
     */
    @Getter
    private final boolean primaryConstructor;
    /**
     * methods called directly from this method
     */
    @Getter
    private Set<MethodCall> directMethodCalls = new HashSet<MethodCall>();
    /**
     * methods called directly from this method or on the call stack from this method via other methods belonging to the same type hierarchy
     */
    @Getter
    private Set<MethodCall> methodCalls = new HashSet<MethodCall>();

    @Getter
    private Set<MethodCall> methodCallsIgnorePublicAndProtected = new HashSet<MethodCall>();
    private Set<Method> spyMethods;
    /**
     * methods referenced from this method. i.e.  SomeClassName::someMethodName
     */
    @Getter
    private Set<Method> methodReferences = new HashSet<Method>();
    /**
     *  method calls of methods in this owner's class type or one of it's ancestor type. including indirectly called methods up to max method call search depth. ResolvedMethodCall objects of the class under test are deeply resolved
     *  @deprecated not used. might be removed
     */
//   @Getter  private final Set<MethodCall> calledFamilyMembers=new HashSet<MethodCall>();
    /**
     * references included in this method's implementation
     */
    @Getter
    private Set<Reference> internalReferences = new HashSet<Reference>();
    /**
     * formatted method id. a string used to uniquely discriminate this method from others
     */
    @Getter
    private final String methodId;
    /**
     * Fields affected (assigned to) by methods called from this method. currently calculated only for constructors. i.e. when delegating to other constructors
     */
    @Getter
    private final Set<Field> indirectlyAffectedFields = new HashSet<Field>();

    public Method(PsiMethod psiMethod, PsiClass srcClass, int maxRecursionDepth, TypeDictionary typeDictionary) {
        isPrivate = psiMethod.hasModifierProperty(PsiModifier.PRIVATE);
        isProtected = psiMethod.hasModifierProperty(PsiModifier.PROTECTED);
        isDefault = psiMethod.hasModifierProperty(PsiModifier.DEFAULT) || psiMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL);
        isPublic = psiMethod.hasModifierProperty(PsiModifier.PUBLIC);
        isAbstract = psiMethod.hasModifierProperty(PsiModifier.ABSTRACT);
        isNative = psiMethod.hasModifierProperty(PsiModifier.NATIVE);
        isStatic = psiMethod.hasModifierProperty(PsiModifier.STATIC);
        name = psiMethod.getName();
        ownerClassCanonicalType = psiMethod.getContainingClass() == null ? null : psiMethod.getContainingClass().getQualifiedName();
        constructor = psiMethod.isConstructor();
        primaryConstructor = constructor && psiMethod.getClass().getSimpleName().contains("PrimaryConstructor");
        isSetter = PropertyUtils.isPropertySetter(psiMethod);
        isGetter = PropertyUtils.isPropertyGetter(psiMethod);
//        final PsiField psiField = PropertyUtil.findPropertyFieldByMember(psiMethod);
//        propertyName = psiField == null ? null : psiField.getName();
        propertyName = ClassNameUtils.extractTargetPropertyName(name, isSetter, isGetter);
        if (srcClass != null) {
            overridden = isOverriddenInChild(psiMethod, srcClass);
            inherited = isInherited(psiMethod, srcClass);
        } else {
            overridden = false;
            inherited = false;
        }
        isInInterface = isInterface(psiMethod);
        methodId = PsiMethodUtils.formatMethodId(psiMethod);
        accessible = typeDictionary.isAccessible(psiMethod);
        isSynthetic = isSyntheticMethod(psiMethod);
        final List<Pair<PsiMethod, PsiSubstitutor>> methodSubstitutionMap = findMethodSubstitutionMap(psiMethod, srcClass);
        this.returnType = resolveReturnType(psiMethod, methodSubstitutionMap, maxRecursionDepth, typeDictionary);
        methodParams = extractMethodParams(psiMethod, methodSubstitutionMap, primaryConstructor, maxRecursionDepth, typeDictionary);
    }

    static boolean isRelevant(PsiClass psiClass, PsiMethod psiMethod) {
        boolean isRelevant = true;
        final PsiClass containingClass = psiMethod.getContainingClass();
        final PsiClass ownerClass = containingClass == null ? psiClass : containingClass;
        if (ownerClass != null && isLanguageInherited(ownerClass.getQualifiedName())) {
            isRelevant = false;
        } else {
            final String methodId = PsiMethodUtils.formatMethodId(psiMethod);
            if (LanguageUtils.isGroovy(psiMethod.getLanguage())
                    && (psiMethod.getClass().getCanonicalName().contains("GrGdkMethodImpl") || methodId.endsWith(".invokeMethod(java.lang.String,java.lang.Object)") || methodId.endsWith(".getProperty(java.lang.String)") || methodId
                    .endsWith(".setProperty(java.lang.String,java.lang.Object)"))) {
                isRelevant = false;
            } else if (ownerClass != null && ownerClass.getQualifiedName() != null) {
                JavaPsiFacade facade = JavaPsiFacade.getInstance(ownerClass.getProject());
                PsiClass[] possibleClasses = facade.findClasses(ownerClass.getQualifiedName(), GlobalSearchScope.projectScope((ownerClass.getProject())));
                if (possibleClasses.length == 0) {
                    isRelevant = false;
                }
            }

            if (!isRelevant) {
                TestMeConfig state = TestMeConfigPersistent.getInstance().getState();
                String prefix = Optional.ofNullable(state).map(TestMeConfig::getGeneratePojoPrefix).orElse("");
                for (String start : prefix.split(",")) {
                    if (StringUtils.isNotBlank(start) && methodId.startsWith(start)) {
                        return true;
                    }
                }
            }
        }
        return isRelevant;
    }

    /**
     * true - if method has a return type
     */
    public boolean hasReturn() {
        return returnType != null && !"void".equals(returnType.getName());
    }

    boolean isTestable() {
        return !isLanguageInherited(ownerClassCanonicalType) && !isSetter() && !isGetter() && !isConstructor() && ((isDefault() || isProtected()) && !isInherited() || isPublic()) && !isOverridden() && !isInInterface() && !isAbstract() && !isSynthetic();
    }

    void resolveInternalReferences(PsiMethod psiMethod, TypeDictionary typeDictionary) {
        if (!isLanguageInherited(getOwnerClassCanonicalType())) {
            resolveCalledMethods(psiMethod, typeDictionary);
            resolveReferences(psiMethod, typeDictionary);
            resolveMethodReferences(psiMethod, typeDictionary);
        }
    }

    private static boolean isLanguageInherited(String ownerClassCanonicalType) {
        return "java.lang.Object".equals(ownerClassCanonicalType) || "java.lang.Class".equals(ownerClassCanonicalType) || "groovy.lang.GroovyObjectSupport".equals(ownerClassCanonicalType);
    }

    @Nullable
    private Type resolveReturnType(PsiMethod psiMethod, List<Pair<PsiMethod, PsiSubstitutor>> methodSubstitutionMap, int maxRecursionDepth, TypeDictionary typeDictionary) {
        final PsiType psiType = psiMethod.getReturnType();
        if (psiType == null) {
            return null;
        } else {
            final Optional<PsiType> substitutedType = findSubstitutedType(psiMethod, psiType, methodSubstitutionMap);
            Object typeElement = null;
            if (LanguageUtils.isScala(psiMethod.getLanguage())) {
                typeElement = ScalaPsiTreeUtils.resolveReturnType(psiMethod);
            }
            return typeDictionary.getType(substitutedType.orElse(psiType), maxRecursionDepth, true, typeElement);
        }
    }

    private static PsiField resolveLeftHandExpressionAsField(@NotNull PsiExpression expr) {
        PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
        if (!(parent instanceof PsiAssignmentExpression)) {
            return null;
        }
        final PsiAssignmentExpression psiAssignmentExpression = (PsiAssignmentExpression) parent;
        final PsiReference reference = psiAssignmentExpression.getLExpression().getReference();
        final PsiElement element = reference != null ? reference.resolve() : null;
        return element == null || !(element instanceof PsiField) ? null : (PsiField) element;
    }

    private boolean isInterface(PsiMethod psiMethod) {
//            //method inherited from an interface but implemented on the interface should not be considered as interface method
//            return psiMethod.hasModifierProperty("abstract") || psiMethod.getContainingClass() != null && psiMethod.getContainingClass().isInterface();

        return psiMethod.hasModifierProperty("abstract");
    }

    private boolean isSyntheticMethod(PsiMethod psiMethod) {
        if (LanguageUtils.isScala(psiMethod.getLanguage())) {
            return ScalaPsiTreeUtils.isSyntheticMethod(psiMethod);
        } else {
            return false;
        }
    }

    @NotNull
    private List<Pair<PsiMethod, PsiSubstitutor>> findMethodSubstitutionMap(PsiMethod psiMethod, PsiClass srcClass) {
        if (isInherited() && isTestable() && srcClass != null && hasGenericType(psiMethod)) {
            return srcClass.findMethodsAndTheirSubstitutorsByName(psiMethod.getName(), true);
        } else {
            return new ArrayList<>();
        }
    }

    private boolean hasGenericType(PsiMethod psiMethod) {
        return Stream.concat(Stream.of(psiMethod.getParameterList().getParameters()).map(PsiVariable::getType), Stream.of(psiMethod.getReturnType())).anyMatch(psiType -> mayContainTypeParameter(psiType));
    }

    private boolean mayContainTypeParameter(PsiType psiType) {
        return psiType instanceof PsiClassReferenceType/* && ((PsiClassReferenceType) psiType).resolve() instanceof PsiTypeParameter */;
    }

    private Optional<PsiType> findSubstitutedType(PsiMethod psiMethod, PsiType psiType, List<Pair<PsiMethod, PsiSubstitutor>> methodsAndTheirSubstitutors) {
        return methodsAndTheirSubstitutors.stream()
                .filter(pair1 -> pair1.first.equals(psiMethod))
                .findFirst()
                .flatMap(pair -> Optional.of(pair.second.substitute(psiType)));
    }


    private void resolveReferences(PsiMethod psiMethod, TypeDictionary typeDictionary) {
        if (LanguageUtils.isGroovy(psiMethod.getLanguage())) {
            for (ResolvedReference resolvedReference : GroovyPsiTreeUtils.findReferences(psiMethod)) {
                internalReferences.add(new Reference(resolvedReference.getReferenceName(), resolvedReference.getRefType(), resolvedReference.getPsiOwnerType(), typeDictionary));
            }
        } else {
            for (ResolvedReference resolvedReference : JavaPsiTreeUtils.findReferences(psiMethod)) {
                internalReferences.add(new Reference(resolvedReference.getReferenceName(), resolvedReference.getRefType(), resolvedReference.getPsiOwnerType(), typeDictionary));
            }
        }
    }

    private void resolveMethodReferences(PsiMethod psiMethod, TypeDictionary typeDictionary) {
        for (PsiMethod resolvedMethodReference : JavaPsiTreeUtils.findMethodReferences(psiMethod)) {
            if (isRelevant(resolvedMethodReference.getContainingClass(), resolvedMethodReference)) {
                this.methodReferences.add(new Method(resolvedMethodReference, resolvedMethodReference.getContainingClass(), 1, typeDictionary));
            }
        }
    }

    private void resolveCalledMethods(PsiMethod psiMethod, TypeDictionary typeDictionary) {
        // todo try to pass/support src class in scala/groovy as well. if successful, consider re-implementing with a factory method call
        if (LanguageUtils.isGroovy(psiMethod.getLanguage())) {
            for (ResolvedMethodCall resolvedMethodCall : GroovyPsiTreeUtils.findMethodCalls(psiMethod)) {
                addDirectMethodCallIfRelevant(typeDictionary, resolvedMethodCall, null);
            }
        } else if (LanguageUtils.isScala(psiMethod.getLanguage())) {
            for (ResolvedMethodCall resolvedMethodCall : ScalaPsiTreeUtils.findMethodCalls(psiMethod)) {
                addDirectMethodCallIfRelevant(typeDictionary, resolvedMethodCall, null);
            }
        } else {
            for (ResolvedMethodCall methodCalled : JavaPsiTreeUtils.findMethodCalls(psiMethod)) {
                addDirectMethodCallIfRelevant(typeDictionary, methodCalled, methodCalled.getPsiMethod().getContainingClass());
            }
        }
        methodCalls = this.directMethodCalls;
        methodCallsIgnorePublicAndProtected = new HashSet<>(directMethodCalls);
    }

    private void addDirectMethodCallIfRelevant(TypeDictionary typeDictionary, ResolvedMethodCall methodCalled, PsiClass srcClass) {
        if (isRelevant(methodCalled.getPsiMethod().getContainingClass(), methodCalled.getPsiMethod())) {
            this.directMethodCalls.add(new MethodCall(new Method(methodCalled.getPsiMethod(), srcClass, 1, typeDictionary), convertArgs(methodCalled.getMethodCallArguments())));
        }
    }

    private List<MethodCallArgument> convertArgs(List<MethodCallArg> methodCallArguments) {
        final ArrayList<MethodCallArgument> methodCallArgs = new ArrayList<MethodCallArgument>();
        if (methodCallArguments != null) {
            for (MethodCallArg methodCallArgument : methodCallArguments) {
                methodCallArgs.add(new MethodCallArgument(methodCallArgument.getText()));
            }
        }
        return methodCallArgs;
    }

    private boolean isOverriddenInChild(PsiMethod method, PsiClass srcClass) {
        String srcQualifiedName = srcClass.getQualifiedName();
        String methodClsQualifiedName = method.getContainingClass() == null ? null : method.getContainingClass().getQualifiedName();
        if (srcQualifiedName == null || methodClsQualifiedName == null || srcQualifiedName.equals(methodClsQualifiedName)) {
            return false;
        } else {
            final PsiMethod childMethod = MethodSignatureUtil.findMethodBySuperMethod(srcClass, method, false);
            return childMethod != null;
        }
    }

    private boolean isInherited(PsiMethod method, PsiClass srcClass) {
        String srcQualifiedName = srcClass.getQualifiedName();
        String methodClsQualifiedName = method.getContainingClass() == null ? null : method.getContainingClass().getQualifiedName();
        return (srcQualifiedName != null && methodClsQualifiedName != null && !srcQualifiedName.equals(methodClsQualifiedName));
    }

    private List<Param> extractMethodParams(PsiMethod psiMethod, List<Pair<PsiMethod, PsiSubstitutor>> methodSubstitutionMap, boolean shouldResolveAllMethods, int maxRecursionDepth, TypeDictionary typeDictionary) {
        ArrayList<Param> params = new ArrayList<Param>();
        final PsiParameter[] parameters;
        if (LanguageUtils.isScala(psiMethod.getLanguage())) {
            parameters = ScalaPsiTreeUtils.resolveParameters(psiMethod);
        } else {
            parameters = psiMethod.getParameterList().getParameters();
        }
        for (PsiParameter psiParameter : parameters) {
            final ArrayList<Field> assignedToFields = findMatchingFields(psiParameter, psiMethod);
            final Optional<PsiType> substitutedType = findSubstitutedType(psiMethod, psiParameter.getType(), methodSubstitutionMap);
            params.add(new Param(psiParameter, substitutedType, typeDictionary, maxRecursionDepth, assignedToFields, shouldResolveAllMethods));
        }
        return params;
    }

    private static ArrayList<Field> findMatchingFields(PsiParameter psiParameter, PsiMethod psiMethod) {
        final ArrayList<Field> fields = new ArrayList<Field>();
        try {
            if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
                for (PsiReference reference : ReferencesSearch.search(psiParameter, new LocalSearchScope(new PsiMethod[]{psiMethod}))) {
                    final PsiElement element = reference.getElement();
                    PsiField psiField = null;
                    if (LanguageUtils.isGroovy(element.getLanguage())) {
                        psiField = GroovyPsiTreeUtils.resolveGrLeftHandExpressionAsField(element);
                    } else if (element instanceof PsiExpression && !PsiUtil.isOnAssignmentLeftHand((PsiExpression) element)) {
                        psiField = resolveLeftHandExpressionAsField((PsiExpression) element);
                    }
                    if (psiField != null && psiField.getContainingClass() != null) {
                        fields.add(new Field(psiField, psiField.getContainingClass(), null, 0));
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warn(String.format("cant search for matching fields for parameter %s in method %s", psiParameter.getName(), psiMethod.getName()), e);
        }
        return fields;
    }

    public Set<Method> getSpyMethods() {
        return this.getMethodCallsIgnorePublicAndProtected().stream()
                .map(MethodCall::getMethod)
                .filter(method -> method.getOwnerClassCanonicalType().equals(this.getOwnerClassCanonicalType()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Method)) {
            return false;
        }

        Method method = (Method) o;

        return methodId.equals(method.methodId);
    }

    @Override
    public int hashCode() {
        return methodId.hashCode();
    }

    @Override
    public String toString() {
        return "Method{" + "methodId='" + methodId + '\'' + '}';
    }

}
