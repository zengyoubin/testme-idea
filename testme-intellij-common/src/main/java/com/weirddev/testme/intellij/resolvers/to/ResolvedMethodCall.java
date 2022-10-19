package com.weirddev.testme.intellij.resolvers.to;

import com.intellij.psi.PsiMethod;
import com.weirddev.testme.intellij.common.utils.PsiMethodUtils;

import java.util.List;

/**
 * Date: 16/09/2017
 *
 * @author Yaron Yamin
 */
public class ResolvedMethodCall {
    private final PsiMethod psiMethod;
    private final List<MethodCallArg> methodCallArguments;
    private final String methodId;
    private final String callFieldName;

    public ResolvedMethodCall(PsiMethod psiMethod, List<MethodCallArg> methodCallArguments) {
        this.psiMethod = psiMethod;
        this.methodCallArguments = methodCallArguments;
        methodId = PsiMethodUtils.formatMethodId(psiMethod);
        this.callFieldName = null;
    }

    public ResolvedMethodCall(PsiMethod psiMethod, List<MethodCallArg> methodCallArguments,String callFieldName) {
        this.psiMethod = psiMethod;
        this.methodCallArguments = methodCallArguments;
        methodId = PsiMethodUtils.formatMethodId(psiMethod);
        this.callFieldName = callFieldName;
    }

    public PsiMethod getPsiMethod() {
        return psiMethod;
    }

    public List<MethodCallArg> getMethodCallArguments() {
        return methodCallArguments;
    }

    public String getCallFieldName(){
        return callFieldName;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolvedMethodCall)) return false;

        ResolvedMethodCall that = (ResolvedMethodCall) o;

        return methodId.equals(that.methodId);
    }

    @Override
    public int hashCode() {
        return methodId.hashCode();
    }


}
