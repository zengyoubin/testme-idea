package com.weirddev.testme.intellij.generator;

import com.google.common.collect.ImmutableSet;
import com.weirddev.testme.intellij.template.context.Method;
import com.weirddev.testme.intellij.template.context.Param;
import com.weirddev.testme.intellij.template.context.Type;
import com.weirddev.testme.intellij.utils.ClassNameUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Date: 16/09/2017
 *
 * @author Yaron Yamin
 */
public class TestBuilderUtil {

    private static final Set<String> STRING_TYPES = ImmutableSet.of("java.lang.String", "java.lang.Object","scala.Nothing","scala.Predef.String","String");

    public static boolean looksLikeObjectKeyInGroovyMap(String expFragment, String canonicalTypeName) {
        return ":".equals(expFragment) && !"java.lang.String".equals(canonicalTypeName);
    }

    public static boolean hasValidEmptyConstructor(Type type) {
        if (type.isInterface() || type.isAbstract()) {
            return false;
        }
        if (type.isHasDefaultConstructor()) {
            return true;
        }
        for (Method method : type.findConstructors()) {
            if (method.isAccessible() &&  method.getMethodParams().size() == 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStringType(String canonicalName) {
        return STRING_TYPES.contains(canonicalName);
    }

}
